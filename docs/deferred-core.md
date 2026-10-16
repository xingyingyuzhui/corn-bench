# 依赖 modbus-core 的条目（暂缓清单）

> 来自 UI 对照清单，随各 PR 更新；以最新 PR 描述里的状态为准。其中“日志时间基数”一项已由 PR-3 在 application 层（`TrafficRecorder`）解决，不再需要改 core。

> 约束：协议与 modbus-core 不动。下面每一条在 ui/ 内都**没有放假数据、没有放空壳**；列出所需的最小 core/application 改动。

| 条目 | 现状（ui/ 里能做到的） | 所需最小改动 |
|---|---|---|
| **SessionCounters**（会话条 TX/RX/错误计数：G#13、b1-2#2、b4-2#4、b1-5#3） | 不显示。ui 层没有可靠来源：`traffic` 回放缓冲最多 1000 条，且不随重连清零，从里面数出来的是错的 | `SessionManager` 增加 `val counters: StateFlow<SessionCounters>`（`tx`、`rx`、`errors`，每次 `publishRunning` 归零；在 `newMaster`/从站引擎发布流量处累加） |
| **previewFrame**（b1-3#3 写入帧预览） | **已做过渡版**：ui 层用 `RtuCodec.encodeRequest` 直接编码，与实际发送用同一个编码器，字节真实。但 ui-plan 约定的入口是 application 层 | `SessionManager.previewFrame(request): ByteArray`（内部调同一个编码器），ui 改调它，删 `WritePlanner` 里的直接编码 |
| **UsbDeviceSummary 授权状态**（b1-1#3） | 不属于工作台，未做 | `UsbDeviceSummary` 增 `hasPermission: Boolean` |
| **RegisterChange.source**（b1-5#1、#6 从站“来源”） | 不属于工作台，未做 | `RegisterChange` 增 `source`（本机编辑 / 外部主站 / 模拟规则） |
| **FrameDescriber**（b1-6#5 日志摘要、b6-1#3 字段解码） | 不属于工作台，未做 | core 增 `FrameDescriber.describe(bytes, kind)` 返回功能码/地址/数量/异常码 |
| **TrafficEvent.id / pairedId**（b6-1#3 请求-响应配对） | 未做 | `TrafficEvent` 增 `id: Long`、`pairedId: Long?` |
| **AddressStyle 第三档“两者”+ 默认值**（b3-2#3、D5） | 工作台跟随现有两档：选 MODICON 才显示 40001；默认（0 基）不显示，和稿面“默认两者”不同 | `UiSettings.AddressStyle` 增 `BOTH`，默认值改 `BOTH`（并做 DataStore 迁移） |
| **日志时间基数**（第 5 项排查） | `TrafficScreen` 里 `origin = events.first().monotonicNs`，是 ui 层问题，属 PR-5（日志），本轮未改 | application：`DefaultSessionManager` 在 `connect`/`teardown` 时 `_traffic.resetReplayCache()`（`MutableSharedFlow` 支持）；ui：`TrafficViewModel` 在会话 epoch 变化时清空 `history`，并把 `origin` 固定为本次会话第一帧而不是“当前窗口第一帧” |
