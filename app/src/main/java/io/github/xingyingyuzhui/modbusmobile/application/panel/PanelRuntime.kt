package io.github.xingyingyuzhui.modbusmobile.application.panel

/** 面板的运行时对象（应用级）：最新值存储 + 轮询调度器。界面通过 CompositionLocal 取，没有提供时（截图 / 单测）面板页不轮询。 */
class PanelRuntime(val store: PointStore, val scheduler: PollScheduler)
