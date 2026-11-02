plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.20"
    id("com.google.devtools.ksp") version "2.0.20-1.0.24"
}

// 发布签名: 从环境变量或 gradle.properties 读取, 未配置时保持未签名(仅本地验证)。
val releaseKeystore = System.getenv("NASHI_KEYSTORE") ?: (findProperty("nashi.keystore") as String?)
val releaseStorePassword = System.getenv("NASHI_STORE_PASSWORD") ?: (findProperty("nashi.storePassword") as String?)
val releaseKeyAlias = System.getenv("NASHI_KEY_ALIAS") ?: (findProperty("nashi.keyAlias") as String?)
val releaseKeyPassword = System.getenv("NASHI_KEY_PASSWORD") ?: (findProperty("nashi.keyPassword") as String?)

android {
    namespace = "com.nashi"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.nashi"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures { compose = true }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

ksp {
    // 导出的 schema JSON 随代码入库, 迁移差异可在评审时直接 diff;
    // 离线环境没有 room-testing, 迁移断言仍是纯 JVM 的 MigrationTest。
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.compose.bom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.0")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("io.ktor:ktor-client-android:2.3.12")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.patrykandpatrick.vico:compose:1.16.1")
    implementation("io.github.sceneview:sceneview:2.3.0")
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-compiler:2.51.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // PC 端 JVM 单元测试: 纯 JVM(无 Robolectric / 无 android-all), 不需要 Android 资源
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockk)
}
