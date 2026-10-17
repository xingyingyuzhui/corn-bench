# 面板工程文件格式（版本 1）

位置：`<应用私有目录>/files/projects/<工程 id>.json`（目前只有 `default`）。不在公开存储，不随日志导出。
序列化：`org.json`（无新依赖）。代码：`application/panel/`（`ProjectJson` / `ProjectRepository`），纯 Kotlin，`core` 与协议未改动。

## 写入与恢复

1. 新内容写到 `default.json.tmp`，`fsync`；
2. 旧的 `default.json` 复制为 `default.json.bak`；
3. 原子重命名 `tmp → default.json`。

读取：先读主文件；解析失败回退 `.bak`（界面提示“已用备份”）；都失败则把坏文件另存 `default.json.corrupt-<毫秒时间>`，**原文件不删不改**，界面提示后由用户决定。程序从不自动删除工程数据。

## 版本

- 顶层必须有整数 `version`（缺失视为损坏）。
- `version` 大于本版本支持的（现在是 1）→ **只读**：能显示，但任何保存都被拒绝，不覆盖更新版本写的文件。
- 单个组件读不懂（类型是更新版本才有、字段不合法、点位校验不过）→ 该组件以 `raw` 原样保留，界面不显示，保存时**逐字写回**；同时给出警告。不会丢、不会崩。
- 数值型字段超界会被夹到合法范围（面板列数 2..4，组件跨列 / 跨行 1..2）。

## 结构

```json
{
  "version": 1,
  "id": "default",
  "name": "我的工程",
  "panels": [
    {
      "id": "pn1", "name": "主面板", "columns": 2,
      "widgets": [
        {
          "id": "w1", "kind": "number", "title": "进水温度", "spanColumns": 1, "spanRows": 1,
          "point": {
            "id": "p1", "name": "温度", "unitId": 1,
            "area": "INPUT_REGISTER", "address": 100,
            "type": "S16", "order": "ABCD", "bit": 0, "asciiRegisters": 1,
            "scale": 0.1, "offset": -20.0, "decimals": 1, "unitLabel": "°C",
            "rangeMin": -20.0, "rangeMax": 120.0,
            "thresholds": [ {"level": "WARN", "min": 60.0, "max": 80.0}, {"level": "ALERT", "min": 80.0} ]
          }
        }
      ]
    }
  ]
}
```

### 点位字段

| 字段 | 取值 |
|---|---|
| `unitId` | 1..247（0 是广播，读不到回应） |
| `area` | `COIL` / `DISCRETE_INPUT` / `INPUT_REGISTER` / `HOLDING_REGISTER` |
| `address` | 0..65535（协议地址，不是 4xxxx 参考号）；`address + 占用个数 ≤ 65536` |
| `type` | `U16 S16 U32 S32 F32 U64 S64 F64 BCD16 BCD32 ASCII BIT BOOL`；`BOOL` 只用于线圈 / 离散输入，其余只用于寄存器 |
| `order` | `ABCD`（标准大端）/ `CDAB`（字序交换）/ `BADC`（字内字节交换）/ `DCBA`（全小端）；16 位类型只有字节交换起作用 |
| `bit` | `BIT` 类型：0..15，0 = 最低位 |
| `asciiRegisters` | `ASCII` 类型占几个寄存器，1..125 |
| `scale` `offset` | 工程值 = 原始值 × scale + offset；`scale` 不能为 0 |
| `pollMs` | 轮询周期（毫秒），100..600000，缺省 1000（老文件没有这个字段）；同一从站、同一区域、同一周期且地址相连的点位会合并成一次读 |
| `decimals` | 只影响显示，0..10，缺省 = 最短表示 |
| `rangeMin` `rangeMax` | 显示范围（进度条 / 曲线纵轴用），可缺省；有则 min < max |
| `thresholds` | 区间 `[min, max)`，`level` ∈ `NORMAL WARN ALERT`；多个命中取最严重 |

### 组件字段（除点位外）

