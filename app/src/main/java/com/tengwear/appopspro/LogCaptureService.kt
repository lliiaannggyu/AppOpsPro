package com.tengwear.appopspro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 日志**实时抓取**的前台服务。
 *
 * 抓的是「一直流」的 logcat：进程常驻输出，服务逐行收、按批写进 [LogCaptureState]，
 * 直到用户点「停止」（或进程自己结束）为止。
 *
 * 为什么用前台服务：
 * - 页面协程绑定组合，离开页面 / Activity 销毁就被取消，抓到一半会断
 * - 前台服务带常驻通知，进程不会被系统随便回收，息屏 / 切走也一直在抓
 *
 * 逐行写状态会让 UI 每行重组一次（logcat 每秒几百行），所以这里先攒到
 * `pending`，由 flusher 每 [FLUSH_INTERVAL_MS] 批量提交一次。
 */
class LogCaptureService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var captureJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // 立即收尾，**不等协程 / 管道**：先把状态置为已停止、退出前台、停掉自己，
            // onDestroy 里 scope.cancel() 会连带取消抓取（进而销毁子进程 + 关流）。
            // 不这么做的话，万一管道没被唤醒，界面会一直显示「抓取中」。
            captureJob?.cancel()
            LogCaptureState.stop()
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }

        val level = LogLevel.entries
            .firstOrNull { it.name == intent?.getStringExtra(EXTRA_LEVEL) } ?: LogLevel.All
        val keyword = intent?.getStringExtra(EXTRA_KEYWORD).orEmpty()
        val pkg = intent?.getStringExtra(EXTRA_PACKAGE).orEmpty()

        // ⚠️ 必须在 5s 内 startForeground，否则系统会抛
        // "Context.startForegroundService() did not then call Service.startForeground()"。
        startForeground(NOTIFICATION_ID, buildNotification())
        LogCaptureState.start()

        captureJob?.cancel()
        captureJob = scope.launch {
            val pending = ArrayList<String>(256)
            val lock = Any()

            // 定时把攒下的行批量提交给 UI
            val flusher = launch {
                while (isActive) {
                    delay(FLUSH_INTERVAL_MS)
                    val batch = synchronized(lock) {
                        if (pending.isEmpty()) null else ArrayList(pending).also { pending.clear() }
                    }
                    if (batch != null) LogCaptureState.append(batch)
                }
            }

            try {
                // 针对某个应用：优先用 `--pid` 精确过滤；
                // 拿不到 PID（应用没在跑 / 无 pidof）就退化为「用包名当关键字」匹配。
                val pid = if (pkg.isBlank()) null else LogcatRepository.resolvePid(pkg)
                val effectiveKeyword = if (pkg.isNotBlank() && pid == null) pkg else keyword

                LogcatRepository.stream(level, effectiveKeyword, pid) { line ->
                    synchronized(lock) { pending.add(line) }
                }
                LogCaptureState.stop()
            } catch (e: CancellationException) {
                // 用户点「停止」—— 这是正常路径，不是错误
                LogCaptureState.stop()
                throw e
            } catch (e: Exception) {
                LogCaptureState.fail(e)
            } finally {
                flusher.cancel()
                // 收尾时把剩下的也提交掉
                val rest = synchronized(lock) {
                    if (pending.isEmpty()) null else ArrayList(pending).also { pending.clear() }
                }
                if (rest != null) LogCaptureState.append(rest)
                stopForegroundCompat()
                stopSelf()
            }
        }
        // 被系统杀掉后不重启（抓取是显式发起的一次性动作）
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "日志抓取", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("正在实时抓取日志")
            .setContentText("AppOps Pro 正在后台持续读取 logcat")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    companion object {
        private const val CHANNEL_ID = "appopspro_log_capture"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "com.tengwear.appopspro.action.STOP_LOG_CAPTURE"
        private const val EXTRA_LEVEL = "level"
        private const val EXTRA_KEYWORD = "keyword"
        private const val EXTRA_PACKAGE = "package"

        /** 攒够多久提交一批（太长不实时，太短重组频繁）。 */
        private const val FLUSH_INTERVAL_MS = 250L

        /**
         * 开始实时抓取。
         *
         * @param packageName 目标应用包名；空串表示「全部应用」。
         */
        fun start(context: Context, level: LogLevel, keyword: String, packageName: String) {
            val intent = Intent(context, LogCaptureService::class.java)
                .putExtra(EXTRA_LEVEL, level.name)
                .putExtra(EXTRA_KEYWORD, keyword)
                .putExtra(EXTRA_PACKAGE, packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** 停止抓取（服务自己会收尾并退出前台）。 */
        fun stop(context: Context) {
            context.startService(
                Intent(context, LogCaptureService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
