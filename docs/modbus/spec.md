# Corn Modbus — Android 离线 RTU 主站 / 从站调试器产品与技术规格 V5.1

- 状态：**V5.1 首版开发基线（规格冻结候选）**；M0/M1 风险门槛与真机互操作未通过前不得宣称已完成产品验收。
- 日期：2026-10-16
- 来源：用户提供的 V4.0 文档第 0–14 章，以及对审阅问题作出的独立设计决策；**本文件不包含商业软件的反汇编内容、函数地址或行为兼容需求**。
- 角色：开发人员和 Coding Agent 的**唯一首版规范性需求、模块、行为、验收依据**。扩展路线见 `roadmap.md`；审阅决策见 `review-decisions.md`；独立参考笔记见 `research/mthings-notes.md`（不作为实现依据）。
- V5.1 变更：补齐外站事务抢占、残帧主动过期、0x17 长度与未知 FC 策略、多虚拟站广播原子性、Android 前台服务时序、分块回显及配置版本一致性；同步扩大测试矩阵。

## 0. 产品范围及决策

### 0.1 产品定义

Android 原生、完全离线、通过手机 USB-C OTG + USB-RS485 转换器收发 Modbus RTU 报文的通用调试器。支持两种**互斥**运行角色：

1. **Master（主站）**：向外部 Modbus RTU 从站读写四类数据区，单次操作、批量监控、实时曲线、历史采集和报文日志。
2. **Slave（从站）**：将手机作为一个或多个虚拟 Modbus RTU 从站，维护四类寄存器区，响应外部主站的读取与写入，支持在手机上修改虚拟值、模拟数据变化及记录访问。

同一 USB 串口一次仅运行一个角色；不存在同口主从并行收发。**不依赖联网、设备型号识别、预设点表或私有协议。** 原始地址可以直接读写，工程配置可以导入导出。

### 0.2 必做 / 不做

| 归属 | 功能 |
|---|---|
| 首版 M0–M6 | Android USB Host；串口配置；Modbus RTU 主/从；FC 0x01/02/03/04/05/06/0F/10；读写工作台；虚拟四区；单/多 Unit ID；监控点表；曲线；会话记录；本地 JSON/CSV 导入导出；通信监视器与稳定性测试 |
| 首版协议最低保障 | CRC、长度与数量校验、异常码、广播、主站单在途事务、从站对其他站请求/回复的过滤、回显治理、超时/断开、完整审计与写入结果未知状态 |
| 首版非目标 | Modbus ASCII/TCP、S7、DL/T645、云端/联网、BLE、iOS/Windows/KMP、精密空调专用规则、组态编辑器、复杂告警、流程图、PID、Lua、导入第三方软件项目格式 |
| 后续独立立项 | E1–E6（详见 `roadmap.md`），不得作为 M0–M6 的隐含依赖 |

### 0.3 一组明确的首版协议决定

- 首版只支持八个功能码：`0x01,0x02,0x03,0x04,0x05,0x06,0x0F,0x10`。
- `0x17`（十六进制 0x17，十进制 23，Read/Write Multiple Registers）**不执行**。仅识别其合法完整请求包络，且可信地确定帧方向与边界、请求发给本机 Unit ID 时，返回 `0x97 0x01`（ILLEGAL FUNCTION）；主站 UI 不生成此功能码。其 **RTU 完整请求字节数 = `13 + WriteByteCount`**（包含 Unit ID、FC、数据及 2 字节 CRC）；合法时 `WriteByteCount = WriteQuantity × 2`，`ReadQuantity = 1..125`，`WriteQuantity = 1..121`。详见 §3.2.1。注意十进制“FC17”也可能指 `0x11`，本规格一律使用带 `0x` 的十六进制标识。
- 其他未实现功能码：只有**明确建模请求包络且能可信判定完整边界、方向与 CRC**时，才可以返回 `0x01`；对未知长度/无法解释 byteCount 位置的功能码，**一律静默丢弃并进行有界恢复**。禁止盲扫 CRC 后猜测为未知请求并回复，也不得执行任何写入。
- 0 是广播地址，不是普通从站地址；普通 Unit ID 1–247，248–255 不用于普通从站配置。
- 主站广播只允许协议规定的写操作（本版 0x05/06/0F/10），**不等待回复**，而是在可确认的写出阶段后进入 `TURNAROUND_WAIT`；建议默认 150 ms，可配置，实际须按设备响应处理能力及适配器写出完成语义调校。广播不自动重试。
- 地址内部统一为 0-based PDU 地址 `0..65535`；UI 可显示 0xxxx/1xxxx/3xxxx/4xxxx，但必须明确转换，严禁隐式 +/-1。

## 1. 工程与模块

采用两个 Gradle 模块，不做为了未来平台扩展的额外抽象：

