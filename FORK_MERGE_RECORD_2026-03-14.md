# Fork Merge Record (2026-03-14)

## 1. 基线信息
- Upstream 基线: `origin/main` @ `22f12b72`
- 当前 fork 分支: `codex/proxy-vertical-pin`
- 当前记录提交: `845ad4af`
- 本轮目标:
  - Proxy 页改为纵向分组列表（可展开/折叠）
  - 增加一键折叠全部
  - `url-test/fallback` 支持长按 `fix/unfix`
  - UI 显示内核返回 `fixed` 状态
  - fork CI 支持签名调试包 + rolling prerelease 发布

---

## 2. 提交序列（origin/main..HEAD）
1. `e8a2062c` Implement vertical proxy groups and pin support
2. `0c4ab60f` Fix proxy group binding layout
3. `96eeb345` Add signed debug release workflow
4. `9b6ad8d8` Fix debug release asset upload
5. `d3e40a1d` Align release workflows with CI signing
6. `d6d0628a` Refine proxy group pin state UI
7. `e0f1b349` Fix proxy group pin interactions
8. `a067bd85` Stabilize proxy pin state handling
9. `5b52d499` Restore core fixed-state semantics for proxy pin
10. `4afd7424` Add subtle borders to proxy group header
11. `7bc0ea78` Increase proxy header border thickness
12. `845ad4af` Keep pin failure toast non-intrusive

---

## 3. 文件级变更与原因（全部）

### A. 功能必须（建议每次 upstream 更新后都迁移）

1) `app/src/main/java/com/github/kr328/clash/ProxyActivity.kt`
- 变更:
  - 接入 `Pin/Unfix` 请求处理
  - 定时刷新（5s）+ `reloadVersions` 防旧请求覆盖新状态
  - `pin/unfix` 失败时 toast（但仍 `ReloadAll`，不改变业务路径）
- 原因:
  - 保障 fix/unfix 的 UI 与内核状态收敛
  - 解决异步刷新导致的偶发状态回滚

2) `core/src/main/java/com/github/kr328/clash/core/model/ProxyGroup.kt`
- 变更: 新增 `fixed` 字段并写入 Parcel
- 原因: 前端需要拿到内核 `fixed` 状态

3) `core/src/main/golang/native/tunnel/proxies.go`
- 变更:
  - `ProxyGroup` JSON 增加 `fixed`
  - `PatchSelector` 失败返回 `false`（不再假成功）
  - `PatchSelector/UnfixProxy` 同步 `cachefile.SetSelected`
  - 新增 `UnfixProxy`
  - `fixedProxy(group)` 直接透传内核 `fixed` 字段
- 原因:
  - 这是 fix/unfix 核心链路
  - 之前“`fixed==now` 才显示”会误判并导致 UX 混乱，已回退为内核真值

4) `core/src/main/golang/native/tunnel/connectivity.go`
- 变更: `HealthCheck` 对非 selector 先 `ForceSet("")` + 清 cache
- 原因: 与上游 `groups/proxies route` 行为对齐，避免历史 fixed 残留

5) `core/src/main/golang/native/tunnel.go`
- 变更: 导出 `unfixProxy`
- 原因: JNI 层调用入口

6) `core/src/main/cpp/main.c`
- 变更: 新增 `nativeUnfixProxy` JNI 绑定
- 原因: Android -> Go 桥接

7) `core/src/main/java/com/github/kr328/clash/core/bridge/Bridge.kt`
- 变更: 声明 `nativeUnfixProxy`
- 原因: 桥接 API

8) `core/src/main/java/com/github/kr328/clash/core/Clash.kt`
- 变更:
  - 默认 `ProxyGroup` 构造补 `fixed`
  - 暴露 `unfixProxy`
- 原因: core API 对齐模型与 JNI

9) `service/src/main/java/com/github/kr328/clash/service/remote/IClashManager.kt`
- 变更: 新增 `pinProxy/unfixProxy`
- 原因: 远程接口支持 fix/unfix

10) `service/src/main/java/com/github/kr328/clash/service/ClashManager.kt`
- 变更:
  - 实现 `pinProxy/unfixProxy`
  - 对 fixable 组清除 `SelectionDao` 脏记录
- 原因:
  - 避免旧版本错误持久化选择在 profile reload 时反向污染状态

