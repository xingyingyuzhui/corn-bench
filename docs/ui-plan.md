# Corn Modbus · UI 阶段任务说明（交给 coding agent）

日期：2026-10-16

**依据**：`docs/spec.md`（V5.1）+ `docs/design/v2-b1.png` 到 `v2-b6.png`（**以 v2 为准**；`docs/design/ui-v2.html` 用浏览器打开，地址末尾加 `#b=1` 到 `#b=6` 分别查看，里面的尺寸和颜色就是最终标注）。每个 PR 都从最新的 `main` 拉分支，做完单独提 PR。

| 设计稿 | 内容 |
|---|---|
| `v2-b1.png` | ① 主流程：连接、主站工作台（读取 / 写入）、写入确认、从站工作台、通信日志 |
| `v2-b2.png` | ② 面板与组件：面板、编辑模式、选组件类型、数值组件配置、曲线组件配置、系列编辑 |
| `v2-b3.png` | ③ 我的：我的、设置、导出与分享、导入预览 |
| `v2-b4.png` | ④ 横屏：曲线全屏、主站工作台、面板 |
| `v2-b5.png` | ⑤ 组件细节与从站面板：数值外观、状态文本映射、指示灯 / 开关选位、从站模式面板、模拟规则编辑 |
| `v2-b6.png` | ⑥ 横屏补充：通信日志、我的 |

图中编号约定：`b1-3` 表示 `v2-b1.png` 里按从左到右、从上到下数的第 3 个画面。

**总原则**：
- 界面只通过 `application/` 层和后端交互，**`ui/` 不得 import `core.rtu`、`core.transport`、`usb/`、`io/`、`service/`**。
- 协议行为一律不改，除非本说明里明确列出要改。
- 界面文案全部放进 `strings.xml`（中文）。

## 0. 全局设计规范（UI-1 实现，之后所有 PR 都用这一套）

### 0.1 颜色 token（`ui/theme/ModbusColors.kt`）
用 `CompositionLocal` 提供 `LocalModbusColors`，同时把这些颜色映射进 Material3 的 `colorScheme`，保证系统组件颜色也一致。

| token | 深色 | 浅色 | 用途 |
|---|---|---|---|
| bg | `#0D1117` | `#F4F6F9` | 页面背景 |
| surface1 / 2 / 3 | `#151B23` / `#1C2430` / `#253041` | `#FFFFFF` / `#EEF1F5` / `#E2E7EE` | 卡片 / 输入格 / 选中块 |
| line / rowLine | `#2B3544` / `#1E2631` | `#DCE2EA` / `#EDF0F4` | 描边 / 行分隔线 |
| text / muted / dim | `#E6EDF3` / `#8B98A8` / `#5C6878` | `#121A24` / `#5A6574` / `#8D97A5` | 文字三级 |
| tx（发送·主操作） | `#F5A524` | 填充用 `#F5A524`，文字用 `#B76E00` | |
| rxLocal（本机·正常） | `#3FB950` | `#1A7F37` | |
| error（异常·超时） | `#F85149` | `#CF222E` | |
| foreign（外站） | `#58A6FF` | `#0969DA` | |
| echo（回显） | `#A371F7` | `#8250DF` | |
| ambiguous（方向不明） | `#DB61A2` | `#BF3989` | |
| dropped（丢弃·过期） | `#6E7681` | `#6E7781` | |

- 每个语义色都配一个浅底色，用于 badge 背景：深色主题下透明度 0.14，浅色主题下 0.10。
- 用户在设置里选的**强调色只改主按钮和选中态**，上表的语义色永远固定。可选强调色：琥珀 `#F5A524`（默认）、蓝 `#2F81F7`、绿 `#2DA44E`、青 `#0E8A8A`、紫 `#8250DF`。
- 颜色**不能是唯一的信息来源**，每个 badge 都必须带文字。

### 0.2 字体、尺寸、密度
- 界面文字用系统默认字体（中文会自动用 Noto Sans CJK）。数值、HEX、地址统一用一款随 App 打包的等宽字体：JetBrains Mono 或 Geist Mono，两者都是 OFL 许可。放到 `res/font/`，许可证文件放进 `licenses/`。
- 圆角：卡片 16、输入格 10、chip 16、按钮 14、悬浮岛 24。
- 高度：主按钮 52、次按钮 44、小按钮 32；**所有可点击元素的触控区域不小于 48dp**。页面左右内边距 14，元素间距 8/10。
- **字号设置**（标准 / 大 / 特大 = 1.0 / 1.15 / 1.3）：在根部用 `CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale * userScale))` 叠加到系统字号上。
- **密度设置**（舒适 / 紧凑）：通过一个 `LocalSpacing` 提供间距，紧凑模式下间距乘 0.75，但触控区域不缩小。

