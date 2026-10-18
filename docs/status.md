# 当前完成状态

更新：2026-10-18。依据：`docs/spec.md`（V5.1）、`docs/ui-plan.md`、设计稿 `docs/design/v2-b1..b6.png`、`docs/注意事项.md`。

## 分支与 PR

本次核对：2026-10-18（北京时间），依据本地源码、`git fetch origin` 后的远端分支及 GitHub PR/Actions。**当前本地为 `mine-export@6cc873f`；main 仍为 `05ea1e2`，尚未整合本轮功能。** 以下是核对时的快照，不代表之后自动更新的状态。

| 分支 / PR | 基于 | 状态与内容 |
|---|---|---|
| `main` | — | 已合并至 PR #10；下节为 main 已有能力 |
| `slave-units-setup` / #9 | main | 已合并，CI 通过：从站多 Unit、配置保存、最近来源 |
| `slave-sim-rules` / #10 | 从站配置分支 | 现有表单与 ViewModel 已合并；main 尚无完整规则编辑界面 |
| `codex/slave-simulation-ui` / [#11](https://github.com/xingyingyuzhui/modbus_android/pull/11) | main | 开放，规则界面和专项测试已实现；历史代码 CI 通过，当前 PR 检查列表为空 |
| `codex/virtual-rtu-lab@7769d5b` | 模拟规则界面分支 | 已推送，尚无独立 PR；debug ADB 串口桥、主机 PTY 与 PyModbus 联调 |
| `history-store@514bfa3` / [#12](https://github.com/xingyingyuzhui/modbus_android/pull/12) | `codex/virtual-rtu-lab` | 开放，两项 CI 成功；Room 会话与点位快照 |
| `mine-export@6cc873f` / [#13](https://github.com/xingyingyuzhui/modbus_android/pull/13) | `history-store` | 开放，两项 CI 成功；“我的”历史管理、ZIP 保存与分享 |
| `task/T-20261018-qa-01-traffic-crash` / [#14](https://github.com/xingyingyuzhui/modbus_android/pull/14) | main | 开放，一项 CI 成功、一项失败；日志持续进帧时的越界修复 |
| `task/T-20261018-and-06-cl01-tokens` / [#15](https://github.com/xingyingyuzhui/modbus_android/pull/15) | main | 开放，两项 CI 失败；主题、字体、间距 token |
| `task/T-20261018-and-07-cl02-controls` / [#16](https://github.com/xingyingyuzhui/modbus_android/pull/16) | CL-01 | 开放，两项 CI 失败；按钮、输入框等控件状态 |
| `task/T-20261018-and-08-cl03-feedback` / [#17](https://github.com/xingyingyuzhui/modbus_android/pull/17) | CL-02 | 开放，两项 CI 失败；ResultBanner / ErrorBanner / UndoBar |
| `task/T-20261018-and-16-cl16-keyline` / [#18](https://github.com/xingyingyuzhui/modbus_android/pull/18) | CL-01 | 开放，两项 CI 失败；页面 keyline 与布局统一 |
| `task/T-20261018-qa-02-master-poll-bg` / [#19](https://github.com/xingyingyuzhui/modbus_android/pull/19) | main | 开放，两项 CI 成功；应用作用域 MasterPoller，离开工作台后继续轮询 |
| `task/T-20261018-and-09-cl04-workbench-write` | 控件 / 反馈相关提交 | 远端已有 WIP，未见开放 PR；读写在途、结果反馈、回读、字段错误与空态，尚未验收 |

本地 `mine-export` 已包含模拟规则、虚拟串口、历史库和导出链路；**尚未包含上述 QA 修复及 CL-01/02/03/04/16 的最新迭代**。不同分支有交叉合并，不能仅按编号顺序视为已整合。合并 main 仍需老板在聊天里明确确认。

## 已完成（在 main 上）

### 协议核心 `modbus-core`
- RTU 编解码：8 个功能码 01/02/03/04/05/06/0F/10，CRC，异常码 01–0B（value class，任意码值都能表示），数量 / 地址越界按规范回 03 / 02。
- 帧长度规则 `FrameShapes`：含 0x17 和 07/0B/0C/11/16/18 的“只识别、回不支持”。
- 主站引擎：单在途、请求间隔、超时后静默窗口、发请求前清空接收队列、不匹配的帧继续等、写超时 = 结果未知、广播 turnaround、回显抑制。
- 从站引擎：点对点 / 多站总线两种扫描器（可信起点、外站事务观察与抢占、残帧过期、不可信起点上的本机请求判为“方向不明”）、广播逐 Unit 原子写、不回复。
- 回显跟踪：关 / 开 / 自动；FC05/06 不参与自动判定；截止时间从写调用返回后起算。
- 寄存器库：改值来源（外部写 / 广播 / 本地编辑 / 模拟 / 初始装载）、旧值、新值、时间；`lastChange()`。
- 从站模拟器：常量 / 递增 / 正弦 / 方波 / 随机 / 序列，叠加随机、限幅；外部写默认暂停规则；未映射地址不新建。

### application 层
- `DefaultSessionManager`：演示设备与 USB，主站 / 从站会话，`counters`（TX / RX / 错误 / 从站统计），`previewFrame()`，`simulation`。
- 帧解码 `FrameDescriber`（日志摘要和字段详情）。
- 面板：点位编解码、轮询调度、点位存储、曲线样本（只在内存）、工程文件（原子写、读不懂原样保留）。
- 设置：DataStore。
- 日志记录器：开始 / 暂停 / 清除 / 保存，墙钟时间，容量上限。

### 界面
- 设计系统、深浅主题、强调色、字号 / 密度，悬浮岛 dock / 横屏竖排导航，会话条（主站显示收发计数）。
- 连接页；主站工作台（读 / 写、写入确认、广播红色确认、寄存器表）；从站工作台（统计格、四区表格、改值、加地址段）。
- 日志页（记录 / 分页 / 保存、帧详情）；“我的”页与设置。
- 面板：网格、编辑模式（草稿制）、添加组件向导、数值 / 指示灯 / 状态文本 / 进度条 / 仪表 / 曲线 / 开关 / 按钮 / 寄存器表、曲线全屏与快照、写入经确认。

## PR #9（已合并）
- `application/slave/`：`SlaveSetup`（Unit、地址段、值、模拟规则）、JSON 格式（读不懂的部分原样写回）、`FileSlaveSetupStore`（`files/projects/slave.json`，原子写 + `.bak` + 坏文件另存 + 新版本只读）。
- 会话层：从站会话按配置装载；`slaveUnits` / `setSlaveUnits()`（运行中在两帧之间重建从站引擎）/ `saveSlaveSetup()`。
- 从站工作台：Unit 切换 / 添加 / 删除（需确认）；“最近来源”列；改值、加地址段、增删 Unit 改为页内浮层。
- CI 加 30 分钟超时；`.kotlin/` 加入忽略。

## `slave-sim-rules`（main 上的基础逻辑）
- 已有：`ui/workbench/SimulationRuleForm.kt`（规则草稿 ↔ `SimulationRule`、逐字段校验、预览采样）；`SlaveViewModel` 里的规则列表状态、编辑草稿、保存 / 删除、暂停 / 恢复、全部暂停 / 恢复；错误提示文案。
- main 尚无规则列表和编辑页；完整界面及专项测试已在 PR #11 实现，并沿后续分支进入当前本地，不能把 main 的缺口理解成当前分支也没有。

## 从站模拟规则界面 `codex/slave-simulation-ui`（草稿 PR #11）

- 接续从站模拟规则：规则列表、全页编辑、波形预览、规则暂停/恢复与全部暂停/恢复。
- 规则草稿补 Unit、非有限数与上下限校验；参数绝对值限制为 10¹²，避免有限输入在波形计算中溢出；预览按实际刷新步数采样。
- 补六种波形往返、边界校验、ViewModel 配置一致性与 Compose 交互/布局测试。
- 增删 Unit 后保存失败向界面返回失败，运行时配置仍生效，可重试保存。
- “保存并运行”立即启动当前规则；“启动从站时自动运行”只控制重连时的状态。保存失败保留草稿并提示运行时规则可能已生效，重试沿用同一 ID。
- 本阶段已在草稿 PR #11 完成实现及验证，尚未合并 main；验证结果见下方和 `board.md`。

## 本地虚拟串口 `codex/virtual-rtu-lab`

- 在模拟规则分支之上增加本机串口对串口测试台：debug-only Android abstract LocalSocket、`adb forward`、主机 PTY 中继、独立 PyModbus 对端。Release 不含桥接 socket 名，不增加 INTERNET 权限。
- 功能代码 `607e2fe`、联调记录 `7769d5b` 已推送。本地虚拟串口双向联调已完成，记录见 `docs/virtual-rtu-lab.md` 和 `board.md`。尚未建 PR，不合并。

## 历史落库与导出（当前本地已包含，未合并 main）

- `514bfa3`：Room 会话表、点位快照表；默认 5 秒记录、保留 30 天、250 MB 软上限，支持按变化记录、质量标记、队列丢弃计数和进程重启后的未结束会话收口。
- 接线为 `AppContainer → DefaultSessionManager / PollScheduler → HistoryRecorder → Room`；磁盘写入不在主站请求锁内等待，空间不足停止记录。
- `6cc873f`：“我的”页展示存储用量、记录状态和历史会话；可保留会话、删除已关闭且未保留的会话，设置保留天数和容量。
- ZIP 可选择工程 JSON、从站配置、点表 CSV、寄存器快照、历史样本和内存通信日志，通过 SAF 保存或 FileProvider 分享；构建 ZIP 在后台线程，不申请网络权限。
- **导入未实现**。落库入口目前来自主站面板点位轮询；从站可记录会话，但没有对应从站点位采样入口。曲线内存环和完整通信帧仍未持久化；后台不额外采集。

## 远端 QA 与界面迭代（当前本地未包含）

- PR #14：`TrafficViewModel.refresh()` / 导出改为使用同一份记录快照，避免记录中持续进帧导致索引越界；有并发追加与清除的回归测试。核对时 CI 结果混合，不能记为全部通过。
- PR #19：新增应用作用域 `MasterPoller`，工作台 ViewModel 订阅状态；离开工作台后继续轮询，停止 / 断开 / 新连接 epoch 等条件负责结束。测试覆盖 VM 离开与重建、异常后的状态复位、连接后立即启动。CI 已通过，整合后的模拟器行为未在本次核对中验证。
- PR #15–18：主题 token、字体行高、按钮加载/禁用/焦点状态、输入控件、反馈组件与 keyline 已有实现，但检查仍失败。不能把源码存在或局部修复等同于整体验收。
- CL-04 工作台读写分支仍标为 WIP，完成度与整合验证待补。

## 验证情况
- 单元测试、Robolectric 界面测试、lint、APK 打包：PR #9 和 PR #10 的 CI 全部通过（`main` 合并时带 `[skip ci]`，以各分支 CI 为准）。
- cloud-workbench 云编译：`slave-units-setup@c06a316` 的 `assembleDebug` 成功。
- 模拟规则界面阶段（`1003fb7`）：本地已完成 core 58 项 + app 801 项测试、Roborazzi 录制、lint 和 APK 打包；修正测试滚动后的 6 项交互专项也通过。横屏手势区安全间距修复及 24dp 回归已通过最终复验。代码 `1003fb7` 的两项完整 CI（39049452334 / 39049448534）均已通过；之后只有文档提交。
- 本地模拟器：Pixel_9_Pro / API 35，已验证演示从站规则新建、真实改值、暂停/恢复、放弃修改、删除与空列表、键盘弹出和横竖屏布局。前半流程用隔离 QA 包验证，最后已清理旧测试包并安装正常 applicationId 的最新 APK，横屏系统手势区修复截图已确认；截图只在本地。
- 本地虚拟串口（2026-10-18 01:22，可见窗口 `emulator-5582`）：手机作从站时 PyModbus 探测 28/28 通过（9600 8N1，超时 2 秒，含广播）；手机作主站时 FC03 读回 1000..1009，FC06 将 HR0 写为 0 并收到同帧应答。主机中继单测 10 项通过。这批结果不替代 USB 与 RS485。
- 历史库 PR #12：CI [39059428601](https://github.com/xingyingyuzhui/modbus_android/actions/runs/39059428601) / [39059424562](https://github.com/xingyingyuzhui/modbus_android/actions/runs/39059424562) 均成功。
- 导出 PR #13：CI [39062224833](https://github.com/xingyingyuzhui/modbus_android/actions/runs/39062224833) / [39062065750](https://github.com/xingyingyuzhui/modbus_android/actions/runs/39062065750) 均成功。
- 轮询 PR #19：CI [39080221370](https://github.com/xingyingyuzhui/modbus_android/actions/runs/39080221370) / [39080218402](https://github.com/xingyingyuzhui/modbus_android/actions/runs/39080218402) 均成功。
- PR #15 / #18 / #17 所核对的失败运行分别为 791 项中 18 项、794 项中 17 项、801 项中 3 项失败。PR #16 最新代码 `b628444` 的 CI [39081212056](https://github.com/xingyingyuzhui/modbus_android/actions/runs/39081212056) 为 **803 项中 20 项失败**，涉及间距、尺寸、交互与颜色断言；具体应修实现还是更新设计断言需逐项核对，不能直接放宽测试。
- 本次状态更新仅核对源码、分支、PR 与 CI 日志；**未重新运行本地构建、GUI、SAF/分享或整合回归**。此前测试数量属于各自提交，不代表当前全部分支共同通过。
- 真机、真实 USB-RS485、多站总线：**未验证**。

## 已知限制
- “CRC 错”计数不显示：扫描器丢弃的字节不发布成报文，统计不到。
- 从站配置只在本地改动时保存；外部写入和模拟写入不单独触发保存。
- 模拟规则的“暂停”是运行时状态，不落盘；落盘的是“启动从站时自动运行”。
- 设置默认值中“屏幕常亮 / 震动 / 横屏自动全屏”是关（稿面为开），待老板决定。

## 待验证风险与下一步

1. 优先核对并修复 PR #15–18 的失败测试、厘清分支依赖，再做整合验证；PR #14 的失败运行也需收口。
2. 历史容量计量使用 `page_count × page_size`，删除会话不会直接缩小数据库文件；接近容量上限时，清理后仍可能停录。依据 `RoomHistoryStore.usageBytes()` 与 `HistoryRecorder.prune()`，**尚未实测**。
3. 历史导出全量读取样本，再创建 CSV 行列表和 ZIP 字节数组；接近 250 MB 软上限时有内存压力风险，**尚未实测**。
4. 补导入的大小限制、校验、预览与新建/替换流程；导入后不得自动连接、写入或启动从站。
5. 整合版本需在**有窗口的本地模拟器**验证，遵守老板“不要无头模拟器”的要求；Live Edit 尚未建立 Android Studio 调试会话，不能记为已启用。
6. 真实 USB-RS485、FTDI 延迟设置、USB 授权展示、日志请求/响应配对和运行中从站结构配置快照仍待推进。
