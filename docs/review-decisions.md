# V4 → V5 → V5.1 审阅问题与修订决策清单

日期：2026-10-16。核对基准：用户上传的《Modbus Mobile — 架构 V4.0》（2118 个实际文本行；部分编辑器可能显示为 2119 行）。此表给审核人员快速定位，具体行为以 **V5.1 `spec.md` 为唯一规范性依据**。

| ID | V4 位置 | 审阅问题 | V5 决策（V5.1 沿用） | 归属 |
|---|---|---|---|---|
| R01 | §4.1/4.3、§11.2 | 从站不能只按请求帧长度处理，其他站响应会错帧 | `RtuBusFrameScanner` 同时处理请求、回复、异常、TX 回显和模糊方向；有界 CRC 恢复，绝不单凭 CRC 执行写入 | spec §3.2、§8 F01–F06 |
| R02 | §0–14 vs §15–21 | 混合规格与商业产品研究 | `spec.md` 只含独立开发规格；参考结论单列研究笔记；扩展独立路线 | 全部文件 |
| R03 | §16–21 证据 | `/Users/qin/Desktop/...` 不可达 | 研究笔记不使用这些路径作为可点证据，全部标记“原始证据未提供/待核查” | research |
| R04 | §20.7 | E1–E6 可能挤占首版 | M0–M6 是唯一首版，E 项先 RFC 再立项 | spec §0、roadmap |
| R05 | §15.1/16.1/17/18/19 | 研究标签重复 | 独立研究笔记只保留一份标签定义 | research |
| R06 | V4 逆向兼容内容 | 版权/许可及缺陷复制风险 | 取消兼容模式、二进制行为逐项复刻和第三方工程导入；只保留公开规范可支持的通用结构设计 | spec §0/§10，research |
| R07 | V4 无 FTDI 延迟配置 | FTDI 接收延迟可能导致从站超时 | 可选芯片能力检测与 get/set latency，真机量化延迟 | spec §2.2、F12 |
| R08 | V4 无回显分支 | 自发帧可能回显成新请求 | TX 回显上下文与可选抑制、重复合法 FC05/06 不可误丢 | spec §2.3、F05–F06 |
| R09 | §6/§7 | I/O 阻塞取消实现未定 | 首版选专用 `read/write` I/O 工作者 + 有限超时 + close 取消 + join | spec §2.1、F11 |
| R10 | §0/4.2/17.2.1 | 0x17 支持范围冲突 | 首版不执行，已识别完整请求回功能异常；功能码统一十六进制 | spec §0.3、F09 |
| R11 | §4.3 | 只考虑从站广播 | 主站广播写无回复、turnaround wait 可配置 | spec §0.3、§3.1、F08 |
| R12 | §7/§13 | 只有 `:app` | 两模块 `:app` 与纯 JVM `:modbus-core` | spec §1 |
| R13 | §19.8 | Lua VM 未选 | E5 单独评估引擎/预算，不进入首版 | roadmap E5 |
| R14 | §17.3 | S7 库未选 | E3 独立选库与许可审查 | roadmap E3 |
| R15 | §17.7 | DL/T645 标准缺口 | E2 前置阻塞项 | roadmap E2 |
| R16 | §17.3.1 | S7 PDU 排版错误 | 从首版规格移除；如独立 RFC 引用原数据，需按设备型号重新核验 `SMART 240 / 300 480 / 400/1200 960 / 1500 1960`，不据此直接承诺 | research/roadmap |
| R17 | §20.2 | Android 17 网络权限 | 官方页面已核对，但仅保留在网络扩展立项中 | roadmap E3 |
| R18 | §18.4 | 单点单行高频入库无保留上限 | 默认节流历史、环形缓冲、保留天数和容量、磁盘满明确状态 | spec §6.2、F13 |

## V5.1 审阅新增决策（基于第二轮 Agent Review）

| ID | 问题 | V5.1 最终决策 | 位置与验收 |
|---|---|---|---|
| Q01 | 外站无回复时 `ObservedTransaction` 可能阻塞本机新请求 | 在**可信帧起点**识别与外站响应互斥的合法本机完整请求，允许 `PREEMPTED_BY_NEW_REQUEST` 抢占；不得从外站响应 payload 提取内部 CRC 子串。观察事务与组帧计时分离 | spec §3.2.2a；F16/F17/F25 |
| Q02 | 半包长期悬挂且无新 ByteChunk 时不能过期 | 注入单调时钟 + 独立 `onClockTick`；组帧预算考虑最大 ADU 线上时间、USB 延迟和调度余量；不硬编码 `max(100ms, 3×FTDI)` | spec §3.2.2a；F18/F19 |
| Q03 | Review 声称 0x17 请求长度 `11+WriteByteCount` | **纠正为 `13 + WriteByteCount`（完整 RTU ADU）**；给出 byte offset 和 byte count/quantity 校验；未知未建模 FC 静默丢弃 | spec §0.3/§3.2.1/§3.2.2a；F09/F10/F20 |
| Q04 | 多虚拟从站广播一个站失败影响其他站 | 冻结广播目标 Unit 集合；每个 Unit 内原子，Unit 之间独立；全部无回复 | spec §3.2.3/§4.1；F21 |
| Q05 | Android 14+ 前台服务时序不明确 | USB 授权与 `connectedDevice` 运行前提先成立，用户前台发起服务并及时 `startForeground`；**端口打开成功不是 Android API 的服务启动强制前提**，由服务独占打开与管理 | spec §7.1；F24 |
| Q06 | 只设计整帧回显，未考虑多 ByteChunk | 跨批次前缀匹配 + TX 上下文 + 有界过期；合法重复 FC05/06 不可永久过滤；不可消歧时安全丢弃 | spec §2.4；F23 |
| Q07 | 运行中 UI 修改映射/Unit 可能破坏请求快照 | 值修改与结构修改分开，单请求绑定 revision/epoch，结构性修改事务边界提交 | spec §4.2；F22 |
| Q08 | Agent 提议先 M1 全部完成才开始 M0 | 改为先建双模块骨架，M1 与 M0 尽早并行；首个联合验收节点前不大量投入 UI | spec §8 |

**术语纠正**：在 `responseDeadline` 前观察到本机新请求，并不说明外站真的已经超时；状态命名为 `PREEMPTED_BY_NEW_REQUEST`，避免日志误报。`frameAssemblyDeadline`、`responseDeadline`、`echoDeadline` 是不同的期限。

## 特别风险声明

“根据请求长度/响应长度双算法扫描 + CRC”是必要的候选识别手段，**不是完整可靠的帧边界方案**。0x05/06 请求与应答字节同形；Android 不能直接取得符合 RTU 要求的物理空闲边界，故必须使用方向上下文、有限恢复和保守丢弃，并用真实多站总线证明设备组合的兼容范围。

## 公开资料核对

- Modbus 串行线 V1.02：帧边界 t3.5、广播无回复及 turnaround delay（通常示例 100–200 ms）：https://www.modbus.org/file/secure/modbusoverserial.pdf
- FTDI：接收延迟定时器默认 16 ms（特定器件/驱动情形）：https://ftdichip.com/Support/Knowledgebase/settingacustomdefaultlaten.htm
- `usb-serial-for-android`：FTDI 接口及 I/O 行为：https://github.com/mik3y/usb-serial-for-android
- Android 本地网络权限（官方页面 2026-10-02 更新）：https://developer.android.com/privacy-and-security/local-network-permission