```text
modbus-mobile/
├── settings.gradle.kts
├── app/                             # com.android.application；Android 平台实现
│   └── src/main/java/.../
│       ├── ui/                      # Compose：连接/主站/从站/曲线/日志/工程
│       ├── application/             # SessionManager、RoleController、use cases
│       ├── usb/                     # UsbSerialTransport、USB 授权、芯片特性
│       ├── io/                      # SerialIoWorker、响应时间与关闭管理
│       ├── storage/                 # Room、DataStore、留存策略
│       ├── exchange/                # SAF + JSON/CSV 解析/导出
│       └── service/                 # 可选 connectedDevice 前台服务
└── modbus-core/                     # org.jetbrains.kotlin.jvm，**无 Android 依赖**
    └── src/
        ├── main/kotlin/.../
        │   ├── model/               # 帧、功能码、结果、错误、数据类型
        │   ├── rtu/                 # CRC、编解码、帧扫描、关联上下文
        │   ├── master/              # MasterEngine、单事务 Actor、轮询规划
        │   ├── slave/               # SlaveEngine、VirtualRegisterBank、模拟器
        │   └── conversion/          # bit/16/32/64 位、字节序、缩放
        └── test/kotlin/.../         # JUnit 单测、向量测试、模糊输入测试
```

依赖方向：`app -> modbus-core`。`modbus-core` 不引用 `android.*`、Compose、Room 或 USB 库；编译期确保协议核心是纯 JVM。当前工程不考虑跨平台发布。

协议核心需要的时钟、接收事件、原始写出通过小接口注入，便于在纯 JVM 测试中替换。

```kotlin
interface ByteTransport {
    suspend fun send(frame: ByteArray): SendResult
    val received: Flow<ByteChunk>
    val connectionState: StateFlow<ConnectionState>
}

data class ByteChunk(
    val bytes: ByteArray,
    val receivedAtMonotonicNs: Long, // 主机收到这一批字节的时刻，绝非逐字节线上时间
    val connectionEpoch: Long
)

enum class Role { MASTER, SLAVE }
```

`SendResult` 必须区分“交给 USB 底层”“写调用返回”“可确定线上已完成（若适配器支持）”等证据等级；不能假定 USB API 的 `write()` 返回意味着 RS-485 最后一位已经发送完毕。

## 2. USB 串口传输与 I/O 生命周期

### 2.1 技术决定

- 使用 Android `UsbManager` 获取 USB 授权、枚举设备与接口。
- 复用 `usb-serial-for-android` 的主流芯片驱动，不自行实现 FTDI/CH340/CP210x USB 控制传输。
- **首版默认采用 `UsbSerialPort.read()` / `write()` + 一个专用 I/O 工作器**，读调用使用有限超时，`readQueue` 与有限超时读不可同时开启；写由唯一所有者串行化。先在 M0/M3 证明其稳定性。`SerialInputOutputManager` 仅作为后续可替代实现进行对照测试，**首版不得把两条收包链同时开启**。
- USB Java 阻塞调用不具备 Kotlin 协程自动中断语义。关闭顺序：标记 stop / session epoch++ → 禁止新请求 → 关闭端口中断阻塞读（必要时按驱动语义关闭 USB connection）→ join 专用线程（有界超时）→ 释放剩余 USB 资源 → 清空接收缓冲。若线程无法及时退出，报告异常；不得悄悄启动第二个 I/O 所有者。所有收到的 `ByteChunk` 必须带对应 epoch。
- 串口参数 `baudRate/dataBits/parity/stopBits/responseTimeoutMs/interRequestDelayMs` 均为可配置；断连必须使 UI 显示离线，所有在途事务过期。

### 2.2 FTDI 延迟与 USB 颗粒度

- FTDI 某些产品接收延迟定时器典型默认 16 ms，会影响小报文交付给 Android 的时间；**不能把此数值当作所有品牌转换器的固定值**。
- 检测 FTDI 端口与库支持后，在高级设置提供 `Auto` / 手动延迟毫秒数与读取当前值；试验可从 2–16 ms 范围观察，但必须以该芯片支持范围与真实测试为准，不强制设为 1 ms。
- `FtdiSerialPort.setLatencyTimer/getLatencyTimer` 已由底层库提供；平台适配器可按能力调用，设置失败需记录但不能崩溃。
- 测量 `请求线上接收完毕 → Android 收到完整字节 → 形成响应 → USB 写入返回 → 外部主站收到完整响应` 的 p50/p95/p99；无总线硬件时间戳时报告**端到端时间**，不能冒称精确的物理分项延迟。

### 2.3 回显

USB-RS485 转换器可能将自身发送字节回送接收端，默认**保留 TX 历史并提供显式回显兼容选项**；是否自动启用必须通过设备实测。接收链在 TX 阶段对齐最近发出的完整字节串，**匹配状态必须跨越多个 USB `ByteChunk`**；确认可信的自回显后才能抑制该帧。

禁止按“某报文最近发送过”永久过滤，因为外部主站后续发送的完全相同的合法请求不能被误删（0x05/0x06 的请求与回复本身可以一致）。对方向无法判定的候选帧，宁可标记 `AMBIGUOUS_DIRECTION` 丢弃，也不能触发第二次写入或递归回复。

### 2.4 分块回显状态机（V5.1 必做）

