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

实现与本机互操作结果待补齐，不据此关闭真机验收项。