### 0.3 悬浮岛导航和横屏
- 一级入口有 5 个：**连接 / 工作台 / 面板 / 日志 / 我的**。原来的 `monitor/`、`project/` 两个包改名为 `panel/`、`mine/`。
- 用 `material3-adaptive` 的 `currentWindowAdaptiveInfo()` 判断窗口形态：
  - 宽度为 Compact 且高度不是 Compact（竖屏手机）→ 底部**悬浮岛** `FloatingDock`：离屏幕边缘 14dp，高 64dp，圆角 24，半透明背景 + 描边 + 阴影，并且要加上 `WindowInsets.navigationBars`，不能被系统导航条挡住。
  - 其他情况（横屏手机、平板）→ 左侧竖排的**悬浮导航栏** `FloatingRail`：宽 62dp，离上下左边缘各 12dp。
- 内容区要给悬浮岛留出位置：通过 `LocalDockInsets` 把需要的 bottom（竖屏）或 start（横屏）padding 传下去。`LazyColumn` 要用 `contentPadding` 留出这块空间，**不能直接把最后一项盖住**。
- 横屏用双栏布局：工作台是"请求构造 | 结果表"，日志是"帧列表 | 帧详情"，我的是"分组菜单 | 设置详情"，面板列数从 2 变成 4。双栏用普通 `Row` + 权重实现，不引入 `ListDetailPaneScaffold`。
- 曲线全屏是一个独立页面，横屏时自动进入（受设置里"横屏自动切换曲线全屏"控制）。

### 0.4 通用组件（`ui/components/`，每个都配 Preview，深色、浅色各一份）
`SessionStrip`（每个一级页面顶部的会话状态栏，点一下回连接页）、`SegmentedControl`、`ChipRow`（支持单选、多选、横向滚动）、`FieldTile`（标签 + 数值 + 副标注，例如 `0` 旁边小字 `40001`）、`SettingRow`（开关 / 箭头 / 锁定三种样式）、`SemanticBadge(kind)`、`QualityBadge`（正常 / 过期 / 超时 / 结果未知）、`HexFrameText`（CRC 部分暗色、高亮区间可配置，用 `AnnotatedString` 实现）、`RegisterTable`、`ConfirmSheet`（基于 `ModalBottomSheet`）、`ColorSwatchRow`、`BitPicker16`。

## 1. 各 PR 的划分（一个合并后再开下一个）

### UI-1：设计系统 + 外壳（对照 b1 的连接页 + b4/b6 的横屏外壳）
- 第 0 节的全部内容：主题、强调色、字号、密度、悬浮岛 / 竖排导航栏、`SessionStrip`、通用组件。
- **设置的存储**：用 `androidx.datastore:datastore-preferences`。在 `application/SettingsRepository` 暴露 `StateFlow<UiSettings>`，包括：主题（跟随系统 / 浅 / 深）、强调色、字号、密度、屏幕常亮、横屏自动全屏、震动、地址显示方式、默认数值格式、0x 前缀、时间显示方式。
  - 屏幕常亮：在已连接并且设置打开时，给 Activity 窗口加 `FLAG_KEEP_SCREEN_ON`，用 `DisposableEffect` 管理，断开或关闭设置时去掉。
- **连接页按 v2 重做**：串口参数一键预设；"高级"折叠成一行摘要；底部按钮文案写清要做什么，例如"连接为主站 · 9600 8N1"。回显选项主站、从站都显示。
- **截图测试**：引入 Roborazzi（基于 Robolectric，在 JVM 上跑，不需要模拟器）。每个页面至少截 4 张：深色 / 浅色 × 竖屏 / 横屏（横屏用 `@Config(qualifiers = "w780dp-h360dp-land")`）。基准图提交到仓库，CI 里跑 `verifyRoborazziDebug`。**PR 描述里贴出截图**，用来和设计稿对比。