- 维护 `EchoCandidate(txId, connectionEpoch, txBytes, matchedPrefix, startedAt, deadline)`，候选长度和生命周期均有上限；示例 `TX=[A B C D E F]`、RX 分三批 `[A B] + [C D] + [E F]` 时，应按**完整发送帧**确认回显，而不是逐批过滤。
- 回显候选仅关联本次发送阶段及设备/驱动支持的方向信息；不能因为“历史上发过相同字节”而过滤未来报文。尤其 0x05/0x06 请求与正常响应同形。
- 前缀匹配失败：缓存字节须依据已确认的可信帧起点重新进入分类，若起点或方向已失去可信性则安全丢弃；不得把半个候选当成可执行写入，不得无条件吞掉不匹配字节。
- 多个候选均可解释同一字节流时标记 `AMBIGUOUS_DIRECTION`，不发响应、不修改虚拟寄存器。纯软件无法保证区分所有回显与合法重复报文，真机兼容模式必须说明适用的转换器/总线条件。
- 单元测试覆盖分块回显、前缀中途失败、时间窗过期、相同 FC06 重复合法请求、TX/RX 交错及角色切换/断连时清空旧 epoch 状态。

## 3. Modbus RTU 协议

- 帧：`[UnitId:1][Function:1][Data:N][CRC Low:1][CRC High:1]`，ADU 最大 256 字节。
- `CRC16/MODBUS` 反射多项式 `0xA001`、初值 `0xFFFF`；校验向量 `01 03 00 00 00 02 C4 0B`；`123456789 -> 0x4B37`。
- 请求和响应长度分别定义：0x01–0x04 读取请求为 8 字节；读取正常回复 `5 + byteCount`；0x05/0x06 请求与正常回复均为 8 字节；0x0F/0x10 请求 `9 + byteCount`、正常回复 8 字节；异常回复 5 字节。0x17 请求 **`13 + WriteByteCount`**，只作为本版“识别完整且可信请求后返回不支持”的辅助形态。
- 保证所有数量、byteCount、地址末尾范围、CRC、对应功能码和站号都独立校验。CRC 正确不意味着操作参数合法。
- RTU 定义大于等于 `t3.5` 的帧间静默；Android USB Host 不可靠提供物理逐字节到达时刻。`t1.5/t3.5` 的实现与硬件行为必须明确记录，不以 USB `read()` 返回间隔宣称符合物理线路的定时要求。**软件恢复不是严格 RTU 边界检测的等价替代**。

### 3.1 主站帧处理

Master 单总线在途事务约束：`IDLE → SENDING → AWAITING_RESPONSE → VALIDATING → DONE/FAILED/UNKNOWN`。响应需匹配当前 `connectionEpoch + unit + function + byteCount/数量 + CRC`，异常响应的功能码为 `requestedFunction | 0x80`。

- 手动写入默认优先于轮询，但仍须公平调度和有界队列。
- 写入超时或拔线，无法判断是否已执行时返回 `UNKNOWN_OUTCOME`；禁止盲目自动重试。
- 读超时可按显式策略有限重试；迟到响应不得匹配新请求。
- 广播写：发送后不建响应等待项；进入 `TURNAROUND_WAIT`，等待配置的处理间隔，再允许下一次事务。
- 接收噪声或错误帧不更新有效点位。

### 3.2 从站面对**整条总线**的帧扫描器（重点修复 V4）

**两级模型**：`RtuBusFrameScanner` 按字节流发现候选帧并分类，`SlaveRequestDispatcher` 仅处理确定是发给本地 Unit ID 的完整请求。不能将整条 RX 流交给只理解请求结构的 `RtuRequestParser`。

#### 3.2.1 帧类型规则

| FC | 请求长度 | 正常回复长度 | 风险 |
|---|---|---|---|
| 01/02 | 8 | `5 + byteCount` | 主站请求和别站回复长度不同 |
| 03/04 | 8 | `5 + byteCount` | 其他站回复可能带有本机 Unit ID 字节作为数据 |
| 05/06 | 8 | 8，且内容同请求 | 需事务方向上下文判断 |
| 0F/10 | `9 + byteCount` | 8 | 先识别请求/应答上下文 |
| 异常响应 | 不适用 | 5 | `FC | 0x80`，不能作为请求执行 |
| 0x17 | `13 + WriteByteCount`（`byteCount` 位于 ADU 偏移 10） | 不在本版解析响应 | 可信合法本机请求才返回 `0x97 0x01`；本功能不执行 |

**0x17 明确的 ADU 字节偏移（仅用于确认不支持）：**

```text
0          Unit ID                       1 byte
1          Function = 0x17               1 byte
2..3       Read Starting Address         2 bytes
4..5       Read Quantity                 2 bytes
6..7       Write Starting Address        2 bytes
8..9       Write Quantity                2 bytes
10         WriteByteCount                1 byte
11..10+N   Write Register Values         N bytes
11+N..12+N CRC16                         2 bytes
总长度 = 13 + N, N=WriteByteCount=2×WriteQuantity
```

解析时首先等待完整的 11 字节头，再按 `N` 估算需要的剩余字节；限制 `ReadQuantity 1..125`、`WriteQuantity 1..121`、`N=2×WriteQuantity`、完整 ADU 不超过 256 字节，最后核对 CRC 与方向。**不能使用 `11 + N` 作为完整 RTU 长度**；无法可信确认边界时静默丢弃。

