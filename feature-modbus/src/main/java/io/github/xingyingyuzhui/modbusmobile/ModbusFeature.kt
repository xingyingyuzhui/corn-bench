package io.github.xingyingyuzhui.modbusmobile

import android.app.Application
import android.content.Context
import io.github.xingyingyuzhui.modbusmobile.application.ConnectionTarget
import io.github.xingyingyuzhui.modbusmobile.application.SessionState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 上位机模式在进程里的入口（原 `ModbusMobileApp`）。
 *
 * 和烧录模式一样走懒加载：只有在真正进入上位机模式（或系统重启 [service.SessionService]）时才创建
 * [ModbusContainer]；烧录模式运行期间进程里不会出现 DataStore / USB 仓库 / Room 历史。
 */
object ModbusFeature {
    @Volatile private var created: ModbusContainer? = null

    /** 容器是否已经创建（没创建 = 上位机模式本进程里没用过，一定不持有 USB）。 */
    val isCreated: Boolean get() = created != null

    fun container(context: Context): ModbusContainer {
        created?.let { return it }
        synchronized(this) {
            created?.let { return it }
            val app = context.applicationContext as Application
            return ModbusContainer(app).also { created = it }
        }
    }

    /**
     * 不能离开上位机模式的原因；null = 可以离开。
     *
     * 会话运行中、USB 正在请求授权 / 打开 / 停止时不能离开；空闲或出错可以。
     * 外壳只置灰目标卡并写原因，不提供“强制断开”。
     */
    fun leaveBlockedReason(): String? {
        val c = created ?: return null
        return when (val state = c.sessionManager.state.value) {
            is SessionState.Running -> when (state.target) {
                ConnectionTarget.Demo -> "上位机演示会话正在运行：请先在连接页断开"
                is ConnectionTarget.Usb -> "串口已连接：请先在连接页断开后再切换"
            }
            SessionState.RequestingPermission -> "正在请求 USB 授权：等授权结果出来后再切换"
            SessionState.Opening -> "正在打开串口：等连接结束或失败后再切换"
            SessionState.Stopping -> "正在断开串口：等断开完成后再切换"
            SessionState.Idle, is SessionState.Error -> null
        }
    }

    /**
     * 离开上位机模式前释放 USB：停掉应用级轮询、断开会话（关闭串口线程与前台服务），并等状态回到空闲 / 出错。
     * 返回 false = 超时仍在断开，外壳不切换。
     */
    suspend fun releaseUsb(timeoutMillis: Long = 5_000): Boolean {
        val c = created ?: return true
        c.masterPoller.stop()
        if (c.sessionManager.state.value !is SessionState.Idle && c.sessionManager.state.value !is SessionState.Error) {
            c.sessionManager.disconnect()
        }
        return withTimeoutOrNull(timeoutMillis) {
            c.sessionManager.state.first { it is SessionState.Idle || it is SessionState.Error }
        } != null
    }

    /** 只给测试 / 预览用：清掉单例，让下次 [container] 重新按传入的 Application 创建。 */
    internal fun resetForTest() {
        synchronized(this) { created = null }
    }
}
