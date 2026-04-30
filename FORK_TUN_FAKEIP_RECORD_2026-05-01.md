# Fork TUN/Fake-IP Record (2026-05-01)

## Summary
本记录用于后续在 fork 分支合并 `MetaCubeX/ClashMetaForAndroid` 上游时，恢复本 fork 的多用户/工作资料网络隔离能力。

本次新增两个相互独立的能力：
- Android TUN 地址自动隔离：只影响 CFMA `VpnService` 运行时传给 native `startTun()` 的 gateway/portal/DNS。
- `dns.fake-ip-range` 覆写：只影响 mihomo DNS fake-ip 池，写入现有 override JSON，并通过现有 reload 机制生效。

两者禁止联动。fake-ip-range 不是 Android `VpnService` TUN 地址来源。

## Source Basis
- mihomo DNS 文档说明 `dns.fake-ip-range` 是 fake-ip 池配置，原生 TUN 默认 IPv4 地址会参考它：
  - `https://wiki.metacubex.one/en/config/dns/`
- mihomo TUN 文档说明 `system/gvisor/mixed` stack 差异，并列出 Android userId 常见值：机主 `0`、手机分身 `10`、应用多开 `999`：
  - `https://wiki.metacubex.one/en/config/inbound/tun/`
- mihomo listener TUN 文档支持 `inet4-address/inet6-address`，但这是 listener TUN 配置，不是 CFMA 当前 Android `VpnService` fd 路径：
  - `https://wiki.metacubex.one/en/config/inbound/listeners/tun/`
- CFMA 当前源码路径确认：
  - `TunService.kt` 创建 Android `VpnService.Builder` fd。
  - `patchTun()` 禁用 profile 内置 TUN。
  - native `startTun()` 使用 CFMA 传入的 gateway/portal/dns。

## Implementation
### Android TUN 地址隔离
相关文件：
- `service/src/main/java/com/github/kr328/clash/service/TunAddress.kt`
- `service/src/main/java/com/github/kr328/clash/service/TunService.kt`
- `service/src/main/java/com/github/kr328/clash/service/store/ServiceStore.kt`
- `design/src/main/java/com/github/kr328/clash/design/NetworkSettingsDesign.kt`

新增 `ServiceStore.autoTunAddressIsolation`，默认 `false`。默认关闭时完全保持旧地址：
- IPv4 gateway: `172.19.0.1/30`
- IPv4 portal/DNS: `172.19.0.2`
- IPv6 gateway: `fdfe:dcba:9876::1/126`
- IPv6 portal/DNS: `fdfe:dcba:9876::2`

开启后用 `Process.myUid() / 100000` 推导 Android userId：
- userId `0`：仍使用 bucket `19`，保持主用户兼容。
- userId `10`：bucket `20`，地址 `172.20.0.1/30 -> 172.20.0.2`。
- userId `999`：bucket `29`，地址 `172.29.0.1/30 -> 172.29.0.2`。
- 其他非 0 userId：bucket `20 + floorMod(userId, 10)`。

`TunService.open()` 不再直接使用散落常量，统一读取 `TunAddress`：
- `addAddress`
- `addRoute` 的 virtual DNS `/32` 或 `/128`
- `addDnsServer`
- `TunModule.TunDevice.gateway`
- `TunModule.TunDevice.portal`
- `TunModule.TunDevice.dns`

设置页显示当前计算值，且服务运行中沿用现有 VPN 选项禁用逻辑。

### fake-ip-range 覆写
相关文件：
- `core/src/main/java/com/github/kr328/clash/core/model/ConfigurationOverride.kt`
- `design/src/main/java/com/github/kr328/clash/design/OverrideSettingsDesign.kt`

新增字段：
- Kotlin: `ConfigurationOverride.Dns.fakeIpRange: String?`
- JSON: `dns.fake-ip-range`

UI 只允许枚举选择：
- 不覆写，使用配置文件或 CFMA 默认
- `28.0.0.0/8`
- `29.0.0.0/8`
- `198.18.0.1/16`
- `198.19.0.1/16`

`null` 表示不覆写，不强制写入 override JSON。

## Safety Boundaries
- 默认关闭 TUN 地址隔离，主用户行为不变。
- TUN 地址隔离不写入 profile，不写入 `override.json`。
- fake-ip-range 不改变 Android `VpnService` gateway/portal/DNS。
- 不新增 JNI/Go bridge。
- 不改 mihomo submodule。
- 不改 selector、pin/fixed、health check、proxy UI。
- `systemProxy` 本地 bypass 规则已有 `172.2*`，覆盖 `172.20` 到 `172.29`。

## Tests
新增自动测试：
- `service/src/test/java/com/github/kr328/clash/service/TunAddressTest.kt`
- `core/src/test/java/com/github/kr328/clash/core/model/ConfigurationOverrideTest.kt`

本地验证命令：
```bash
ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew --no-daemon :service:testAlphaDebugUnitTest :core:testAlphaDebugUnitTest
ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew --no-daemon :design:compileAlphaDebugKotlin
ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew --no-daemon :app:compileAlphaDebugKotlin
```

本轮结果：
- `:service:testAlphaDebugUnitTest` 通过。
- `:core:testAlphaDebugUnitTest` 通过。
- `:design:compileAlphaDebugKotlin` 通过。
- `:app:compileAlphaDebugKotlin` 通过。

## Manual Acceptance
- 默认关闭隔离：主用户 TUN 地址仍为 `172.19.0.1/30`，旧行为不变。
- work profile 开启隔离：设置页显示 userId `10`、bucket `20`、gateway `172.20.0.1/30`、portal/DNS `172.20.0.2`。
- 主用户和 work profile 同时运行 `system` stack：DNS/TCP/UDP 不再因共享 `172.19.0.0/30` 冲突而互相影响。
- `gvisor` / `mixed` stack 不回归。
- `bypassPrivateNetwork`、`dnsHijacking`、`systemProxy`、`allowIpv6` 组合下无 DNS 黑洞。
- fake-ip-range 修改后，override JSON 写入 `dns.fake-ip-range`，TUN 地址不随之变化。

## Merge Notes
后续合并上游时优先检查：
- `TunService.kt` 是否仍由 Android `VpnService` 创建 fd 并传入 native。如果上游改用 mihomo 原生 TUN 配置，本方案需要重新评估。
- `ConfigurationOverride.Dns` 是否已有 `fake-ip-range` 字段。如果上游新增同名字段，保留上游字段并只迁移 UI 枚举。
- `NetworkSettingsDesign.kt` 的运行中禁用逻辑是否变化。隔离开关必须继续在服务运行中禁用，避免用户误以为运行时可热切换。