#### 3.2.2 分帧优先级与方向上下文

1. 读取字节流到**有界缓冲**（最大待处理窗建议不超过若干完整 ADU；实现时定义常量与逐次上限；不得无限累加）。每批读可能包含半帧、多帧和任意断点。
2. 维护 `ObservedTransaction(unitId, fc, expectedResponseShape, observedAt, responseDeadline, epoch)`，记录**观察到的、来自外部主站**的其他站合法请求。下一条对应站号/功能码/长度/CRC 的回复在上下文匹配后**作为回复跳过**。`responseDeadline` 按可配置的 `responseTimeoutMs` 与单调时钟计算，不能与候选帧组装超时混为一谈。
3. 先判断可能完整的**已知外站响应**及**自身 TX 分块回显**；再从首个可信帧起点检测请求/响应的长度候选与 CRC；仅允许完整合法帧进入分类。禁止从尚未消费完的外站回复 payload 内嵌的 CRC 子串中抽取本机请求。
4. 对任何可能代表多个合法解释的候选（特别是 05/06 的方向），优先依靠已知事务与 TX 状态。**无法消歧时不发送响应、不更新 RegisterBank**。
5. 遇到损坏、未知、超时帧：有界 CRC 候选扫描和前缀滑动恢复。需要同时限制最大扫描长度、尝试次数及等待时长，以避免噪声导致 CPU 消耗和内存增长；扫描得到 CRC 正确也仍需结构、上下文、可信起点校验。**完整且可信的新本机请求允许抢占已经失效/不匹配的外站预期事务，但不可在未确认帧起点或可能的外站响应 payload 内抢占。**
6. 对不能可靠界定起点或终点的未知功能码，宁可静默丢弃，不执行，**不猜测是否应返回异常**。
7. `UnitId` 过滤应当发生在识别完整候选帧之后，不能看第一个字节与本机不匹配就按 8 字节截断。

**限制声明**：在不具备物理总线级逐字节时间戳/空闲边界的 USB-RS485 组合上，纯软件不可能消除所有方向与帧边界歧义。对连续高负载多从站总线、不受控回显、非标准短间隔和碰撞场景，需标明兼容性限制并做实机验收。**不以 CRC 扫描代替从站硬实时承诺**。

#### 3.2.2a 外站事务抢占、候选帧到期与未知功能码（V5.1）

**外站预期响应不会独占接收器。** 当先前观察到发往外站 X 的请求，但该站长时间没有回复时，状态机可能遇到外部主站随后发给本机虚拟站 Y 的新请求。应满足以下决策顺序：

1. 首先从**可信的下一个帧起点**完整识别候选帧，核验 Unit、功能码结构、CRC 和已接收字节；禁止仅在任意偏移扫描到“Unit=Y 且 CRC 对”就触发抢占。
2. 如候选完整匹配 X 的预期回复，按外站回复消费并关闭 `ObservedTransaction`；若候选完整匹配 Y 的合法请求、**与 X 预期响应互斥**，则立即将旧事务标记 `PREEMPTED_BY_NEW_REQUEST`，进入 Y 的请求分派，**不必硬等旧 `responseDeadline` 到期**。此标记不是对 X 已经物理超时的事实断言。
3. 如两个解释都成立，或当前候选可能仍是未完整收齐的 X 响应，标记 `AMBIGUOUS_DIRECTION`，不执行任何写入；按照帧组装/事务截止时间恢复，不能“猜一个”。
4. 到达 `responseDeadline` 时仅过期 X 的**事务观察上下文**，不应自动把当前未完整帧的剩余字节当作新请求；新事务也需要满足可信起点条件。
5. 主动时间事件由 `Scanner.onClockTick(nowMonotonicNs)`（或等价的独立时钟驱动）触发；即使 USB 暂无新 `ByteChunk`，超时仍需处理。使用可注入的 `MonotonicClock` 在 JVM 测试中虚拟推进。

**陈旧残帧（Stale Partial Frame）：** 给每个未完成候选保存 `firstChunkAt/lastChunkAt/frameAssemblyDeadline/connectionEpoch`，缓冲达到上限或计时过期要主动清空并记 `STALE_PARTIAL_FRAME`。`frameAssemblyDeadline` 必须是**单独的组帧预算**，至少覆盖所支持波特率下可能完整 RTU ADU 的线上时间、USB 批次交付余量与线程调度余量，并结合最大帧长、串口参数和真机校准决定；不可硬编码 `max(100 ms, 3 × FTDI_Latency)` 作为跨设备正确阈值。一个新的 `ByteChunk` 若与残帧跨越已校准的保守空闲阈值，可在 append 之前丢弃陈旧残帧，但不能据此声称精确检测到 RTU `t3.5`。

**未知功能码：** 只有已建模长度/结构包络的已知功能码（包括仅为拒绝而建模的 `0x17`）能进入完整请求判定。其他未知 FC 无通用“长度字段位置”，即使某一滑动窗口的 CRC 恰好匹配，也不得仅凭此猜测帧尾并发送 `ILLEGAL_FUNCTION`；应有界恢复且静默丢弃。保留统计 `UNKNOWN_UNFRAMED_FC`。

