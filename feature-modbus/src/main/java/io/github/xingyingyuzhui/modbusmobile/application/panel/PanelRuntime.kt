package io.github.xingyingyuzhui.modbusmobile.application.panel

import kotlinx.coroutines.CoroutineScope

/**
 * 面板的运行时对象（应用级）：最新值存储 + 轮询调度器 + 向导预览的工厂。
 * 界面通过 CompositionLocal 取，没有提供时（截图 / 单测）面板页不轮询、向导没有实时预览。
 */
class PanelRuntime(
    val store: PointStore,
    val scheduler: PollScheduler,
    /** 曲线的样本历史（和 [scheduler] 共用同一个实例才会有数据）。 */
    val history: SampleHistory = SampleHistory(),
    private val previewFactory: ((CoroutineScope) -> PointPreview)? = null,
) {
    fun newPreview(scope: CoroutineScope): PointPreview? = previewFactory?.invoke(scope)

    private val activeOwners = LinkedHashSet<String>()

    /**
     * 谁在前台用着面板的数据：面板页、曲线全屏页各是一个 [owner]。有任何一个在前台就轮询，全部离开才停——
     * 页面切换时旧页面的“离开”可能晚于新页面的“进入”，用计数而不是单个开关才不会误停。
     */
    fun setActive(owner: String, value: Boolean) {
        val active = synchronized(activeOwners) {
            if (value) activeOwners += owner else activeOwners -= owner
            activeOwners.isNotEmpty()
        }
        scheduler.setActive(active)
    }

    /** 工程变了（载入 / 保存）：交给调度器轮询，并登记曲线要记录样本的点位。 */
    fun apply(project: Project) {
        history.track(historyTargets(project))
        scheduler.setPoints(project.usablePoints)
    }

    companion object {
        fun historyTargets(project: Project): Map<String, SampleHistory.Tracked> {
            val result = LinkedHashMap<String, SampleHistory.Tracked>()
            for (panel in project.panels) for (w in panel.widgets) {
                val c = w.curve?.takeIf { w.isUsable } ?: continue
                for (s in c.series) result[s.point.id] = SampleHistory.Tracked(s.point, c.retentionMinutes * 60_000L, c.maxPoints)
            }
            return result
        }
    }
}
