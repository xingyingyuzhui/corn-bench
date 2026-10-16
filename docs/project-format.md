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

### 组件类型

`number`（数值）、`lamp`（状态灯）、`status_text`（状态文字）、`progress`（进度条）。其它类型按“读不懂”处理。

## 换算规则（`ValueCodec`）

- 读：寄存器字 →（按 `order` 整理成大端）→ 按类型解释 → ×scale+offset。64 位整数全程用 `Long` / `BigInteger`，不经 `Double`，不丢精度；F32 的 NaN / ∞ 显示“—”。
- 写：工程值 →（−offset）/scale → 整数类型四舍五入 → **范围检查**（超界拒绝，不回绕，如把 −5 写进 U16）→ 按 `order` 还原。64 位整数经 `Double` 反算只在 ±2^53 内才精确，超出拒绝。
- 位写入 = 先读整字、改一位、再写回（非原子，写入时需提示）。
- BCD 里出现 A–F 半字节视为无效值。

## 轮询（`PollScheduler`，P3）

- 只在“主站会话在跑 + 面板页在前台”时发读请求；离开页面 / 退到后台立刻停，值保留；换连接（epoch 变化）或断开会清空所有点位，回到“无数据”。
- 一次一个请求，按每组到期时间轮流发。与工作台等手动请求共用 `MasterEngine` 的公平互斥锁：手动请求最多等当前这一次读，没有“严格优先 / 取消排队中的轮询”（需要改 core）。
- 读得慢不追赶：下一次从“现在”起算。设备返回异常码 / 响应无效的组降频到至少每 2 秒一次，避免对不存在的地址狂发请求。
- 质量：`NO_DATA`（没读到过）/ `OK` / `STALE`（本次失败，保留上一次的值）/ `TIMEOUT`（连续失败 ≥3 次且最近是超时）/ `ERROR`（异常码、响应无效、值无效如 BCD 非法或 F32 为 NaN）。
- 合并读取默认只合并相连或重叠的范围（不多读没人用的地址）；合并后的某一组失败会让组内所有点位一起失败（不拆分重试）。
