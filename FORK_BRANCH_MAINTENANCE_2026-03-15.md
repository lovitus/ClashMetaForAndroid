# Fork Branch Maintenance Notes (2026-03-15)

## 1. Branch Matrix
| Branch | Purpose | mihomo Source | Notes |
| --- | --- | --- | --- |
| `codex/proxy-vertical-pin` | 主功能分支（纵向分组 + fix/unfix UI + CI 发布） | 上游默认（`MetaCubeX/mihomo`） | 作为后续与 upstream 对齐的主迁移目标 |
| `codex/proxy-vertical-pin-custom-mihomo` | 验证/使用 custom mihomo（persistent-pin） | `lovitus/mihomo` + submodule pinned commit | 仅为 custom core 场景，不应反向污染主功能分支 |

---

## 2. Divergence Summary
- 对比分支:
  - base: `codex/proxy-vertical-pin`
  - compare: `codex/proxy-vertical-pin-custom-mihomo`
- 分叉点（merge-base）: `f9e4f8a1` (`Add CI and release runbook to merge record`)
- custom 分支独有提交（按时间）:
  1. `30865373` `chore(core): switch mihomo to lovitus persistent-pin fork`
  2. `3dc6b948` `fix(proxy): keep fixed pin during health checks`
  3. `fe6f14ec` `fix(config): enforce persistent pin for test/fallback groups`
  4. `0527f616` `revert(config): remove forced persistent-pin patching`
  5. `1660882b` `chore(debug): add pin diagnostics and core reset/load traces`
  6. `1e3a62d6` `ci(debug): include branch metadata and log debugging release title`
  7. `abc4fe64` `chore(ci): clarify mihomo metadata and restore baseline submodule`
  8. `5bcc8545` `fix(proxy-ui): prevent repeated fixed badge on group nodes`

---

## 3. Net File Diff Classification (`proxy-vertical-pin...proxy-vertical-pin-custom-mihomo`)
| File | Type | Should Backport to `proxy-vertical-pin` | Reason |
| --- | --- | --- | --- |
| `.gitmodules` | custom core routing | No | 仅 custom-mihomo 分支需要 |
| `core/src/foss/golang/clash` (submodule pointer) | custom core pin | No | 仅 custom-mihomo 分支需要 |
| `core/src/main/golang/native/tunnel/connectivity.go` | 通用逻辑修复 | Yes | 修复“点击组测速会立刻撤销 pin/fix” |
| `design/src/main/java/com/github/kr328/clash/design/component/ProxyViewState.kt` | 通用 UI 修复 | Yes | 修复 fixed 徽标/文字重复拼接 |
| `core/src/main/golang/native/config/load.go` | 调试日志 | No (默认) | 仅诊断用途，非业务必需 |
| `core/src/main/golang/native/main.go` | 调试日志 | No (默认) | 仅输出 `[APP] core reset` |
| `.github/workflows/build-debug.yaml` | fork CI 描述增强 | Optional | 仅影响 release 文案和 metadata，不影响业务逻辑 |

---

## 4. Invalid / Neutralized Changes
- `fe6f14ec` + `0527f616` 是一组“先加后回退”的修改，目标文件均为 `core/src/main/golang/native/config/process.go`。
- 当前两个分支在该文件上 **无净差异**，可视为无效增量，不需要回迁。

---

## 5. Backport Plan (custom -> proxy-vertical-pin)
仅回迁通用 bug 修复:
1. `3dc6b948`  
   影响文件: `core/src/main/golang/native/tunnel/connectivity.go`  
   目的: 保留 fix/pin，不在 `HealthCheck` 时被强制清空。
2. `5bcc8545`  
   影响文件: `design/src/main/java/com/github/kr328/clash/design/component/ProxyViewState.kt`  
   目的: 防止 fixed 徽标在 group-as-node 场景反复追加。

不回迁:
- custom core 切换（`.gitmodules` + submodule pointer）
- 诊断日志（`load.go`, `main.go`）
- debug release 文案变更（`build-debug.yaml`，按需）

---

## 6. Upstream Merge Playbook (MetaCubeX/ClashMetaForAndroid)

### 6.1 For `codex/proxy-vertical-pin`
1. 在最新 upstream 基线上创建新工作分支。
2. 优先迁移主功能记录文件 `FORK_MERGE_RECORD_2026-03-14.md` 的 A 组改动。
3. 额外补上本文件第 5 节的两个通用 bug 修复（`3dc6b948`, `5bcc8545`）。
4. 保持 `.gitmodules` 指向 upstream 默认 core。
5. 跑本地编译与关键交互回归（fix/unfix、group health check、group-as-node）。
6. push 后跑 `Build Debug`，验证 APK 和 release。

### 6.2 For `codex/proxy-vertical-pin-custom-mihomo`
1. 先将 `proxy-vertical-pin` 的最新通用修复合入（merge/cherry-pick）。
2. 再应用 custom core 路由:
   - `.gitmodules` 指向 `lovitus/mihomo`
   - submodule 指针更新到目标 custom commit/tag
3. 不默认携带临时诊断日志；仅在排障窗口开启。
4. push 后用 `Build Debug` 产物做验证，重点观察 persistent-pin 场景。

---

## 7. Validation Focus After Any Merge
- `url-test/fallback` 长按 fix 后，点击组测速不会自动掉 pin。
- group-as-node 显示 fixed 时，不会出现徽标重复堆叠。
- UI fixed 状态与内核返回一致，unfix 后显示及时回落。
- release 元数据中的 branch/core commit 与实际构建一致。

---

## 8. Release Immutability Policy (2026-03-20)
- 目标: 每次构建新增 release，禁止覆盖旧 release/tag，保留可回滚安装包。
- `build-debug.yaml`:
  - tag 使用唯一值: `debug-alpha-<branch>-<shortsha>-r<runid>`
  - 不再删除同名 release/tag
- `build-pre-release.yaml`:
  - tag 使用唯一值: `prerelease-alpha-<branch>-<shortsha>-r<runid>`
  - 不再删除同名 release/tag
- `build-release.yaml`:
  - 发布前强校验：若 tag/release 已存在则失败
  - 不再执行上传覆盖/notes 覆盖
