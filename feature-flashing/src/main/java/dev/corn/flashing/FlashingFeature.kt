package dev.corn.flashing

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.room.Room
import dev.corn.flashing.service.FlashService
import dev.corn.flashing.storage.BatchController
import dev.corn.flashing.storage.BatchPhase
import dev.corn.flashing.storage.FlashDatabase
import dev.corn.flashing.storage.FlashRepository
import dev.corn.flashing.storage.JobLauncher
import dev.corn.flashing.usb.StLinkHardwareBackend
import dev.corn.flashing.usb.UsbProbeMonitor
import java.util.concurrent.CopyOnWriteArraySet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 烧录模式在进程里的入口（原 `CornApplication`）。
 *
 * [install] 只登记前台状态跟踪，很轻；Room、USB 探针监视、算法库、批次控制器等都在 [container]
 * 第一次被用到时才创建（进入烧录模式、或系统重启 [FlashService] 时），上位机模式下不会碰它们。
 */
object FlashingFeature {
    @Volatile private var application: Application? = null
    @Volatile private var created: FlashingContainer? = null
    private val foreground = MutableStateFlow(false)

    /** 由宿主 Application.onCreate 调用；重复调用无副作用。 */
    fun install(app: Application) {
        synchronized(this) {
            if (application != null) return
            application = app
            app.registerActivityLifecycleCallbacks(ForegroundTracker(foreground))
        }
    }

    fun container(context: Context): FlashingContainer {
        created?.let { return it }
        synchronized(this) {
            created?.let { return it }
            val app = application ?: (context.applicationContext as Application).also(::install)
            return FlashingContainer(app, foreground.asStateFlow()).also { created = it }
        }
    }

    /** 容器是否已经创建（没创建 = 烧录模式本进程里没用过，一定不持有 USB）。 */
    val isCreated: Boolean get() = created != null

    /**
     * 不能离开烧录模式的原因；null = 可以离开。单片烧录进行中、或批次没结束（含暂停）都不能离开，
     * 外壳只置灰目标卡并显示原因，不提供中止入口。
     */
    fun leaveBlockedReason(): String? {
        val c = created ?: return null
        if (c.repository.state.value.running) return "正在烧录：等这一片完成后再切换"
        val batch = c.batches.active.value ?: return null
        return when (batch.phase) {
            BatchPhase.PAUSED -> "批次已暂停但未结束：请先在工作台结束批次"
            BatchPhase.FINISHED -> "批次已烧完但还没结束：请先在工作台结束批次"
            else -> "批量烧录进行中：请先在工作台结束批次"
        }
    }

    /**
     * 离开烧录模式前释放 USB：停掉“等待下一块板”的轮询，并等正在进行的识别芯片（[StLinkHardwareBackend]
     * 每次操作都 `StLinkUsbSession.open().use {}`）收尾。返回 false = 超时仍在识别，外壳不切换。
     */
    suspend fun releaseUsb(timeoutMillis: Long = 5_000): Boolean {
        val c = created ?: return true
        c.leaveHooks.forEach { it() }
        if (leaveBlockedReason() != null) return false
        return withTimeoutOrNull(timeoutMillis) { c.repository.state.first { !it.probing } } != null
    }

    private class ForegroundTracker(private val state: MutableStateFlow<Boolean>) : Application.ActivityLifecycleCallbacks {
        private var started = 0
        override fun onActivityStarted(activity: Activity) { started++; state.value = true }
        override fun onActivityStopped(activity: Activity) { started--; state.value = started > 0 }
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}

/** 烧录模式的依赖（与原 CornApplication 的字段、构造顺序一致）。 */
class FlashingContainer internal constructor(
    val app: Application,
    /** True while any activity is started; batch boards only start then. */
    val foreground: StateFlow<Boolean>,
) {
    val reservedRegions: dev.corn.flashing.storage.ReservedRegionStore
    val algorithms: dev.corn.flashing.pack.AlgorithmLibrary
    val repository: FlashRepository
    val usb: UsbProbeMonitor
    val batches: BatchController
    val launcher: JobLauncher

    /** 离开烧录模式时要执行的收尾（ViewModel 登记“停止等待下一块板”）。 */
    internal val leaveHooks = CopyOnWriteArraySet<() -> Unit>()

    init {
        android.util.Log.i("CornUsb", dev.corn.flashing.usb.NativeUsbFs.availabilityDescription)
        val database = Room.databaseBuilder(app, FlashDatabase::class.java, "corn-flashing.db").build()
        usb = UsbProbeMonitor(app)
        val usbManager = app.getSystemService(UsbManager::class.java)
        reservedRegions = dev.corn.flashing.storage.ReservedRegionStore(app)
        algorithms = dev.corn.flashing.pack.AlgorithmLibrary(app)
        repository = FlashRepository(app, database.dao(), targetRegistry = { algorithms.snapshot().targets },
            reservedPolicies = { reservedRegions.snapshot() },
            reservedPreview = { reservedRegions.state.value.policies }) {
            val device = StLinkHardwareBackend.findAuthorized(usbManager, usb.devices.value) ?: return@FlashRepository null
            val snapshot = algorithms.snapshot()
            StLinkHardwareBackend(usbManager, device, snapshot.targets, snapshot.algorithms)
        }
        launcher = JobLauncher { id ->
            try {
                ContextCompat.startForegroundService(app, Intent(app, FlashService::class.java).putExtra(FlashService.EXTRA_JOB_ID, id))
                true
            } catch (_: IllegalStateException) {
                false // ForegroundServiceStartNotAllowedException and the pre-31 background-start error
            } catch (_: SecurityException) {
                false
            }
        }
        batches = BatchController(
            app, database.dao(), repository, launcher, foreground,
            cancelRunning = {
                app.startService(Intent(app, FlashService::class.java).setAction(FlashService.ACTION_CANCEL))
            },
        )
    }
}