**响应延迟说明：** Android 无物理线路精确时间戳时，日志区分“观察到的字节块时间”“推测帧就绪时刻”和“实际业务处理耗时”，不得将推测时间标注为线路测量事实。

#### 3.2.3 请求分派规则

- 目标 Unit ID 为已启用的本机虚拟站，CRC、方向、完整帧与参数均有效：分派处理。
- 发往其他站的请求和其他站的正常/异常回复：不处理，不回复，更新被动统计。
- 地址 0 广播合法写：对启用且相关地址有效的虚拟从站执行对应写，**无回复**；广播读不响应。广播处理前固定本次启用 Unit ID 的集合与配置版本；逐站独立检查映射并原子提交该站的整个写请求，一个站失败/未映射不影响另一站成功更新；**不要求跨多个 Unit ID 的全局原子提交或回滚**。
- 本机请求不支持但报文边界可确知：异常 `ILLEGAL_FUNCTION (0x01)`；不存在的映射区：`ILLEGAL_DATA_ADDRESS (0x02)`；非法数量或数据：`ILLEGAL_DATA_VALUE (0x03)`。
- 错 CRC、截断、错误方向、自回显、无法判定的候选：不回包，不修改寄存器。
- 响应关键路径只进行内存 Bank 的快照/原子写与报文编码，异步发布变化，不阻塞等待 Room、CSV、Compose 或模拟器运算。

### 3.3 主从功能码边界

| FC | 功能 | 约束 |
|---|---|---|
| 0x01/0x02 | 读取线圈/离散输入 | 1–2000 bit |
| 0x03/0x04 | 读取保持/输入寄存器 | 1–125 registers |
| 0x05 | 写单线圈 | ON `FF00`、OFF `0000` |
| 0x06 | 写单保持寄存器 | 16-bit |
| 0x0F | 写多线圈 | 1–1968 bit；字节计数严格校验 |
| 0x10 | 写多保持寄存器 | 1–123 registers；字节计数严格校验 |

主站外部写入 Coil 与 HR；从站 UI 可以手动修改全部四类虚拟数据区，但来自外部主站的写命令不能修改 DI 或 IR。

## 4. 从站寄存器引擎

`VirtualRegisterBank` 按 `unitId + area + address` 唯一定位单元，四区独立：Coil / DI 为 Boolean；IR / HR 为原始 `UInt16` 位模式（内部可用 Int 范围 `0..65535`）。

- 支持稀疏映射与批量初始化。跨过未映射区域的请求统一返回 `0x02`，不自动补零。
- 读取得到一致快照；多字写入一次性验证后原子应用，不出现部分提交。
- 从站启动时明确 Unit ID 列表；多个虚拟站共用一个物理 SlaveEngine 与 RX 处理链，不可各自打开串口。
- 外部写入成功先更新内存并形成协议响应，再异步记录变更日志；保证下一次读取能观察到新值。
- 首版基础值模拟包含常量、手动修改、批量赋值；动态 Counter/Ramp/Sine/Random/Toggle 归属 M4 增强项，须确保模拟器不会抢夺响应线程。
- 发生模拟器与外部写入竞争时默认暂停该地址的模拟规则，防止刚写入即被覆盖；可在 UI 展示明确来源。

### 4.1 广播写的原子性边界（V5.1）

- 同一物理 SlaveEngine 可模拟 Unit 1、Unit 2 等多个 Unit ID。对 `UnitId=0` 的合法写入，在处理开始时获取“启用 Unit 集合 + 对应映射 revision”的一致快照，按固定顺序遍历。
- **每个 Unit 的原子性：** 先完整校验地址范围、功能码与值域，再一次性提交该 Unit 的所有位/寄存器；任一部分非法则对该 Unit 完全跳过，不产生部分写入。
- **Unit 之间相互独立：** Unit 1 已映射成功写入，不因为 Unit 2 未映射而回滚；Unit 2 静默跳过。无论结果如何，**广播绝不回复异常或正常报文**。
- 记录各 Unit 的处理结果与寄存器变化事件，区分 `APPLIED/SKIPPED_UNMAPPED/SKIPPED_DISABLED`；这些是本地统计，不是协议响应。

### 4.2 运行中配置版本与请求一致性（V5.1）

- 将**值编辑**与**结构性配置**分离。手动改数值或外部 Modbus 写入在对应 Bank 内原子提交；Unit ID、启用状态、寄存器稀疏映射、数据区长度等变更使用不可变 `ConfigSnapshot(revision)`。
- 一条已经进入分派的请求必须绑定某一个 `configRevision + connectionEpoch`，从 CRC 验证到生成响应的地址映射保持不变；不得执行到一半从旧地址映射切到新地址映射。
- 用户在运行中修改结构配置：后台先校验新快照，按明确事务边界切换（或要求暂停从站）；涉及 Unit ID 变更建议先暂停监听再提交。旧 revision 的排队命令与过期回调不得污染新配置。
- `RegisterChanged` 包含来源、unit、area、address、旧值、新值、revision、epoch 和单调时间；UI、历史与模拟器不应根据格式化文本反推数据。

## 5. UI 与用户操作闭环

