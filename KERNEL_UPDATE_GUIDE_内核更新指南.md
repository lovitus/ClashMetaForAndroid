# Kernel Update Guide / 内核更新指南 (2026-04-20)

## 1. Scope / 范围
- 适用分支: `codex/proxy-vertical-pin-custom-mihomo-2026-04-20`
- 目标: 说明 `alpha` 与 `pin` 两条内核线的真实更新方式。

## 2. Current Kernel Mapping / 当前内核映射
- 仓库中的真实子模块只有一个: `core/src/foss/golang/clash`。
- 当前 `.gitmodules`:
  - `url = https://github.com/lovitus/mihomo`
  - `branch = codex/persistent-pin-option`
- 当前 workflow 固定 `pin` 来源:
  - release tag: `v2026.04.20-persistent-pin.5`
  - release branch: `persistent-pin-option-1.19.24merge`
  - resolved commit: `313230d95da4f28f9c23327709d7c91dcfc48919`

## 3. How `alpha` Is Updated / `alpha` 如何更新
`alpha` 在构建时动态解析，不依赖当前分支的子模块指针。

- 在 `build-debug / build-pre-release / build-release` 中，`alpha` 都会执行:
  1. `git fetch origin main`
  2. 读取 `origin/main` 的 `core/src/foss/golang/clash` gitlink
  3. checkout 到该 commit 进行构建

结论:
- `alpha` 是“构建时跟随上游 `origin/main`”。
- 不需要运行 `update-dependencies` 才能更新 `alpha`。

## 4. How `pin` Is Updated / `pin` 如何更新
`pin` 使用 workflow 固定值 `PIN_KERNEL_REF`。

当前固定值对应:
- release tag: `v2026.04.20-persistent-pin.5`
- release branch: `persistent-pin-option-1.19.24merge`
- resolved commit: `313230d95da4f28f9c23327709d7c91dcfc48919`
- CI build normalization: checkout 后会对 `clash` 子模块与 `core/src/foss/golang` 入口模块分别执行一次 `go mod tidy`，再按 Android ABI 预解析 `cfa/native` 依赖图

结论:
- 只要 `PIN_KERNEL_REF` 不变，构建出来的 `pin` 内核就不会变。
- `pin` 不是自动跟随。
- `pin` 的固定来源可以和 `.gitmodules` 的默认 branch 不同，实际以 workflow 明确写死的 ref 为准。
- 如果目标内核线在当前 Go 版本下存在 module 元数据漂移，CI 会先对 `clash` / `foss` 两层 module 做归一化，再按 Android ABI 对 `cfa/native` 执行 `go list -mod=mod -deps -tags foss,with_gvisor,cmfa` 预解析，避免在 Gradle 外部 Go 构建阶段才触发 `go.mod` 更新错误。

## 5. What `update-dependencies.yaml` Actually Does / `update-dependencies.yaml` 实际作用
Workflow: `.github/workflows/update-dependencies.yaml`

它会:
1. 校验 `target_branch` 与 `.gitmodules` 映射（URL/branch guard）
2. 执行 `git submodule update --remote` 更新仓库子模块指针
3. 更新 `core/src/foss/golang` 与 `core/src/main/golang` 的 `go.mod/go.sum`
4. 生成 PR（`create_pr=true`）

它不会:
- 自动修改 `PIN_KERNEL_REF`
- 自动改变 `alpha` 的来源逻辑

## 6. Recommended Update Procedure / 推荐更新流程

### A) 仅更新 `alpha`
无需改仓库。
- 直接触发构建工作流即可，`alpha` 会按当时的 `origin/main` 解析。

### B) 更新自定义 `pin`（推荐稳定做法）
1. 先在 `lovitus/mihomo` 准备好目标发布线（branch/tag/commit）。
2. 运行 `Update Clash-Core and Go Modules (Manual Guarded)`:
   - `target_branch`: `codex/proxy-vertical-pin-custom-mihomo-2026-04-20`
   - `expected_submodule_url`: `https://github.com/lovitus/mihomo`
   - `expected_submodule_branch`: `codex/persistent-pin-option`
   - `create_pr`: `true`
3. 合并 PR（更新子模块指针 + go mod）。
4. 把以下文件中的 `PIN_KERNEL_REF` 同步改为同一新 commit:
   - `.github/workflows/build-debug.yaml`
   - `.github/workflows/build-pre-release.yaml`
   - `.github/workflows/build-release.yaml`
5. 同步更新文档里的 pin 来源说明，至少记录:
   - release tag
   - release branch
   - resolved commit
6. 触发 `Build Debug` 验证 `alpha` / `pin` 两套产物。

## 7. Important Note / 关键注意
- 只跑 `update-dependencies` 而不改 `PIN_KERNEL_REF`，`pin` 构建仍会打旧 commit。
- `update-dependencies` 输入的默认 `target_branch` 可能不是当前主分支，运行前必须手动确认。
