# Kernel Update Guide / 内核更新指南 (2026-03-26)

## 1. Scope / 范围
- 适用分支: `codex/proxy-vertical-pin-custom-mihomo-2026-03-26`
- 目标: 说明 `alpha` 与 `pin` 两条内核线的真实更新方式。

## 2. Current Kernel Mapping / 当前内核映射
- 仓库中的真实子模块只有一个: `core/src/foss/golang/clash`。
- 当前 `.gitmodules`:
  - `url = https://github.com/lovitus/mihomo`
  - `branch = codex/persistent-pin-option`
- 当前仓库基线指针（gitlink）: `1101ff88f227c6575d010a0054b1ed5d31874d91`

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
`pin` 使用 workflow 固定值 `PIN_KERNEL_REF`（当前为 `1101ff88...`）。

结论:
- 只要 `PIN_KERNEL_REF` 不变，构建出来的 `pin` 内核就不会变。
- `pin` 不是自动跟随。

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
1. 先把目标 commit 推到 `lovitus/mihomo` 的 `codex/persistent-pin-option`。
2. 运行 `Update Clash-Core and Go Modules (Manual Guarded)`:
   - `target_branch`: `codex/proxy-vertical-pin-custom-mihomo-2026-03-26`
   - `expected_submodule_url`: `https://github.com/lovitus/mihomo`
   - `expected_submodule_branch`: `codex/persistent-pin-option`
   - `create_pr`: `true`
3. 合并 PR（更新子模块指针 + go mod）。
4. 把以下文件中的 `PIN_KERNEL_REF` 同步改为同一新 commit:
   - `.github/workflows/build-debug.yaml`
   - `.github/workflows/build-pre-release.yaml`
   - `.github/workflows/build-release.yaml`
5. 触发 `Build Debug` 验证 `alpha` / `pin` 两套产物。

## 7. Important Note / 关键注意
- 只跑 `update-dependencies` 而不改 `PIN_KERNEL_REF`，`pin` 构建仍会打旧 commit。
- `update-dependencies` 输入的默认 `target_branch` 可能不是当前主分支，运行前必须手动确认。