五个一级入口，按角色切换工作台内容：

1. **连接**：USB 设备/端口、串口参数、从站 ID / 主站目标 ID、超时、FTDI 延迟高级参数、回显开关与实际连接质量。
2. **工作台**：主站四区裸地址读写与字节序/数据类型解释；从站 Unit ID、四区映射与当前值编辑。
3. **监控/模拟**：主站采样点表/实时曲线与历史；从站虚拟值、批量编辑、模拟规则与访问次数。
4. **通信日志**：方向、Unit、功能码、地址、CRC、原始 HEX、来源类别（外站请求/外站回复/本机响应/回显/不确定）与关联号。
5. **工程/文件**：会话与工程预设、JSON/CSV 导入导出、历史存储配额、日志文件。

用户可以**不导入文件直接读写**。写入前预览 Unit、区、地址、原始值和发送字节；从站 UI 手动修改不向线路主动发包。

## 6. 数据模型、项目文件和导入导出

### 6.1 自有 JSON 工程格式

```json
{
  "format": "modbus-mobile-project",
  "schemaVersion": 1,
  "projectName": "Example",
  "preferredRole": "SLAVE",
  "serial": { "baudRate": 9600, "dataBits": 8, "parity": "NONE", "stopBits": 1 },
  "master": { "defaultUnitId": 1, "monitorPoints": [] },
  "slave": { "units": [{
    "unitId": 1,
    "regions": [
      { "area": "HOLDING_REGISTER", "startAddress": 100, "values": [10, 20, 30] },
      { "area": "COIL", "startAddress": 0, "values": [true, false] }
    ]
  }] }
}
```

这是新产品的私有格式，不承诺兼容 MThings 或其他商业软件工程。可导出工程 JSON（完整配置）、点表 CSV、寄存器快照 CSV、会话采样 CSV、通信日志 CSV/HEX。地址统一 0-based，CSV 标明时区、数据类型、记录质量与时间来源。

导入流程：SAF 选文件 → 流式解析 → schema/大小/值域/区间重叠/重复 Unit/地址溢出校验 → 展示差异 → 用户确认 → 原子替换或新建项目。导入**绝不自动向外写入或启动虚拟从站服务**。

### 6.2 记录与容量上限（M5 强制）

50 点 × 1 Hz × 86,400 秒 = **4,320,000 条/日**，不应默认将每次刷新永久保存为一行。必须分离：

- UI 实时流：按采样实际频率更新、保留适量内存环形缓冲。
- 本地历史：默认**按 5 秒一次快照**保存开启历史的点（可配置为 1/5/10/60 秒或仅变化）；默认不持久化全部通信帧，按需开启完整抓包。
- 内存日志：有界环形缓冲，例 `10,000` 条为初始目标，长日志由过滤/分页查询，而不是全部载入 UI。
- 设备存储：项目级设置**保留天数**与**软容量阈值**（建议初始 250 MB、可调）；超限后按明确政策清理最旧数据，不能静默删除用户标记为保留的会话。空间不足必须报告 `RECORDING_STOPPED_STORAGE`，**停止记录不应中断正常 Modbus 通信**。
- Room 批量事务写入、后台有界队列、按时间和点位建立索引、流式导出。保存记录时保留采样时间 `sampleAt`、入库时间 `recordedAt`、连接代次、质量标志。长期记录超过总容量须给出可解释的提示和统计。

需要以真机压测来确定最终阈值；上述 250 MB / 10,000 条是产品初值，不是平台保证。

## 7. 生命周期、状态机与错误分类

- USB 连接会话由独立 `SessionManager` 管理，不绑定 Compose 页面生命周期；角色切换先关闭旧 loop 与 IO owner，再启动新角色。
- 主站请求：`QUEUED/SENDING/AWAITING/VALIDATING/SUCCEEDED/FAILED/UNKNOWN_OUTCOME`；广播独有 `TURNAROUND_WAIT`。
- 从站：`STOPPED/CONFIGURED/LISTENING/PROCESSING/RESPONDING/STOPPING/ERROR`。
- 原始报文和解析结果必须记录 `connectionEpoch` 防止重连后旧帧污染新事务。通信质量：`GOOD/STALE/BAD/UNKNOWN`，显示 last-good 时间；超时不得将旧值标成新样本。
- 长时间锁屏运行应使用符合 Android `connectedDevice` 要求的前台服务；无前台服务时 UI 必须说明采集可能停止。USB 拔掉后应用不得继续显示“正在响应”。服务的启动前提、权限以及串口打开先后顺序详见 §7.1。
- 网络权限：首版不申请 `INTERNET`，不包括局域网权限；完全不需要运行中联网。

### 7.1 Android 14+ `connectedDevice` 启动顺序（V5.1）

