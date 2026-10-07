package com.tengwear.appopspro

import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * 以 **shell（或 root）身份**执行系统命令。
 *
 * ## 为什么不用 `Shizuku.newProcess()`
 *
 * 那个方法是 `private` 的（API 14 计划移除），公开 API 只给了 `transactRemote()`。
 * 但它内部做的事很简单 —— 把 binder 转成 [IShizukuService] 再调 `newProcess`。
 * 而 [Shizuku.getBinder] 是 public 的，[IShizukuService] 这个 AIDL 接口也随
 * `dev.rikka.shizuku:aidl` 一起带进来了，所以这里直接走 AIDL：
 *
 * ```
 * IShizukuService.Stub.asInterface(Shizuku.getBinder()).newProcess(cmd, env, dir)
 * ```
 *
 * 效果与 `adb shell <command>` 完全一致（前提是 Shizuku 以 adb/root 启动）。
 *
 * ## 读流为什么必须并发
 *
 * 子进程的 stdout / stderr 是两个独立的管道，写满 64KB 就会阻塞等待读取。
 * 如果先读完 stdout 再读 stderr，一旦 stderr 先写满，进程会卡死在写 stderr 上，
 * 而我们在等 stdout 的 EOF —— 经典死锁。所以这里两个流各起一个协程同时读。
 */
object ShizukuShell {

    private const val TAG = "AppOpsPro"

    /** 单条命令的默认超时。手表上跑 `pm list packages` 这类命令通常 < 1s。 */
    const val DEFAULT_TIMEOUT_MS = 15_000L

    /** 单条命令的执行结果。 */
    data class Result(
        val command: String,
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val durationMs: Long,
        /** 非 null 表示连进程都没起来（Shizuku 未就绪 / Binder 异常），此时 exitCode 无意义。 */
        val error: String? = null,
        /** 是否因超时被强杀。 */
        val timedOut: Boolean = false
    ) {
        /** 给 UI 展示的合并文本。 */
        val displayText: String
            get() = if (error != null) {
                error
            } else {
                buildString {
                    if (stdout.isNotBlank()) append(stdout.trimEnd())
                    if (stderr.isNotBlank()) {
                        if (isNotEmpty()) append('\n')
                        append(stderr.trimEnd())
                    }
                    if (isEmpty()) append("（无输出）")
                }
            }
    }

    /** Shizuku 是否可用（binder 已收到且权限已授予）。 */
    fun isReady(): Boolean = try {
        Shizuku.getBinder() != null && Shizuku.checkSelfPermission() == 0
    } catch (e: Exception) {
        false
    }

    /**
     * 执行一条命令（等价于 `sh -c "<command>"`）。
     *
     * @param timeoutMs 超时后调用 `destroy()` 强杀子进程，避免界面被卡死的命令挂住。
     */
    suspend fun run(
        command: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): Result = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val cmd = command.trim()
        if (cmd.isEmpty()) {
            return@withContext Result(cmd, -1, "", "", 0, error = "命令为空")
        }

