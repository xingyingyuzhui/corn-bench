# Corn Bench 里程碑看板

每个里程碑（推送 / 本地构建 / CI 结果）都记在这里。没验证的写“未验证”。

## 2026-10-27 初始导入

- 源仓库可见性：`xingyingyuzhui/Corn-Flashing` PUBLIC，`xingyingyuzhui/corn-modbus` PUBLIC → 新仓库 `xingyingyuzhui/corn-bench` 建为 **public**。
- 历史导入：本机无 git-filter-repo，改用“临时克隆里一次 `git mv` 提交 + `git merge --allow-unrelated-histories`”。
  - Corn Flashing main @ 24bc94f（55 个提交）→ 移动提交 9a05794
  - Corn Modbus main @ a0a0b98（175 个提交）→ 移动提交 6a7f449
  - 合并提交 f877666（Modbus）、aae066e（Flashing）；`git log --follow` 可追到两边最早的提交。
- 根配置：settings（4 个导入模块）、catalog 插件、合并 .gitignore、LICENSE.md 补 Required Notice、合并 NOTICE、新 README。
- 本地构建（本机无 NDK / CMake，只跑纯 JVM 模块）：
  - `:flashing-core:test` 164 个通过，`:modbus-core:test` 58 个通过（0 失败）。
  - 含原生代码的 `:feature-flashing` 本地未构建，交给 GitHub CI。