1. 用户在前台明确发起连接，`UsbManager` 枚举设备；尚无权限时调用 `UsbManager.requestPermission(device, ...)`，等待授权结果并确认 `hasPermission(device)`，拒绝时只显示错误，不启动访问硬件的业务。
2. `AndroidManifest.xml` 声明适用的 `FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_CONNECTED_DEVICE` 及 `android:foregroundServiceType="connectedDevice"`，按 Android 官方前台服务类型的**实际运行前提条件**启用服务。对 USB 通路，官方列有调用 `UsbManager.requestPermission()` 这一条件；**串口打开成功不是 Android API 规定的服务启动必要前提**，但实际读取/写入前必须拥有 USB 权限。
3. 从前台用户操作调用适用的 `startForegroundService` / `ContextCompat.startForegroundService`，Service 创建后在系统要求时限内使用通知及时 `startForeground(..., FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)`，随后由 Service 独占开启 USB 串口及 I/O 工作器，或在及时升前台的同时串行管理打开操作。**不能先耗时打开端口再无限推迟升前台**。
4. 捕获 `SecurityException`、`ForegroundServiceStartNotAllowedException`（按 API 级别）及端口打开失败；正确通知用户、释放已获取资源、停止服务，禁止隐蔽重试或启动双重 I/O 所有者。
5. 断开、USB detach、授权失效、用户停止时：停止事务与 RX/TX、提升 epoch、关闭串口、更新 UI 状态并停止或降级前台服务；遵守 Android 后台启动限制，不能在没有合规前提时从后台任意重建服务。

**参考**：https://developer.android.com/develop/background-work/services/fgs/service-types 及 https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start 。实际 targetSdk 与设备 Android 版本纳入 M0/M6 验收矩阵。

## 8. 逐阶段交付与阻塞项

| 阶段 | 输出 | 通过条件 |
|---|---|---|
| M0（与 M1 早期并行） | Android USB 授权、connectedDevice 服务、打开/关闭端口、原始收发、OTG 实测 | 实机发现端口、正确升降前台服务、插拔 10 次不产生僵尸 I/O 线程 |
| M1（与 M0 早期并行） | 纯 JVM `:modbus-core`、CRC、请求/响应编码、**BusFrameScanner**、抢占/独立超时、分块回显与广播调度 | 通过无 Android 依赖检查、虚拟时钟测试及 F01–F06/F09/F10/F16–F20/F23 等关键场景 |
| M2 | MasterEngine、手动读写、单在途队列、异常/超时/广播 | 对独立真实从站八个 FC 互测；广播无等待；未知写入结果可观察 |
| M3 | SlaveEngine、四区 Bank、单/多虚拟 Unit、标准异常、映射配置版本 | 外部主站八个 FC 互测；**混合多站总线不会把其他站回复当成本机请求**；广播逐站独立原子提交；回显不触发重复写 |
| M4 | 主站监控/曲线、从站值模拟、调试工作台 | 完成双模式日常使用闭环；界面切换不重启端口 |
| M5 | Room 持久化、自有 JSON/CSV、存储策略、流式导出 | 离线文件往返准确；容量/磁盘满场景报告正确、不拖慢通信 |
| M6 | 性能/稳定性/可访问性与 UI 打磨 | 通过标准测试集、真机多芯片测试与 1 小时运行；形成 p50/p95/p99 延迟报告 |

**开发启动方式：先建立双模块 Gradle 工程；M1 纯 JVM 内核与 M0 Android USB 最小验证尽早并行开展。** 在继续大量 UI 工作前设置第一个联合验收节点：真机 USB-RS485 完成一轮主站请求/从站响应和外站请求+回复混流检查。M1/M3 为首版关键阻塞项，不能用“少量点对点通信成功”替代多站 RX 混合流验证。

### 8.1 必做测试向量（可以直接拆为测试文件）