11) `service/src/main/java/com/github/kr328/clash/service/clash/module/ConfigurationModule.kt`
- 变更: 恢复选择时仅处理 `Selector` 组
- 原因: 防止 `url-test/fallback` 被错误走 selector 恢复路径

12) `design/src/main/java/com/github/kr328/clash/design/ProxyDesign.kt`
- 变更:
  - 改为分组列表适配器
  - 增加 `Pin/Unfix` 请求类型
  - 长按弹窗触发 fix/unfix
  - 增加 collapse-all 入口
- 原因: 承载新交互

13) `design/src/main/java/com/github/kr328/clash/design/adapter/ProxyGroupAdapter.kt`（新增）
- 变更:
  - 分组 header + 节点项
  - 展开/折叠
  - `url-test/fallback` 只允许长按，不允许单击选择
- 原因:
  - 解决原始页面结构不支持纵向分组
  - 修复“fallback/url-test 单击误发 patchSelector”致命问题

14) `design/src/main/java/com/github/kr328/clash/design/component/ProxyViewState.kt`
- 变更: 节点 subtitle 增加 fixed badge
- 原因: UI 显示固定节点

15) `design/src/main/res/layout/design_proxy.xml`
- 变更:
  - 去掉 ViewPager + Tab
  - 改为 RecyclerView 分组列表
  - 增加顶部 collapse bar
- 原因: 页面结构调整

16) `design/src/main/res/layout/adapter_proxy_group.xml`（新增）
- 变更: 分组 header 布局（标题、副标题、测速按钮、展开箭头）
- 原因: 纵向分组必需

17) `design/src/main/res/values/strings.xml`
18) `design/src/main/res/values-zh/strings.xml`
- 变更: 新增 `collapse_all` / `proxy_fix_action` / `proxy_unfix_action` / `proxy_fixed_badge` / `proxy_fix_failed` / `proxy_unfix_failed`
- 原因: 新交互文本

### B. 视觉可选（不影响业务逻辑）

19) `design/src/main/res/drawable/bg_proxy_group_header.xml`（新增）
- 变更:
  - 分组 header 淡蓝背景
  - 边框：top `3dp`，left/right `2dp`
- 原因: 视觉分隔增强，避免展开后分组区域混淆
- 备注: 纯样式，可单独删/改，不影响 pin 逻辑

20) `design/src/main/res/values/dimens.xml`
- 变更: 仅文件尾换行（无实际功能变化）

### C. Fork 专用（不建议上游 PR，按你 fork 需求保留）

21) `.github/workflows/build-debug.yaml`
22) `.github/workflows/build-pre-release.yaml`
23) `.github/workflows/build-release.yaml`
24) `.github/signing/README.md`（新增）
25) `.github/signing/ci-debug.keystore.enc`（新增）
26) `build.gradle.kts`
- 变更:
  - CI 解密 keystore + 写 signing.properties
  - 自动发布 rolling prerelease
  - 签名从 `SIGNING_KEYSTORE_FILE` 环境变量读取
- 原因: 这是 fork 的 CI 调试分发能力，不是上游业务需求

---

## 4. 下次 upstream 更新后的迁移建议（给 fork 用）

### 4.1 推荐做法
在最新 `upstream/main` 上新开分支，然后按主题迁移，不要整段 rebase 旧分支。

### 4.2 迁移顺序（建议）
1. 先迁移 **功能必须**（A组）
2. 再迁移 **视觉可选**（B组）
3. 最后迁移 **fork CI**（C组）

### 4.3 可直接 cherry-pick 的提交（按顺序）
功能+稳定性：
- `e8a2062c`
- `0c4ab60f`
- `d6d0628a`
- `e0f1b349`
- `a067bd85`
- `5b52d499`
- `845ad4af`

视觉（可选）：
- `4afd7424`
- `7bc0ea78`

fork CI（可选，按你 fork 继续发布调试包时再带）：
- `96eeb345`
- `9b6ad8d8`
- `d3e40a1d`

> 注: 以上 commit 依赖关系按当前分支验证过；若 upstream 相同文件改动较大，优先按“第3节文件级原因”手工迁移，避免机械冲突。

---

## 5. 已知现象与取舍
- 现象: 偶发第一次 `pin` 没反应，重试可成功（低频）。
- 当前处理:
  - 保留失败 toast 用于可观测性
  - 不改变业务路径（失败仍会 `ReloadAll`）
