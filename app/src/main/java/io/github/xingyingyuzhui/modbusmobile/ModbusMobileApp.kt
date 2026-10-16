package io.github.xingyingyuzhui.modbusmobile

import android.app.Application

class ModbusMobileApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
