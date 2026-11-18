import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.showmefox.fund"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.showmefox.fund"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        // 签名信息从 keystore.properties 或环境变量读取，绝不写进仓库。
        // 缺失时跳过 release 签名配置，debug 构建不受影响。
        create("release") {
            val props = Properties()
            val propsFile = rootProject.file("keystore.properties")
            if (propsFile.exists()) {
                propsFile.inputStream().use { props.load(it) }
            }
            val storePath = props.getProperty("storeFile")
                ?: System.getenv("SHOWMEFOX_STORE_FILE")
            val storePwd = props.getProperty("storePassword")
                ?: System.getenv("SHOWMEFOX_STORE_PASSWORD")
            val alias = props.getProperty("keyAlias")
                ?: System.getenv("SHOWMEFOX_KEY_ALIAS")
            val keyPwd = props.getProperty("keyPassword")
                ?: System.getenv("SHOWMEFOX_KEY_PASSWORD")

            if (storePath != null && storePwd != null && alias != null && keyPwd != null) {
                storeFile = rootProject.file(storePath)
                storePassword = storePwd
                keyAlias = alias
                keyPassword = keyPwd
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 只有拿到签名材料时才启用，否则用 debug 签名保证能出包
            if (signingConfigs.getByName("release").storeFile?.exists() == true) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { viewBinding = true }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
}

// 项目路径含中文时，Gradle 测试 worker 会按 GBK 解码 classpath 导致 ClassNotFound。
// 显式指定 UTF-8 + 使用纯 ASCII 的临时目录规避。
tasks.withType<Test>().configureEach {
    systemProperty("file.encoding", "UTF-8")
    systemProperty("project.root", rootProject.projectDir.absolutePath)
    jvmArgs("-Dfile.encoding=UTF-8", "-Dsun.jnu.encoding=UTF-8")
}

// 中文路径下 fork 的测试 JVM 无法定位 classpath（Windows + 非 ASCII 路径）。
// 改为在进程内运行测试，规避 worker 的路径编码问题。
tasks.withType<Test>().configureEach {
    maxParallelForks = 1
    forkEvery = 0
}