        var running: IRemoteProcess? = null
        try {
            val binder = Shizuku.getBinder()
                ?: return@withContext Result(cmd, -1, "", "", 0, error = "Shizuku 未就绪")
            val service = IShizukuService.Stub.asInterface(binder)

            // 用 sh -c 包一层，这样用户可以写管道、重定向、&& 等
            val process = service.newProcess(arrayOf("sh", "-c", cmd), null, null)
            running = process

            // stdout / stderr 必须并发读，否则可能死锁（见类注释）
            val stdoutDeferred = async { process.inputStream?.readAllText().orEmpty() }
            val stderrDeferred = async { process.errorStream?.readAllText().orEmpty() }

            // ⚠️ 第二个参数是 java.util.concurrent.TimeUnit 的**枚举常量名**，
            // 服务端会做 TimeUnit.valueOf(...)。传 "ms" 会抛
            // IllegalArgumentException: No enum constant ...TimeUnit.ms
            val finished = process.waitForTimeout(timeoutMs, TimeUnit.MILLISECONDS.name)
            if (!finished) {
                runCatching { process.destroy() }
                return@withContext Result(
                    command = cmd,
                    exitCode = -1,
                    stdout = "",
                    stderr = "",
                    durationMs = System.currentTimeMillis() - started,
                    error = "执行超时（${timeoutMs / 1000}s），已强制结束",
                    timedOut = true
                )
            }

            val exit = process.exitValue()
            val result = Result(
                command = cmd,
                exitCode = exit,
                stdout = stdoutDeferred.await(),
                stderr = stderrDeferred.await(),
                durationMs = System.currentTimeMillis() - started
            )
            Log.d(TAG, "shell exit=$exit cmd=$cmd (${result.durationMs}ms)")
            result
        } catch (e: Exception) {
            Log.w(TAG, "shell failed: $cmd", e)
            runCatching { running?.destroy() }
            Result(
                command = cmd,
                exitCode = -1,
                stdout = "",
                stderr = "",
                durationMs = System.currentTimeMillis() - started,
                error = "执行失败：${e.javaClass.simpleName} ${e.message.orEmpty()}".trim()
            )
        }
    }

    /**
     * **流式**执行命令：逐行回调 stdout，直到进程结束或协程被取消。
     *
     * 与 [run] 的区别：`run` 等进程结束再一次性返回全部输出；`streamLines` 边读边回调，
     * 适合 `logcat`（不带 `-d`）这类「一直输出、不会自己结束」的命令。
     * 调用方**取消协程**即可停止 —— 内部注册了 `invokeOnCompletion` 销毁子进程，
     * 否则阻塞中的 `readLine` 要等到下一行才会发现取消。
     *
     * @param onLine 每读到一行调用一次（suspend，可做轻量处理；重活请自行缓冲）
     */
    suspend fun streamLines(
        command: String,
        onLine: suspend (String) -> Unit
    ): Result = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val cmd = command.trim()
        if (cmd.isEmpty()) {
            return@withContext Result(cmd, -1, "", "", 0, error = "命令为空")
        }

        var running: IRemoteProcess? = null
        try {
            val binder = Shizuku.getBinder()
                ?: return@withContext Result(cmd, -1, "", "", 0, error = "Shizuku 未就绪")
            val service = IShizukuService.Stub.asInterface(binder)

            val process = service.newProcess(arrayOf("sh", "-c", cmd), null, null)
            running = process

            // stderr 并发读掉，避免管道写满导致子进程阻塞（同 run 的死锁原因）。
            val errDeferred = async { process.errorStream?.readAllText().orEmpty() }

            val stream = process.inputStream?.let { ParcelFileDescriptor.AutoCloseInputStream(it) }
            // ⚠️ 取消时**除了销毁进程，还要关流**：只 destroy 的话，若子进程被杀而其输出
            // 管道仍有写端持有（如 `sh -c` 里的 logcat 变成孤儿进程），readLine() 会一直
            // 阻塞，协程永远结束不了 —— 表现就是「点了停止但停不下来」。
            // 关流能立刻唤醒阻塞中的 readLine（返回 null 或抛 IOException）。
            coroutineContext[Job]?.invokeOnCompletion {
                runCatching { process.destroy() }
                runCatching { stream?.close() }
            }

            if (stream != null) {
                stream.bufferedReader(Charsets.UTF_8).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        onLine(line)
                    }
                }
            }
            runCatching { errDeferred.await() }

            val exit = runCatching { process.exitValue() }.getOrDefault(-1)
            Result(
                command = cmd,
                exitCode = exit,
                stdout = "",
                stderr = "",
                durationMs = System.currentTimeMillis() - started
            )
        } catch (e: CancellationException) {
            runCatching { running?.destroy() }
            throw e
        } catch (e: Exception) {
            runCatching { running?.destroy() }
            Result(
                command = cmd,
                exitCode = -1,
                stdout = "",
                stderr = "",
                durationMs = System.currentTimeMillis() - started,
                error = "执行失败：${e.javaClass.simpleName} ${e.message.orEmpty()}".trim()
            )
        }
    }

    /** 把 [ParcelFileDescriptor] 读到 EOF，转成 UTF-8 文本。 */
    private fun ParcelFileDescriptor.readAllText(): String =
        ParcelFileDescriptor.AutoCloseInputStream(this).use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        }
}
