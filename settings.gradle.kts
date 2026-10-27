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

// 初始导入：两个源工程原样并排（各自仍是独立 application），由第一期把它们改成 library 并接入 :app 外壳。
include(
    ":feature-flashing",
    ":flashing-core",
    ":feature-modbus",
    ":modbus-core",
)