| 测试 ID | 场景 | 必须行为 |
|---|---|---|
| F01 | FC03 外站请求 8 字节 + 外站响应可变长 + 本站请求拼接 | 正确跳过两条外站帧，只处理本机请求 |
| F02 | FC05/06 外站请求/原样回复 | 用上下文识别，绝不将回复作为本机新请求 |
| F03 | 外站 FC03 回复 payload 中含本站 Unit ID + 0x05/06 字节 | 不从 payload 中误切出本站请求 |
| F04 | 含前导噪声、坏 CRC、截断帧、逐字节与多帧同 chunk | 有界恢复，不执行未知写入、不积累无限内存 |
| F05 | 已确认回显适配器，本机发送 FC06 回复后 RX 收到完全相同内容 | 不再次修改虚拟 Bank、不再回复 |
| F06 | 无回显适配器，外部主站短时间重复发送相同 FC06 请求 | 不误删合法重复请求 |
| F07 | 从站广播 0x06/0x10 | 根据定义更新适用虚拟站，不回复 |
| F08 | 主站发送广播写，随后按最短配置间隔发普通查询 | 必须执行 turnaround delay，不等待广播回复 |
| F09 | 合法 `0x17` 发往本站，含 `WriteByteCount=2/242` 边界 | **`13+N` 完整长度**，若可信识别完整帧则返回 `0x97 0x01`，数据区不变 |
| F10 | 未建模长度的未知 FC，输入含偶然 CRC 匹配 | 静默丢弃、有界恢复，绝不凭盲扫 CRC 臆造异常回应 |
| F11 | 断线时阻塞 `read()`、角色快速切换 100 次 | 所有 I/O 工作器退出，不启动双所有者 |
| F12 | 配置中的 FTDI latency 变更、其他品牌转换器 | 支持能力检测、失败回退；响应延迟报告可复现 |
| F13 | 50 点 1 Hz 连续采集 + 默认 5 秒落库，模拟磁盘写满 | 数据量有界，UI 与通信运行不受阻；记录停止有提示 |
| F14 | 非法 JSON/CSV、恶意超大 JSON、重叠虚拟区与溢出地址 | 导入拒绝且不破坏既有项目 |
| F15 | 主站写操作超时、迟到成功回复、断线重连 | `UNKNOWN_OUTCOME` 不盲重试；旧连接帧不匹配新请求 |
| F16 | 外站请求后无响应，尚未到观察事务 deadline，可信起点收到本站完整请求 | 旧观察事务标记 `PREEMPTED_BY_NEW_REQUEST`，**不等到 deadline 才处理本站请求**；不虚报外站实际超时 |
| F17 | 外站响应 payload 内包含完整 CRC 正确的“本站写请求”候选 | 不从已识别外站响应 payload 中抢占/执行；方向模糊时丢弃 |
| F18 | 只收到 5/8 字节，之后无任何新 `ByteChunk`，虚拟单调时钟推进 | 主动 timer tick 清理残帧，记录 `STALE_PARTIAL_FRAME`，不等待下一批 USB 字节 |
| F19 | 低波特率下合法长帧分多批交付 / FTDI 接收延迟变化 | 合法长帧不因固定 100 ms 超时被误丢；配置化、可测组帧预算 |
| F20 | 0x17 请求 `N=2`、最大 `N=242`、`N!=2×WriteQuantity`、不完整报文 | 正确按 `13+N` 解析；合法完整请求返回功能异常；畸形/截断帧不执行、不错误回复 |
| F21 | Unit 1 映射 HR100，Unit 2 未映射；广播 FC06/0x10 | Unit 1 原子更新、Unit 2 不更新、无跨站回滚、无总线响应 |
| F22 | 一次外部读取处理中 UI 调整映射/Unit ID，广播同时发生 | 单请求固定 `configRevision`，结构变更安全切换；广播使用冻结的 Unit 集合 |
| F23 | 已发送 FC06 回显分三块，随后合法重复 FC06 请求 | 分块回显仅抑制本次已确认 TX；重复请求不因历史 TX 被永久过滤；不能消歧则明确丢弃 |
| F24 | Android 14+ USB 授权拒绝/授予、服务启动异常、端口打开失败、拔线 | 无 `SecurityException` 崩溃；满足 FGS 运行前提及及时升前台；失败有序停止服务和 I/O |
| F25 | 观察事务等待外站回复期间本机请求与响应候选形态同时成立 | 标记 `AMBIGUOUS_DIRECTION`，既不写 Bank 也不回复；对后续帧可有界恢复 |

补充：在真实多站 RS-485 总线上用外部主站 + 至少一个其他真实从站 + 手机虚拟从站验证 F01–F06、F16–F17、F23/F25；纯 JVM 单测不能证明线路层时序兼容。F24 应在不同 Android API / target SDK 组合上实测。

## 9. 验收门槛与风险声明

- 核心协议测试全部通过（含合法、不合法、模糊边界、断线）；F16–F25 纳入正式质量门槛，协议测试不得依赖墙上时间 sleep 实现确定性断言。
- 正常调试模式下 App 无崩溃、死锁、无限积压、后台数据库拖慢回复问题。
- 实机上连续工作至少 1 小时，锁屏/插拔按 UI 声称的支持范围验证；对目标 USB 芯片记录从站响应时间的 p50/p95/p99 与失败率。
- 多站同线中的其他站回复/本机 TX 回显不可触发错误读写；若硬件无法可靠辨识方向，应明确显示“该适配器/拓扑从站兼容性受限”，而不是标注“完全支持”。**直接连接单主站的从站仿真**是基础支持场景；**接入既有多从站总线**列为高级互操作矩阵，不承诺所有适配器与复杂总线拓扑均可可靠解析。
- 工程 JSON/CSV 可在飞行模式导入导出，数据未被隐式发送外部服务器。
- M0–M6 完成才对外宣称“通用 Modbus RTU 主从站调试工具”；不将任一 E 项纳入首版“完成”定义。

## 10. 参考规范与工具（仅原始/公开技术资料）

- Modbus Application Protocol: https://www.modbus.org/file/secure/modbusprotocolspecification.pdf
- Modbus over Serial Line Specification and Implementation Guide V1.02: https://www.modbus.org/file/secure/modbusoverserial.pdf
- Android USB Host: https://developer.android.com/develop/connectivity/usb/host
- Android Storage Access Framework: https://developer.android.com/guide/topics/providers/document-provider
- `usb-serial-for-android`: https://github.com/mik3y/usb-serial-for-android
- FTDI Latency Timer: https://ftdichip.com/Support/Knowledgebase/settingacustomdefaultlaten.htm
- 本规格**不要求**实现、兼容、移植或复制 MThings 行为、二进制、工程格式、GUI 资产或缺陷。
- Android `connectedDevice` 前台服务类型：https://developer.android.com/develop/background-work/services/fgs/service-types
- Android 前台服务后台启动限制：https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