- 取舍原因: 在不扩大改动面的前提下，先保证主链路稳定可用。

---

## 6. 验证基线（本轮）
- 本地: `./gradlew --no-daemon :app:assembleAlphaRelease` 通过
- CI: `Build Debug` 多次通过（含签名与 rolling prerelease）

---

## 7. 给未来维护者的结论
- 若只保功能，保留 A 组文件即可。
- 若只做视觉调整，可只改 `bg_proxy_group_header.xml`。
- 若要继续 fork 自动分发调试包，保留 C 组文件与 secrets 流程。

---

## 8. CI/Release Runbook（给下一个 AI）

### 8.1 前置检查（首次或新 fork 必做）
- 仓库 Actions 必须启用（Repository Settings -> Actions）。
- 仓库 secrets 必须存在:
  - `CI_DEBUG_SIGNING_KEYSTORE_DECRYPT_PASSWORD`
  - `CI_DEBUG_SIGNING_STORE_PASSWORD`
- `.github/signing/ci-debug.keystore.enc` 必须存在且可解密。
- Workflow permissions 需允许写 release（`contents: write`）。

### 8.2 合并上游后推荐执行顺序
1. 基于新 `upstream/main` 建分支并迁移本记录第4节的 commits。
2. 本地先做至少一次编译验证:
   - `./gradlew --no-daemon :app:assembleAlphaRelease`
3. push 到 fork 分支，让 `Build Debug` 自动触发并生成调试 release。

### 8.3 如何触发与观察 CI
- 自动触发:
  - `build-debug.yaml`: 任意分支 push 会触发。
  - `build-pre-release.yaml`: push `main` 触发。
- 手动触发:
  - GitHub Actions 页面使用 `Run workflow`。
  - `build-release.yaml` 需输入 `release-tag`（如 `v2.11.25`）。
- 运行状态检查（CLI）:
  - `gh run list --limit 10`
  - `gh run view <run-id> --log`

### 8.4 如何确认 release 是正确版本
- 检查 run 的 `head_sha` 是否等于当前分支提交。
- 检查 release 的 `target_commitish` 是否等于该 `head_sha`。
- `Build Debug` 产物 tag 规则:
  - `debug-alpha-<branch>`
- `Build Pre-Release` 产物 tag 规则:
  - `main` 分支: `Prerelease-alpha`
  - 其他分支: `Prerelease-alpha-<branch>`

### 8.5 APK 验签与快速核验（可选）
- 下载 `arm64-v8a` 后可本地验签:
  - `apksigner verify --print-certs <apk-path>`
- 至少确认:
  - `Verified using v1 scheme: true`
  - `Verified using v2 scheme: true`

### 8.6 常见失败排查
- `Decrypt CI debug keystore` 失败:
  - 通常是 `CI_DEBUG_SIGNING_KEYSTORE_DECRYPT_PASSWORD` 不匹配。
- `Signing properties` 失败:
  - 通常是 `CI_DEBUG_SIGNING_STORE_PASSWORD` 缺失或错误。
- release 未更新:
  - 检查 workflow 是否跑在预期分支；
  - 检查 run 是否成功到 `Publish rolling prerelease` 步骤；
  - 检查 release `target_commitish` 是否指向新 SHA。

---

## 9. update-dependencies 安全模式补充（2026-03-15）
- workflow: `.github/workflows/update-dependencies.yaml`
- 变更:
  - 仅允许手动触发（去掉自动 dispatch）
  - 增加强校验：`target_branch`、`.gitmodules` submodule URL、`.gitmodules` submodule branch
  - PR 分支按目标分支命名，避免多分支并行时冲突
- 运行手册:
  - `UPDATE_DEPENDENCIES_SAFE_MODE_2026-03-15.md`
- 目标:
  - 防止多 core / 多分支并行时依赖更新串线
  - 防止 submodule 被错误拉到非目标 core 线路

---

## 11. Release 不覆盖策略（2026-03-20）
- 要求: 新构建必须新增 release，不允许覆盖旧包。
- 当前执行:
  - `build-debug.yaml` 与 `build-pre-release.yaml` 改为唯一 tag（包含 branch/shortsha/runid）。
  - `build-release.yaml` 若发现同名 tag 或 release 已存在则直接失败。
- 效果:
  - 历史验证过的 release 长期可下载。
  - 避免 rolling tag 覆盖导致“好用版本被冲掉”。
