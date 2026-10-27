# 本地 RTU 串口测试台

在 macOS/Linux 创建两个真实 PTY 终端，原始字节双向中继；任何支持串口的工具都可打开 A/B。独立协议对端采用 **PyModbus 3.16.1 + pyserial 3.5**，不会复用 Android 的协议实现。可用于协议、超时、回显、CRC 和多 Unit 验证；不模拟 RS-485 电气特性、USB 芯片驱动、DE/RE、真实波特率、总线冲突或插拔。

```
PyModbus / 第三方串口工具
            ↕ PTY A
     透明字节中继（可注入故障）
            ↕ PTY B
       host TCP 字节桥
            ↕ adb forward（仅指定模拟器）
 debug LocalServerSocket → 应用现有 RTU 会话
```

## 安装和启动

```sh
python3 -m venv /tmp/modbus-rtu-lab-venv
/tmp/modbus-rtu-lab-venv/bin/pip install -r tools/rtu_lab/requirements.txt
/tmp/modbus-rtu-lab-venv/bin/python tools/rtu_lab/lab.py start --state-dir /tmp/modbus-rtu-lab
/tmp/modbus-rtu-lab-venv/bin/python tools/rtu_lab/lab.py status
/tmp/modbus-rtu-lab-venv/bin/python tools/rtu_lab/lab.py stop
```

`start` 后台运行，`serve` 前台运行并接受 Ctrl-C。相同 state-dir 有文件锁保护，只能运行一个 relay；stop 验证 PID、完整脚本路径及随机令牌，只停止本工具启动的后台进程。前台进程使用 Ctrl-C 停止。不同实例使用不同 state-dir。启动 JSON 和 `state.json` 暴露 `port_a`、`port_b`、连接状态和收发计数。状态每 250 ms 更新。日志 `traffic.jsonl` 包含 UTC、单调时间、方向和十六进制字节；日志、状态、报告权限均为 600。运行产物默认在 `/tmp`，不进入仓库。目录中的 `daemon.log` 是后台诊断日志。

单独验证两个端口时无需 ADB，不启用 `--bridge-port`；不要让两个程序同时占用同一 PTY 端口。关闭全部端口后停止 relay，PTY 随之销毁；重启后路径可能改变，需要重新读取 state.json。

## 连接本地 Android 模拟器

仅操作已选定的本地 AVD `emulator-5582`。应用须为 debug 构建，并在连接页选择本地虚拟串口后连接。该入口创建抽象命名空间 LocalServerSocket `modbus-mobile-rtu-lab`，桥接原始 RTU 字节，无 Modbus TCP 的 MBAP 头，也没有额外握手。

```sh
adb -s emulator-5582 forward tcp:15582 localabstract:modbus-mobile-rtu-lab
/tmp/modbus-rtu-lab-venv/bin/python tools/rtu_lab/lab.py start --bridge-port 15582
```

TCP 桥自动打开 PTY B；外部对端打开 A。若应用尚未监听，桥会间隔 500 ms 重连。先打开应用本地串口再运行通信；`state.json` 中 `bridge_connected` 仅表示 host TCP 已连接。ADB forward 即使后端尚未监听也可能短暂接受 TCP，因此它不能证明应用准备就绪；必须以双向字节计数和成功 RTU 请求/回应确认完整链路。结束后执行 `lab.py stop`，以及 `adb -s emulator-5582 forward --remove tcp:15582`。本工具不自动修改 ADB、安装应用或访问其他设备。

## Android 作主站：独立从站

```sh
/tmp/modbus-rtu-lab-venv/bin/python tools/rtu_lab/peer.py server
```

提供 Unit **1 和 2**，四区地址均为 **0..99（零基）**：

| 区域 | 初始值 |
|---|---|
| Coil FC01/05/15 | 偶数地址 false、奇数地址 true |
| Discrete input FC02 | 偶数地址 true、奇数地址 false |
| Holding register FC03/06/16 | Unit × 1000 + 地址 |
| Input register FC04 | Unit × 2000 + 地址 |