### UI-2：连接 / 工作台 / 写入确认 / 从站工作台 / 日志（对照 b1、b6 的日志页、b4 的工作台）
需要先在 core 和 application 层补这些接口：
1. `TrafficEvent` 增加 `id: Long`（单调递增）和 `pairedId: Long?`（请求和响应互相关联）。
2. core 新增纯函数 `FrameDescriber.describe(bytes, kind): FrameDescription`：返回一句话摘要（例如"读保持 U1 0×10"），以及字段列表（字节区间、名称、解码后的值、CRC 是否正确）。8 个功能码、异常帧各写一组测试。
3. `RegisterChange` 增加 `source`（`EXTERNAL_WRITE / BROADCAST / LOCAL_EDIT / SIMULATION`）、`oldValue`、`newValue`、`monotonicNs`。Bank 的写入接口加一个 source 参数（spec §4.2）。
4. `SessionManager` 新增：
   - `counters: StateFlow<SessionCounters>`：TX、RX、错误、本机请求、已响应、异常、外站帧、CRC 错、方向不明。从站引擎每 250 ms 发布一次快照，不要每来一帧就发一次。
   - `previewFrame(request): ByteArray`：写入确认弹窗用它来显示要发送的整帧字节，这样 UI 就不需要 import `core.rtu`。

页面部分：
- **主站工作台**：顶部"读取 / 写入"两种模式。
  - 写入模式下：1 个值走 FC06，多个值自动 FC16，可以强制 FC16；线圈走 FC05/FC0F。
  - 写入前一律弹出 `ConfirmSheet`：显示当前值 → 新值、发送的整帧字节，写线圈时额外加一条"可能驱动执行器"的提示。
  - 结果表支持 U16 / S16 / HEX / F32 切换；点表中某一行就打开对这个地址的写入。
  - 最近发过的请求做成快捷按钮，最多 5 个，存在 ViewModel 里即可。
- **从站工作台**：顶部 4 个统计数字；Unit 和数据区切换；每行显示"最近来源"。刚被外部写过的行做高亮：用 `Animatable` 在 2 秒内淡出，按 `RegisterChange.monotonicNs` 触发，重新进入页面不会重播动画。
- **日志**：按类型筛选、暂停自动滚动、导出入口（先只跳到 UI-6 的导出页）。列表用 `LazyColumn(key = { it.id })`；摘要文字在 ViewModel 里提前算好，不要放到 Composable 里算。横屏时右侧显示帧详情，可以跳到配对的那一帧。
- 输入框的校验直接显示在格子下方；HEX 输入支持带 `0x` 前缀。
- 图标按钮都要设置 `contentDescription`。

### UI-3a：数据换算与采集（纯 core，没有界面；spec 里属于 M4）
在 `core/conversion/` 和 `core/monitor/` 下新增：
- `PointDefinition(id, unitId, area, address, dataType, wordOrder, bitIndex?, scale, offset, decimals, unit, name, enumMap?, pollMs, staleAfter, writable, recordHistory)`。
- `DataType`：BOOL、BIT、U16、S16、U32、S32、F32、U64、S64、F64、BCD16、ASCII(n)。
- `ValueCodec`：
  - 读：原始寄存器 → 按字序重排 → 按类型解释 → 换算成工程值；
  - 写：反算回原始值，`scale = 0` 时拒绝写入，超出范围时拒绝；
  - 64 位整数不能经过 Double 中转。
- `PollScheduler`：按"一轮做完后停顿 N ms"的方式轮询（spec 推荐的模式）。同一 Unit、同一区域的相邻地址合并成一次读，不跨未配置的空洞；队列有上限；**手动发起的请求优先**。
- `PointStore`：保存每个点位的最新值和质量（正常 / 过期 / 错误 / 未知），过期时保留最后一次的有效值和它的时间。
- `CurveBuffer`：每个系列一个环形缓冲区，有点数上限；按屏幕像素分桶，每桶保留最小值和最大值来降采样，保证尖峰不丢；标出缺数据的时间段。
- 测试向量：负数 INT16、四种 32 位字序、UINT64 精度、`raw=1000 × 0.1 − 20 = 80` 的正反换算、位提取、超出范围拒绝写入、相邻地址合并、超时后标记过期。

### UI-3b：面板 + 添加组件 + 曲线（对照 b2、b5 的前 3 张、b4 的曲线全屏）
- **工程配置存储**：在 `application/ProjectRepository` 里，用 kotlinx-serialization 读写 `filesDir/projects/<id>.json`；写入时先写临时文件再 rename，保证不会写坏；解析时打开 `ignoreUnknownKeys`。在 spec §6.1 的格式上增加 `panels[]`、`widgets[]`、`points[]` 字段，都要有默认值，格式写进 `docs/project-format.md`。**样本历史这次不落库**，Room 留给 UI-6。
- **面板**：支持多个面板（顶部 chip 切换）。编辑模式下可以长按拖动排序、拖右下角改尺寸、删除；**编辑模式下不发送任何写入**。网格竖屏 2 列、横屏 4 列，组件尺寸有 1×1 / 2×1 / 2×2。
- **添加组件向导**：选类型 → 数据源与换算 → 外观，共 3 步。8 种组件：数值、曲线、仪表、指示灯 / 开关、状态文本、进度条、寄存器表、按钮。每个配置页的字段按设计稿 b2、b5 做齐，顶部预览用**真实数据**：已连接就读当前值，未连接就显示示例值。
  - 向导的状态放在一个按 nav graph 作用域的 ViewModel 里。
  - 写入相关的组件（开关、按钮、允许面板修改的数值）一律经过 `ConfirmSheet`。
  - 选"寄存器的位"作为可写开关时，要显示"先读、改一位、再写回，不是原子操作"的提示。
