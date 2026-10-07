plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.tengwear.appopspro"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tengwear.appopspro"
        // 真实安装下限由依赖图决定：Wear Compose 全家桶（foundation/material3/material/
        // material-core/navigation）全部声明 minSdk 25，Shizuku api 24 / provider 23，
        // 其余（compose-ui / navigation-compose / activity-compose / core 等）均为 21 或更低。
        // 再低就要换掉 Wear Compose（等于重写整个 UI），会破坏功能，故停在这里。
        // 注：AppOps 的 op 码与系统版本强绑定，本应用已在运行期用 strOpToOp(opName)
        // 解析本机码号，因此 25~34 各版本上权限功能都正确（见 AppOpsRepository）。
        minSdk = 25
        targetSdk = 30
        versionCode = 2
        versionName = "1.1"
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        debug {
            // 调试包不做压缩/混淆，保证构建速度与断点可读性
            isMinifyEnabled = false
            isShrinkResources = false
        }

        release {
            // 项目暂无正式 keystore，先用 debug 签名，保证 assembleRelease 产出的
            // APK 可以直接安装到手表/模拟器验证；上架前替换为正式签名即可。
            signingConfig = signingConfigs.getByName("debug")

            // ★ 体积主要来源是 material-icons-extended（上千个 ImageVector）与未裁剪的
            //   Compose 运行时代码。开启 R8 全量裁剪 + 资源压缩后，只有被引用到的图标
            //   和类会留下，APK 从 ~58MB 降到个位数 MB。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE",
                "/META-INF/LICENSE.txt",
                "/META-INF/LICENSE.md",
                "/META-INF/NOTICE",
                "/META-INF/NOTICE.txt",
                "/META-INF/NOTICE.md",
                "/META-INF/*.version",
                "/META-INF/*.kotlin_module",
                "/META-INF/**/LICENSE.txt",
                "/META-INF/**/LICENSE",
                "/META-INF/com/android/build/gradle/**",
                "/DebugProbesKt.bin",
            )
        }
    }
}

dependencies {
    // Wear Compose M3E
    implementation("androidx.wear.compose:compose-foundation:1.5.0")
    implementation("androidx.wear.compose:compose-material3:1.5.0")
    implementation("androidx.wear.compose:compose-navigation:1.5.0")

    // ⚠️ compose-ui-tooling 只服务于 Android Studio 的 @Preview，
    // 之前写成 implementation 会被打进 release 包（体积白送），改为仅 debug。
    debugImplementation("androidx.wear.compose:compose-ui-tooling:1.5.0")

    // Navigation（SwipDismissableNavHost 依赖它提供 NavHostController 与 rememberNavController；
    // compose-navigation 只把它声明为 runtime 传递依赖，编译期需要显式引入）
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Compose 基础
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // 权限图标。该库包含上千个 ImageVector，是 debug 包体积的主要来源；
    // 这里只用到其中约 40 个，release 构建交给 R8 裁剪（见 proguard-rules.pro）。
    implementation("androidx.compose.material:material-icons-extended")

    // Shizuku API
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    // 隐藏 API 编译时存根（提供 IPackageManager / IActivityManager 等类）
    // 注意：AppOpsManager$PackageOps / $OpEntry 这类 @hide 嵌套类在存根里缺 CREATOR，
    // 无法用 CREATOR 反序列化，相关 Parcel 解析改为按真实字段序手工读取（见 AppOpsRepository）。
    compileOnly("dev.rikka.hidden:stub:4.4.0")

    // Kotlin 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