| 字段 | 取值 |
|---|---|
| `title` | 组件名称；空 = 用点位名称 |
| `spanColumns` / `spanRows` | 1..2；跨列按“k×列宽 + (k−1)×间距”；P4 里跨行只表现为这一行更高 |
| `onLabel` / `offLabel` | 指示灯 / 为 1 / 0 时的文字，缺省“开”/“关” |
| `buttonLabel` | 按钮卡上的文字，可缺省（缺省 = “写入”），最长 12 字 |
| `tableRows` | 寄存器表（`register_table`）的行数，1..16 |
| `tableWritable` | 寄存器表是否允许面板修改，缺省 false；只有 `area` 为保持寄存器 / 线圈时才能为 true |
| `writeValue` | 按钮的预设值（数字）：线圈只能是 0 / 1；保持寄存器按点位 `type`（U16 S16 U32 S32 F32）和 `order` 编码，整数类型要整数且在类型范围内；不做 scale / offset 换算 |
| `states` | 状态文本：`[{"value": 2, "label": "制冷", "tone": "BLUE"}]`；`tone` ∈ `GREEN AMBER RED BLUE GRAY`，缺省 BLUE；没有 `value` 或 `label` 的条目忽略并给警告；取值没有映射时显示“未定义（值）” |

进度条和仪表（`gauge`，半圆表盘，只读）用点位的 `rangeMin` / `rangeMax`（缺省 0..100，必须 min < max）；仪表还用点位的 `thresholds`（预警 / 告警区间，画在表盘外圈）和 `unitLabel`。

### 组件类型

`number`（数值）、`lamp`（状态灯）、`status_text`（状态文字）、`progress`（进度条）、`curve`（曲线，P7）、`gauge`（仪表，P8a）、`switch`（开关，P8b1）、`button`（按钮，P8b1）、`register_table`（寄存器表，P8b2）。其它类型按“读不懂”处理。

### 可写组件（`switch` / `button`，P8b1）

这两类会写设备，**面板上点它们只会请求写入，真正发请求前总要经过确认弹窗**（见 `docs/注意事项.md` 52–55）。

- `switch`：点位只能是线圈（`area = COIL, type = BOOL`），或保持寄存器的某一位（`area = HOLDING_REGISTER, type = BIT, bit = 0..15`）；`unitId` 必须 1..247（要读状态）；可带 `onLabel` / `offLabel`。会被轮询（读状态）。
- `button`：点位是线圈或保持寄存器（`type` ∈ `U16 S16 U32 S32 F32`），**`unitId` 可以是 0..247，0 = 广播**（只有按钮可以）；必须有 `writeValue`，可带 `buttonLabel`。**不进轮询**（它的点位只用来写）。
- 不满足上面规则的开关 / 按钮，整个组件按“读不懂”原样保留（和未知类型一样：不渲染、保存时原样写回、给警告）。
- **版本兼容**：这是纯增量改动，`version` 仍是 1。P8b1 之前的版本读到 `switch` / `button` 会当“不认识的类型”原样保留并提示，再保存也不丢；新版本读旧文件没有任何变化。以后如果改了已有字段的含义才需要抬版本号。

### 寄存器表（`kind = "register_table"`，P8b2）

- 点位 `point` 是**第 1 行**，`tableRows`（1..16）是行数；第 i 行是派生点位（运行时 id `<点位 id>#i`，**不写进文件**），地址 = 起始地址 + i × 每行占用寄存器数，其余字段（Unit / 区域 / 类型 / 字序 / 缩放 / 偏移 / 轮询周期）同第 1 行。
- `area` 为寄存器区时 `type` ∈ `U16 S16 U32 S32 F32`；为线圈 / 离散输入时 `type = BOOL`；`unitId` 1..247。不带 `thresholds` / `rangeMin` / `rangeMax`（带了也不使用）。
- `tableWritable`（缺省 false）：只有保持寄存器 / 线圈可以为 true；输入寄存器 / 离散输入为 true 视为不合规。为 true 时每行出现 ✎，写入一律经确认弹窗（见 `docs/注意事项.md` 58–59）。
- `spanColumns` 固定 2，`spanRows` 固定 1（卡片高度随行数增加）。
- 不合规的表（行数不在 1..16、类型不允许、可写但区域只读、跨度溢出）按“读不懂”原样保留，不渲染。
- **版本兼容**：纯增量，`version` 仍是 1；P8b1 及更早版本读到 `register_table` 会当“不认识的类型”原样保留并提示，再保存也不丢。

### 曲线组件（`kind = "curve"`，P7）

