plugins {
    kotlin("jvm") version "2.0.20"
    kotlin("plugin.serialization") version "2.0.20"
    application
}

application { mainClass.set("com.nashi.sync.ServerKt") }

dependencies {
    implementation("io.ktor:ktor-server-core:2.3.12")
    implementation("io.ktor:ktor-server-netty:2.3.12")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("ch.qos.logback:logback-classic:1.4.14")

    testImplementation("junit:junit:4.13.2")
}

// 说明：本文件独立于根 settings.gradle.kts（根只 include(":app")，
// assembleDebug 不会编译 sync-server）。服务端单独构建：
//   cd sync-server && gradle installDist
// 或直接 docker compose up --build nashi-sync（见 Dockerfile）。
