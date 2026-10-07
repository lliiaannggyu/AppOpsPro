# ══════════════════════════════════════════════════════════════════
#  AppOpsPro — R8 压缩 / 混淆规则
#
#  原则：**默认全部裁剪 + 混淆，只保留运行期靠反射或清单引用、R8 看不见的入口。**
#  尤其注意不要给 material-icons-extended 写 keep 规则（见文件末尾说明）。
# ══════════════════════════════════════════════════════════════════

# ──────────────────────────────────────────────────────────────────
#  Shizuku
# ──────────────────────────────────────────────────────────────────
# Shizuku 依赖 AIDL + 反射，类名 / 方法名一旦被混淆就直接失效。
-keep class rikka.shizuku.** { *; }
-keep interface rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-dontwarn rikka.shizuku.**
-dontwarn moe.shizuku.**

# ShizukuProvider 由 AndroidManifest.xml 声明（从 provider AAR 合并进来），
# 代码里没有直接引用，R8 无法感知它的存在 —— 不保留会导致权限请求直接崩溃。
-keep class rikka.shizuku.ShizukuProvider { *; }

# 所有 ContentProvider 子类都可能是清单里声明的组件，统一保留。
-keep class * extends android.content.ContentProvider { *; }

# ──────────────────────────────────────────────────────────────────
#  隐藏 API 存根（compileOnly，运行期由系统 framework 提供）
# ──────────────────────────────────────────────────────────────────
# dev.rikka.hidden:stub 只在编译期存在，运行期类由系统提供；
# Shizuku 内部还会反射访问 android.os.ServiceManager 等隐藏 API，
# 这些「缺失引用」不应导致构建失败。
-dontwarn dev.rikka.hidden.**
-dontwarn android.app.**
-dontwarn android.content.pm.**
-dontwarn android.os.**

# ──────────────────────────────────────────────────────────────────
#  崩溃定位
# ──────────────────────────────────────────────────────────────────
# 保留行号表：混淆后仍能从堆栈定位到源码行。
# 同时把源文件名统一改写成 SourceFile，不泄露原始文件结构。
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 保留注解 / 内部类 / 泛型签名 —— Compose 与 Kotlin metadata 依赖这些信息。
-keepattributes *Annotation*,InnerClasses,Signature,EnclosingMethod

# ──────────────────────────────────────────────────────────────────
#  material-icons-extended —— 不要写 keep 规则！
# ──────────────────────────────────────────────────────────────────
# 该库包含上千个图标，每个都是 `Icons.Filled.X` 形式的顶层属性，
# 内部由 materialIcon { } 构建 ImageVector。本项目只用到约 40 个，
# 未引用的属性会被 R8 正常移除 —— 这正是 release 包体积能大幅下降的主因。
#
# ⚠️ 千万不要在这里写 `-keep class androidx.compose.material.icons.** { *; }`，
#    那会把上千个图标全部保下来，release 包体积立刻回到几十 MB。
