# Update Dependencies Safe Mode (2026-03-15)

## 1. What Changed
- Workflow: `.github/workflows/update-dependencies.yaml`
- Mode changed to: manual-only (`workflow_dispatch`), no auto dispatch.
- Added hard guards before updating:
  - target branch must match checkout branch
  - `.gitmodules` submodule URL must match expected input
  - `.gitmodules` submodule branch must match expected input

---

## 2. Why This Is Needed
For your multi-core, multi-branch workflow, automatic dependency updates are risky:
- wrong branch can receive update PR
- wrong core line can be updated by `submodule --remote`
- one fixed PR branch name can collide across branches

This safe mode prevents silent cross-branch/core contamination.

---

## 3. How To Run
Run Actions -> `Update Clash-Core and Go Modules (Manual Guarded)` -> `Run workflow`.

Required inputs:
1. `target_branch`
2. `expected_submodule_url`
3. `expected_submodule_branch`
4. `create_pr` (`true` recommended)

---

## 4. Input Matrix (Use Exactly)

For `codex/proxy-vertical-pin`:
- `target_branch`: `codex/proxy-vertical-pin`
- `expected_submodule_url`: `https://github.com/MetaCubeX/mihomo`
- `expected_submodule_branch`: `Alpha`
- `create_pr`: `true`

For `codex/proxy-vertical-pin-custom-mihomo`:
- `target_branch`: `codex/proxy-vertical-pin-custom-mihomo`
- `expected_submodule_url`: `https://github.com/lovitus/mihomo`
- `expected_submodule_branch`: `codex/persistent-pin-option`
- `create_pr`: `true`

If any value is wrong, workflow should fail early by design.

---

## 5. Expected Outputs
- PR base branch = `target_branch`
- PR branch = `update-dependencies-<safe-target-branch>`
- PR body includes:
  - guarded `.gitmodules` mapping
  - clash submodule before/after SHA
  - go-mod update scope

---

## 6. Does This Affect Daily Build/Debug?
No.
- `build-debug`, `build-pre-release`, `build-release` are unchanged by this safe mode.
- Only dependency/core update automation becomes manual and guarded.

---

## 7. Mandatory Verification Before Merge
1. Confirm PR base branch is correct.
2. Confirm `.gitmodules` did not switch to the wrong core URL/branch.
3. Confirm submodule pointer moves on the intended core line only.
4. Run `Build Debug` on that branch before merging.
