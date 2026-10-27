package dev.corn.bench

import android.app.Application
import dev.corn.flashing.FlashingFeature

/**
 * Corn Bench 的宿主 Application。
 *
 * 这里只做很轻的登记：烧录模式的前台状态跟踪（批量烧录要在前台才继续送板）。
 * 两个模式的容器（Room / USB / DataStore）都还在各自第一次进入时懒加载，
 * 所以冷启动停在上位机还是烧录，都不会初始化另一边。
 */
class BenchApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FlashingFeature.install(this)
    }
}
