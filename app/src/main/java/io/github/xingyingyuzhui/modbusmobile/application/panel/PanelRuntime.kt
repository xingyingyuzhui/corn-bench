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
