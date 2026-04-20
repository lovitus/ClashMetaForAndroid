# Dual Kernel Unified Build / 双内核统一构建 (2026-04-20)

## 1. Goal / 目标
在同一套应用代码上，一次 CI 同时产出两套内核安装包:
- `alpha`（上游跟随）
- `pin`（自定义固定）

## 2. Design Summary / 设计摘要
- 不新增第二个子模块。
- 使用 workflow matrix 在构建阶段切换 `core/src/foss/golang/clash` 的 checkout commit。
- 应用层代码、UI、配置逻辑保持一套。

## 2.1 Current Pin Release Line / 当前 Pin 发布线
- release tag: `v2026.04.20-persistent-pin.5`
- release branch: `persistent-pin-option-1.19.24merge`
- resolved commit: `313230d95da4f28f9c23327709d7c91dcfc48919`
- 说明: 当前 Android fork 的 `persistent-pin` 构建固定使用这条稳定发布线

## 3. Workflow Coverage / 覆盖的工作流

### 3.1 `build-debug.yaml`
- matrix: `alpha` + `pin`
- `alpha`: 读取 `origin/main` 子模块指针
- `pin`: 使用 `PIN_KERNEL_REF`
- debug release tag/name 包含 kernel id，避免冲突

### 3.2 `build-pre-release.yaml`
- matrix: `alpha` + `pin`
- 与 debug 同样的 kernel 解析逻辑
- prerelease tag/name 包含 kernel id

### 3.3 `build-release.yaml`
三阶段:
1. `PrepareRelease`
   - 处理版本、校验 tag/release、打 tag、push
2. `BuildRelease`（matrix: `alpha` + `pin`）
   - 每个内核单独构建
   - APK 文件名加 `-alpha` / `-pin` 后缀
3. `PublishRelease`
   - 汇总两套 APK 到同一 release tag 发布

## 4. Runtime Identification / 安装后如何区分
About 页面显示:
- 第一行: App `versionName`
- 第二行: `nativeCoreVersion()`（包含 core 编译信息）

由于 `alpha` / `pin` 使用不同 commit 构建，第二行至少会出现不同 hash，可用于区分安装包来源。

## 5. Operational Checklist / 运维检查清单
每次发版前建议确认:
1. `alpha` 来源是否符合预期（上游 `origin/main` 当前指针）
2. `pin` 的 `PIN_KERNEL_REF` 是否为目标 commit
   - 如使用 release line，建议同时核对 tag / branch / resolved commit 三元组
3. release 产物是否同时包含 `-alpha` 与 `-pin`
4. About 页 core version 是否能区分两包

## 6. Constraints / 约束
- 这是“统一代码 + 双内核构建”方案，不是“双分支双代码”方案。
- 若 `pin` 需要升级，必须显式更新 `PIN_KERNEL_REF`。
