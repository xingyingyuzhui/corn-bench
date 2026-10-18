# 项目整合记录

## 2026-10-17

- 用户在聊天中明确授权：将仓库未合并分支全部合并到 main。
- 远端分支检查：仅 slave-units-setup 和 slave-sim-rules 存在未进入 main 的提交，其余分支已包含。
- PR #9 的两项 CI 通过，已合并，合并提交 09c6fe7。
- 为 slave-sim-rules 创建 PR #10。原提交带 skip ci，未触发检查；本次补充整合记录并推送，以触发完整 CI。
- 模拟规则列表和编辑界面仍待接入，对应新增测试尚未补齐；不将本次整合记作功能验收。
- 本地未运行构建：规定的 /opt/android/build.sh 在本机不存在，以 GitHub CI 为验证依据。模拟器和真实硬件未验证。

- 提交 a1a284f 的两项完整 CI 均通过：39045371067（6m1s）、39045375849（4m48s），含单元/界面测试、lint、截图录制、APK 打包与 INTERNET 权限检查。
- PR #10 已合并，合并提交 9c658ac；本地已切回 main 并同步。
- 完成所有现有远端分支的整合，保留原分支和历史；模拟规则界面仍待开发，真机和模拟器未验证。

## 2026-10-18 · 从站模拟规则收尾

- 用户授权继续推进项目，本阶段在 `codex/slave-simulation-ui` 完成规则列表、全页编辑、预览、暂停/恢复与专项测试；未授权合并 main。
- 复用已有 Compose 控件和设计稿；核对 AndroidX 官方状态提升、BackHandler API，草稿由 ViewModel 持有。
- 修复增删 Unit 后保存失败仍返回成功；保存结果在会话互斥区内返回，测试覆盖保存失败与重试。
- 本机缺少规定的 `/opt/android/build.sh`，不绕过本地构建约定；本阶段通过分支 GitHub CI 验证，结果待记。GUI 模拟器及真实硬件未运行。
- 已提交并推送 `e73e7f6`；创建草稿 PR #11：https://github.com/xingyingyuzhui/modbus_android/pull/11 ，不合并。完整 CI：39048660600（push），PR 事件另触发检查。
- 独立静态审阅完成，修复“保存并运行”与自启标志混用、极大有限参数导致预览溢出两项问题；实际测试和布局以本阶段 CI 为准。
- 用户随后明确本机具备 Android 构建与模拟器环境，授权本地构建、安装和截图验证；据此更新注意事项 #22。发现会话继承的 GRADLE_USER_HOME 指向未挂载外置盘，单次命令改用本机 ~/.gradle，已启动串行 Gradle 验证。
- 模拟器 MCP 有旧的启动身份失败记录；确认无运行设备/进程后，使用相同配置 Pixel_9_Pro、端口 5582 从本地 SDK 启动，无窗口、不清数据，后续由 MCP 借用该实例。
- 本地首次全套测试：core 58 项通过，app 800 项中 2 项新 UI 测试失败；定位为测试自动滚动后点击被浮动 dock 遮挡。修正真实滚动并增加遮挡断言，提交 `26e17f3` 已推送。随后完整录制构建 core 58 + app 800 全通过，lint 与 APK 打包成功。
- 本地 Pixel_9_Pro / API 35：新建正弦规则（HR1）、实际改值来源、暂停/恢复、返回放弃确认、删除确认及删除后空列表已验证，当前测试进程 crash buffer 为空。真实键盘与横竖屏均截图，截图仅存本机私有目录。
- 安装遇到存储不足和旧包签名冲突：先用隔离 QA applicationId 安装验证；临时降低安装空间阈值后均立即恢复原设置；随后用户授权清理旧测试包，已卸载旧 Modbus 包。未清其他应用数据。
- 本地截图发现横屏系统手势条贴到保存按钮底缘，补导航栏安全区与专项断言后再验，不将首次截图作为视觉验收通过。
- 横屏安全区修复提交 `1003fb7` 已推送；最终本地完整构建成功（28s）：core 58 项、app 801 项均无失败，含 24dp 系统导航区回归；Roborazzi、lint、APK 打包均通过。Gradle 录制的 PNG 已复制到本机私有证据目录，恢复仓库原有基线，不提交新增基线。
- 正常 applicationId 的最新 APK 已重新安装到本地模拟器，临时 QA 包已卸载；横屏截图确认按钮较原来上移 72px（24dp），系统手势条不再覆盖按钮，当前进程 crash buffer 为空。独立审阅定向复核此修复通过。
- 正常安装包补测非法数字保存：字段错误明确显示、编辑页保留、无崩溃；测试结束后停止测试应用，模拟器未自动关闭。按用户授权清理旧测试包和可回收缓存/编译缓存，保留其他应用数据；空间仍紧张，未执行 AVD wipe。
- 编译缓存清理命令多数包成功，末尾遇到模拟器残留的 com.jetbrains.kmpapp 包记录而退出 1；不记作完整系统清理成功。系统安装空间阈值已复查恢复默认。
- 最新代码 `1003fb7` 的 PR 完整 CI 39049452334 已成功：https://github.com/xingyingyuzhui/modbus_android/actions/runs/39049452334 。含完整测试、Roborazzi、lint、APK 与 INTERNET 权限检查；同提交 push CI 39049448534 也已成功；两项完整 CI 全绿。最后仅有文档记录提交，带 `[skip ci]`，不复跑代码验证。

