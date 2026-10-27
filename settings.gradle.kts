pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // usb-serial-for-android（Corn Modbus 的 USB 串口驱动）只发布在 JitPack。
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "corn-bench"

// Corn Bench：:app 外壳 + 共享设计系统 + 两个功能模块 + 两个纯 JVM 核心。
include(
    ":app",
    ":ui-common",
    ":feature-flashing",
    ":flashing-core",
    ":feature-modbus",
    ":modbus-core",
)
