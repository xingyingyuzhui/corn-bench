# 本地虚拟串口 RTU 测试台

## 范围

这套测试台用于本机进程间 Modbus RTU 联调，不依赖 USB-RS485 实物。独立 PyModbus 主/从站、PTY 串口对和 Android 调试包组成完整字节流链路：

```text
PyModbus / 外部串口工具
        ↕ 串口 A（PTY）
本地中继：字节日志、分块、延迟、回显、故障注入
        ↕ 串口 B（PTY）
主机本地 TCP ↔ adb forward ↔ Android abstract LocalSocket
        ↕ RawSerialPort 适配
SerialIoWorker → UsbSerialTransport → 实际主站/从站引擎 → 应用界面
```

TCP 只在电脑本地承载调试字节流，不增加 Modbus TCP 协议。Android 侧为 Unix-domain socket，不增加 INTERNET 权限。调试入口只在 debug source set；release 工厂直接返回真实 USB 实现。

## 能验证什么

- 独立协议实现互操作、八种功能码、异常响应、广播无应答、多 Unit。
- 真实线程/进程间 I/O、请求超时、分包、人工延迟和回显、断开与重新连接。
- 日志与界面显示的是外部进程交互结果，不是应用内 Demo 的双引擎自测。

## 仍需硬件验证

- Android USB 枚举/授权、USB 端点与芯片驱动、真实 connectedDevice 前台服务条件。
- RS485 电平、收发方向控制、终端电阻、偏置、接线与干扰。
- 实际波特率下的线上 t1.5/t3.5、USB 打包/驱动延迟；PTY 和人工延迟不能替代它们。

## 实现依据

- [Android LocalServerSocket](https://developer.android.com/reference/android/net/LocalServerSocket)
- [Android LocalSocket](https://developer.android.com/reference/android/net/LocalSocket)
- [PyModbus 官方串口服务端示例](https://github.com/pymodbus-dev/pymodbus/blob/dev/examples/server_async.py)
- [QEMU USB 设备仿真](https://qemu-project.gitlab.io/qemu/system/devices/usb.html)：标准 QEMU 提供 FTDI 设备仿真，但当前 Android Emulator 的 monitor 入口返回不可用，未将其当作已接通的 USB 路径。

## 本阶段验证记录

2026-10-18 01:22（UTC 2026-10-17T17:21:56Z）在可见窗口的 Pixel_9_Pro 上完成双向联调。模拟器序列号 `emulator-5582`，窗口标题 `Android Emulator - Pixel_9_Pro:5582`。使用当时已安装的 debug 包，从站配置为 `docs/fixtures/rtu-lab-slave.json`（version 1，3159 字节，`rules` 为空）。应用侧串口按按钮文案 `9600 8N1`，对端 `--baudrate 9600 --timeout 2`。中继默认帧间隔 4 ms。PTY 上的波特率不形成真实 UART 时序，Android 仍按该波特率计算 t1.5/t3.5。

链路：`adb -s emulator-5582 forward tcp:15582 localabstract:modbus-mobile-rtu-lab`，主机中继状态目录 `/tmp/modbus-rtu-lab`。`bridge_connected` 只表示主机 TCP 已接通；下面以实际 RTU 往返为准。同一次操作开始前，原先的模拟器进程消失过；随后用同一 AVD、端口 5582、`-scale 0.4`、`-no-snapshot-load` 重新打开窗口，未做 AVD wipe。通过记录来自这次重启后的实例。更早的 28 项失败是 debug LocalSocket 的 `SO_TIMEOUT` 把空闲读当成断开，该项已改为 `Os.poll`，那次失败不计入本次通过。

手机作从站。PyModbus `peer.py probe --broadcast` 28 项全部通过，单次响应最长 21.0 ms。Unit 1 和 Unit 2 各自覆盖 FC01、FC02、FC03、FC04、FC05 写后读回、FC06 写后读回、FC15 写后读回、FC16 写后读回，以及非法地址 65000 的异常码 2。广播写 4321 后，两个 Unit 都能读回 4321。抽查：Unit 1 的 FC03 读回保持寄存器 `[1000, 1001, 1002]`，FC06 读回 `1234`，FC15 读回 `[false, true, true, false]`，FC16 读回 `[111, 222, 333]`；Unit 2 的 FC04 读回输入寄存器 `[4000, 4001, 4002]`。

手机作主站。从站会话断开后，PyModbus `peer.py server` 在串口 A 提供 Unit 1 和 Unit 2、地址 0..99，保持寄存器值为 Unit×1000+地址。应用连接「本地虚拟串口」，工作台发出 FC03（Unit 1、地址 0、数量 10）。请求 `01 03 00 00 00 0A C5 CD` 到达对端，应答 `01 03 14 03 E8 … 03 F1 C7 64`，寄存器为 1000..1009。接着工作台按默认写值 0 发出 FC06，请求与应答都是 `01 06 00 00 00 00 89 CA`。中继对每个方向各记一条，所以同一帧在 `traffic.jsonl` 里出现两次。这次读到的 1000 来自对端服务器公式，与从站夹具的初值相同，是另一次会话。

主机中继单测 10 项通过。原始探测报告和主站流量留在本机 `/tmp/rtu-slave-probe.json`、`/tmp/rtu-master-traffic.json`，未入库。

USB 枚举、芯片驱动、RS485 电平、收发方向、终端与偏置、真实波特率线上时序、多站电气总线仍未验证。这次联调不关闭真机验收项。
