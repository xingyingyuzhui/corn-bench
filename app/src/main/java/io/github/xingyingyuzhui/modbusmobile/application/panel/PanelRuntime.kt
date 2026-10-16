package io.github.xingyingyuzhui.modbusmobile.application.panel

import kotlinx.coroutines.CoroutineScope

/**
 * 面板的运行时对象（应用级）：最新值存储 + 轮询调度器 + 向导预览的工厂。
 * 界面通过 CompositionLocal 取，没有提供时（截图 / 单测）面板页不轮询、向导没有实时预览。
 */
class PanelRuntime(
    val store: PointStore,
    val scheduler: PollScheduler,
    private val previewFactory: ((CoroutineScope) -> PointPreview)? = null,
) {
    fun newPreview(scope: CoroutineScope): PointPreview? = previewFactory?.invoke(scope)
}