曲线组件没有顶层 `point`，点位在 `curve.series[].point` 里（点位 id 全工程唯一，和普通点位一样参与轮询）。`spanColumns` 固定 2，`spanRows` 1..2。**文件里只有配置，没有样本**：样本历史只放内存，退出 App 即丢。

```json
{ "id": "w2", "kind": "curve", "title": "", "spanColumns": 2, "spanRows": 1,
  "curve": {
    "windowSec": 60, "sampling": "EVERY_SAMPLE", "retentionMinutes": 60, "maxPoints": 20000,
    "left":  {"auto": true}, "right": {"auto": false, "min": 0.0, "max": 1.0},
    "grid": true, "legend": "TOP", "gaps": "HATCH", "cursor": true,
    "series": [
      {"point": { ...同点位字段... }, "color": 0, "lineStyle": "SOLID", "width": 2, "axis": "LEFT",
       "showPoints": false, "fill": false, "step": false}
    ] } }
```

| 字段 | 取值 |
|---|---|
| `windowSec` | 时间窗口 10..86400 秒（向导预设 1 / 5 / 10 分、1 时，另可自定义分钟数） |
| `sampling` | `EVERY_SAMPLE` 每次读到都记 / `SNAPSHOT` 定时快照（间隔 = max(1 s, 所有系列轮询周期的最大值)，所有系列在同一时刻取值） |
| `retentionMinutes` / `maxPoints` | 内存保留 1..1440 分钟 / 每系列最多 100..100000 点；`retentionMinutes` 不能短于窗口 |
| `left` / `right` | `{auto, min, max}`；手动时要 min < max；没有系列用右轴时右轴设置不起作用 |
| `legend` | `TOP` `BOTTOM` `HIDDEN`；`gaps`：`HATCH`（断开 + 斜纹）/ `BREAK`（仅断开）；`grid` / `cursor`：布尔 |
| `series[]` | 1..8 个；`color` 0..7（8 色色板）、`lineStyle` `SOLID` `DASH` `DOT`、`width` 1..3、`axis` `LEFT` `RIGHT`、`showPoints` / `fill` / `step` 布尔；图例名称就是 `point.name` |

曲线配置不合法（未知枚举、窗口 / 保留 / 点数越界、没有系列、系列超过 8 个…）时，整个组件按“读不懂”原样保留并给警告，不丢也不崩。

## 换算规则（`ValueCodec`）

- 读：寄存器字 →（按 `order` 整理成大端）→ 按类型解释 → ×scale+offset。64 位整数全程用 `Long` / `BigInteger`，不经 `Double`，不丢精度；F32 的 NaN / ∞ 显示“—”。
- 写：工程值 →（−offset）/scale → 整数类型四舍五入 → **范围检查**（超界拒绝，不回绕，如把 −5 写进 U16）→ 按 `order` 还原。64 位整数经 `Double` 反算只在 ±2^53 内才精确，超出拒绝。
- 位写入 = 先读整字、改一位、再写回（非原子，写入时需提示）。开关的寄存器位写入就是这样做的：弹确认前读一次整字，点“发送写入”后再读一次核对，整字变了就不发（见 D46）。
- BCD 里出现 A–F 半字节视为无效值。

## 轮询（`PollScheduler`，P3）

- 只在“主站会话在跑 + 面板页在前台”时发读请求；离开页面 / 退到后台立刻停，值保留；换连接（epoch 变化）或断开会清空所有点位，回到“无数据”。
- 一次一个请求，按每组到期时间轮流发。与工作台等手动请求共用 `MasterEngine` 的公平互斥锁：手动请求最多等当前这一次读，没有“严格优先 / 取消排队中的轮询”（需要改 core）。
- 读得慢不追赶：下一次从“现在”起算。设备返回异常码 / 响应无效的组降频到至少每 2 秒一次，避免对不存在的地址狂发请求。
- 质量：`NO_DATA`（没读到过）/ `OK` / `STALE`（本次失败，保留上一次的值）/ `TIMEOUT`（连续失败 ≥3 次且最近是超时）/ `ERROR`（异常码、响应无效、值无效如 BCD 非法或 F32 为 NaN）。
- 合并读取默认只合并相连或重叠的范围（不多读没人用的地址）；合并后的某一组失败会让组内所有点位一起失败（不拆分重试）。