## 2026-10-18 · 本地虚拟串口 RTU 联调

- 用户要求在本地启动串口对串口服务，模拟 485 联调。新分支 `codex/virtual-rtu-lab` 基于已验证的模拟规则分支；不合并 main。
- 核对本机 Android Emulator 与 QEMU 能力：旧 console monitor 返回 QEMU support no longer available；不把标准 QEMU 的 USB-serial 文档能力当作本机已接通。采用 debug-only ADB Unix-domain socket 串口适配，复用实际 SerialIoWorker 和协议引擎，独立 PyModbus+PTY 为对端。
- Release 不包含调试桥，不增加网络权限；硬件与物理层测试保持未验证，详见 docs/virtual-rtu-lab.md。
- 首次本地 Android 构建：core 58 + app 804 项测试、lint、debug/release 打包通过，APK 检查确认 release 不含桥接 socket 名称、两变体均无 INTERNET 权限。
- 真实模拟器联调发现 debug LocalSocket 的 SO_TIMEOUT 超时抛普通 IOException（Try again），首次空闲 1 秒误断开，导致首次独立探测 28 项失败；不计作协议引擎失败或联调通过。已改为 Os.poll 有限等待读，并新增空闲/数据/EOF 专项测试，准备复验。
- 独立审阅发现并修复主机中继部分写丢字节、分块跨帧乱序、陈旧PID状态误报、短成功读取误判四项；主机工具测试增至10项。
- 复验通过（2026-10-18 01:22，可见窗口 Pixel_9_Pro:5582）。开始前模拟器进程消失过，已用同一 AVD、`-scale 0.4`、`-no-snapshot-load` 重新打开窗口，未做 AVD wipe；通过记录来自重启后的实例。手机作从站：`peer.py probe --baudrate 9600 --timeout 2 --broadcast` 28/28 通过，含两 Unit 的 FC01–04、FC05/06/15/16 写后读回、非法地址异常码 2、广播 4321 读回。手机作主站：对 PyModbus server 发出 FC03 数量 10，收到 1000..1009；默认 FC06 将 HR0 写为 0，应答与请求同帧。主机中继单测 10 项通过。原始报告留在本机 `/tmp`，未入库。真机 USB-RS485 与多站电气仍未验证。详见 `docs/virtual-rtu-lab.md`。

## 2026-10-18 · 迭代状态核对

- 用户要求先检查新一轮迭代，随后授权更新状态文档。本次更新 `docs/status.md`，本地分支为 `mine-export@6cc873f`，main 为 `05ea1e2`。
- 核对源码、远端分支、PR #11–19 与 Actions：历史库 #12、导出 #13、轮询 #19 两项 CI 均成功；日志修复 #14 结果混合；主题/控件/反馈/keyline #15–18 检查失败，最新 #16 为 803 项中 20 项失败。均尚未合并 main。
- 区分当前本地已有的历史库/导出与远端尚未整合的 QA/UI 改动；记录导入未实现、历史容量清理与全量导出内存风险（源码发现、未实测）。
- 本次仅更新文档，未重新运行构建、模拟器或分享/保存流程，未修改源码及现有截图改动，未合并分支。后续模拟器须有可见窗口，Live Edit 尚未建立调试会话。

## 2026-10-18 · 支线修复与整合（已授权）

- 老板在聊天明确授权：整理问题，修复当前能修的问题，最终将支线合并main。根整合分支 `codex/integrate-iterations` 保留各支线历史，PR #20：https://github.com/xingyingyuzhui/modbus_android/pull/20 。
- 已合入模拟UI、虚拟RTU、历史与导出、日志快照、应用级轮询、v2主题/控件/反馈/keyline及CL04工作台，并修正整合冲突。UI、历史、工作台分别实现，独立reviewer只读审阅；全部已发现阻塞项修复并定向复核。
- 最终代码 `1d37136` 本地完整构建46秒成功：core58+app898，0失败/错误/跳过；lint、Roborazzi、debug/release APK通过；PTY工具10项及offline APK检查通过。录制PNG备份到本机私有目录并恢复仓库基线，不提交截图。
- 两项云端CI 39110230148 / 39110227707 均成功（代码1d37136），准备执行授权合并；后续只有文档更新，带skip ci，不重复源码验证。
- 本轮可见模拟器ADB持续offline，重连/冷启动/ADB重启/软件渲染未恢复；没有验证新APK安装和GUI，不wipe其它数据。该项与真实USB/485保留未验证。