重启 server 恢复初值。支持广播写（Unit 0，不回应），未知 Unit 忽略。server 不主动发送请求。使用 `--port /dev/ttys...` 可直接指定串口；`--side B` 可在不启用 TCP 桥时让 server 占用 B。PTY 上的 baudrate 仅是串口配置，不模拟真实传输速率。

## Android 作从站：读取或完整主动探测

先在应用从站配置 Unit 1/2，并让四区至少覆盖地址 0..3；异常检查默认地址 65000 必须不在配置中。**probe 会改写指定地址及后续三项，并可广播修改两个 Unit；仅在明确运行 probe 时执行写入。** 请使用模拟器测试配置。规则引擎应暂停，避免采样期间改变读回值。

```sh
# 只读，无写入
/tmp/modbus-rtu-lab-venv/bin/python tools/rtu_lab/peer.py read --units 1,2

# 八功能码、每次写入的读回、越界异常码 02、多 Unit 和广播
/tmp/modbus-rtu-lab-venv/bin/python tools/rtu_lab/peer.py probe --units 1,2 --broadcast --report /tmp/rtu-probe.json
```

`--address` 改变探测起点，`--exception-address` 改变越界检查地址。读四区只检查有效回应（未知 Android 初值）；写功能另做精确读回。异常必须为 02；广播通过两个 Unit 的读回来确认实际执行。报告给出各检查通过与否、值、耗时、错误；有失败时进程退出码为 1。Unit 0 的广播由 PyModbus 编码，使用 `no_response_expected=True`，不等待不存在的响应。广播后的寄存器值保持 4321，不自动还原。默认只探测 Unit 1/2，如只配了一个 Unit，明确传 `--units 1`。

## 故障注入

启动时选用以下参数（修改前先停止已有 relay）：

```sh
/tmp/modbus-rtu-lab-venv/bin/python tools/rtu_lab/lab.py serve \
  --bridge-port 15582 --chunk-size 2 --chunk-gap-ms 1 --delay-ms 20
```

- `--chunk-size N` / `--chunk-gap-ms M`：以 N 字节拆分、间隔 M ms 转发。
- `--delay-ms M`：每帧转发前延迟。
- `--echo`：将原始发送数据回显给发送侧，同时正常转发。
- `--drop-every N`：每个方向独立累计，每第 N 帧丢弃。
- `--corrupt-every N`：每第 N 帧翻转最后一个字节的低位，用于 CRC 错误。
- `--frame-gap-ms M`：以 M ms 接收空闲划分注入帧，默认 4 ms。这是主机调度近似；连续无间隔帧可能合并，不等价于硬件的精确 3.5 字符计时。

故障标记及原始/污染字节记录在 traffic.jsonl。全部输出通过每目标 FD 的 FIFO 保持字节顺序，部分写和 EAGAIN 保留队首剩余字节；后一帧从前一帧的末尾分块之后排程。桥接字节计数反映实际写出数量；断开连接时未写出的数据记入 bridge_disconnect_discarded_bytes，不跨连接重放。延迟和分块会作用于两个方向，因此往返时延包含双向注入时间。极端并发或长帧传输应另外评估主机调度影响。

## 自测

```sh
/tmp/modbus-rtu-lab-venv/bin/python -m unittest discover -s tools/rtu_lab/tests -v
```

覆盖 PTY 双向、分块/延迟/回显/CRC 污染、丢帧、TCP↔PTY 桥、部分写/EAGAIN 重试、相邻帧分块顺序、PID 复用保护和短回应拒绝，及独立 PyModbus serial client/server 的两个 Unit、八功能码、写入读回、异常、广播。

官方参考：[PyModbus server API](https://pymodbus.readthedocs.io/en/stable/source/server.html)、[数据模型](https://pymodbus.readthedocs.io/en/stable/source/simulator/datamodel.html)、[官方 serial server example](https://github.com/pymodbus-dev/pymodbus/blob/v3.16.1/examples/server_sync.py)、[ADB forward](https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/docs/user/adb.1.md)。
