plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.roborazzi)
}

android {
    // 第一期不改 namespace / Kotlin 包名（dev.corn.flashing），applicationId 由 :app 决定。
    namespace = "dev.corn.flashing"
    // `corn.flashing.skipNative=true` 只给没有 NDK 的验收构建机（Oracle redroid 云构建）用：不编 USBFS 原生库，
    // `NativeUsbFs` 加载失败时自己降级为“不可用”，烧录侧只能走模拟器后端。正式构建与 CI 不设这个属性。
    if (providers.gradleProperty("corn.flashing.skipNative").orNull != "true") {
        ndkVersion = "27.2.12479018"
        externalNativeBuild {
            cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" }
        }
    }
    compileSdk { version = release(37) { minorApiLevel = 2 } }
    defaultConfig {
        minSdk = 29
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    lint { abortOnError = true }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(project(":flashing-core"))
    implementation(project(":ui-common"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit4)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    // 第二期起烧录模式也要随外壳深浅色变化，这里补 Compose 渲染测试（只在本模块，不进 :app）。
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    debugImplementation(libs.compose.ui.test.manifest)
}

roborazzi {
    outputDir.set(file("src/test/screenshots"))
}

tasks.withType<Test>().configureEach {
    systemProperty(
        "roborazzi.record.filePathStrategy",
        "relativePathFromRoborazziContextOutputDirectory",
    )
    // 像素基线开关（docs/screenshots.md）：verify = 与入库基线逐像素比对，record = 重录基线。不传 = 跟 Roborazzi 任务走。
    providers.gradleProperty("corn.baseline").orNull?.let { systemProperty("corn.baseline", it) }
}
