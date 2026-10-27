# 本地 ADB 恢复记录

日期：2026-10-19。只处理既定 Pixel_9_Pro / emulator-5582，保持可见窗口。

## 证据与边界

- 旧环境 Android 能进入桌面，Mac TCP 可连接5583并发送CNXN，但未收到握手回应；不足以判断密钥失败或项目APK故障。
- 冷启动、重启ADB、软件渲染、关闭VirtioVsockPipe均未恢复旧环境。
- 官方SDK更新前已备份：Emulator 36.3.10→37.2.12、Platform Tools 36.0.2→37.0.1。更新本身未恢复旧数据环境，因此不能把工具版本当成已证实根因。
- 同一个AVD/端口，使用独立的新userdata和cache后恢复device；旧数据环境异常是当前证据指向的范围，**具体损坏文件/设置尚未定位**，不宣称已证明USB调试被关闭或存储满是唯一原因。
- 原AVD完整备份后，将可用镜像复制到默认数据路径；QCOW2链为同目录相对backing文件，镜像检查无错误。没有删除旧备份。
- 最新APK安装/启动、MCP截图及UI树成功。Android内reboot和完整停止再冷启动后，APK与测试文件均保留。最初安装后立即停止曾丢失未同步状态，后续先sync再重启并实际核对持久化。
- 当前MCP报告manual、ready=true、automatic_shutdown=false；MCP仍运行时连接正常，不能将“MCP进程存在”视作本次离线原因。其默认无头启动/自动管理策略与用户可见窗口要求冲突，后续只借用手工启动实例。

## 已保留的本机备份

- 原AVD：`~/AndroidEmulator-Local/avd-backups/Pixel_9_Pro-before-adb-repair-20261019/`。
- 原SDK：`~/AndroidEmulator-Local/sdk-backups/20261019/{emulator,platform-tools}/`。
- 独立验证镜像：`~/AndroidEmulator-Local/avd-data/Pixel_9_Pro-clean-20261019/`。

恢复旧AVD应先停止模拟器，再按备份恢复data/cache及对应encryptionkey镜像；保留备份，不在运行中替换镜像。旧环境可能再次offline，不将回滚作为常规启动方式。

## 后续启动

使用本机SDK（不要给MCP下无头启动命令）：

```sh
~/Library/Android/sdk/emulator/emulator -avd Pixel_9_Pro -port 5582 \
  -no-audio -no-boot-anim -no-snapshot -gpu swiftshader
~/Library/Android/sdk/platform-tools/adb -s emulator-5582 wait-for-device
~/Library/Android/sdk/platform-tools/adb -s emulator-5582 shell getprop sys.boot_completed
```

确认boot_completed=1，且`emu avd windowtype`为qtwindow后，再让MCP借用。停止前执行`adb -s emulator-5582 shell sync`，新安装或写入后的持久化用实际重启验证。无设备时不要通过mobile操作隐式启动无头实例。

此次是环境修复，无项目源码变更；不等同于完整设备读写、SAF/分享或真实USB/485验收。