- **曲线**：用 Compose `Canvas` 自己画，**不引入第三方图表库**，因为缺数据时画斜线断开、双 Y 轴、游标这些行为需要完全可控。
  - 重绘频率限制到大约 10 次/秒，用 `snapshotFlow` + `sample` 实现。
  - 游标用 `pointerInput` 的拖动手势实现。
  - 系列配置：颜色、线型（实线 / 虚线 / 点线）、粗细、左轴 / 右轴、显示数据点、面积填充、阶梯线。
  - 图表配置：时间窗口、取点方式（每次采样 / 定时快照）、内存保留时长、每系列点数上限、Y 轴自动或手动范围、网格、图例位置、缺数据画法。
  - 横屏全屏页右侧显示每个系列的当前值和最小 / 最大值，有快照按钮。

### UI-4：从站面板 + 模拟规则（对照 b5 的第 4、5 张）
- core 新增 `ValueSimulator`：常量、递增（可回绕、可设上下限）、正弦、方波、随机、序列；参数包括刷新间隔、叠加随机、上下限。默认策略是 **`PAUSE_GENERATOR_ON_EXTERNAL_WRITE`**（spec §5.3）：外部主站写了某个地址，就暂停作用在这个地址上的规则。
  - 模拟器在单独的协程里运行，只通过 Bank 的写接口改值（来源标为 `SIMULATION`），**不能占用从站响应的线程**。
  - 用虚拟时间测试：正弦在 1/4 周期时的值、外部写入后规则暂停、恢复后继续。
- 从站模式下，面板上的组件直接绑定虚拟寄存器，并标出这个值当前由谁控制；模拟规则列表支持运行 / 暂停 / 恢复，以及全部暂停。

### UI-5：我的 + 设置补全 + 导入（对照 b3、b6 的"我的"）
- "我的"首页、工程切换和新建、完整的设置页（b3 里的所有分组）。"写入前确认"显示成锁定状态，不能关闭。
- **导入**：用 SAF 的 `OpenDocument` 选文件；**文件大小上限 5 MB**；流式解析；校验格式版本、地址溢出、区段重叠、Unit 重复；显示预览页（含校验结果和包含的内容）。导入方式可选"新建工程"或"替换当前"。**导入不会自动连接设备、发送写入，也不会启动从站。**

### UI-6：历史落库 + 导出与分享（spec 里属于 M5；对照 b3 的导出页）
- **Room**：`samples` 表（按 spec §6.2，默认每 5 秒一次快照）、会话表、可选的通信日志表。设置保留天数和容量上限（默认 250 MB）。空间不足时进入 `RECORDING_STOPPED_STORAGE` 状态并提示用户，**但不能影响正在进行的通信**。
- **导出**：勾选要导出的内容 → 在后台线程把文件打包成 ZIP，放到 `cacheDir/exports/` →
  - "保存到文件"：用 SAF 的 `CreateDocument`；
  - "分享…"：通过 `FileProvider` 生成 content URI，用 `Intent.ACTION_SEND` + `FLAG_GRANT_READ_URI_PERMISSION` 打开系统分享面板。
  - **不需要也不能申请 `INTERNET` 权限**，CI 里现有的检查继续保留。
  - CSV 里要带时区、数据类型、质量标志和时间来源（spec §6.1）。
- "我的"页面里的存储用量和"历史会话"列表在这个 PR 里接上真实数据。

## 2. 每个 PR 的固定要求
1. CI 全绿，并且 Roborazzi 截图验证通过。PR 描述里贴出本 PR 涉及页面的截图（深色、浅色、横屏），旁边注明对应设计稿的编号，例如"b1-3"。
2. 列出和设计稿不一致的地方及原因，比如某个控件在 Compose 里没法完全按稿实现。**不要悄悄改设计。**
3. 新增的 core 逻辑必须有单元测试；ViewModel 至少测试状态流转和输入校验。
4. 不新增网络权限，不引入 Hilt、图表库这类大型依赖。确实需要引入新依赖时，在 PR 描述里说明理由和许可证。
5. 没有真机就写"未测"，不要写成"已测"。
