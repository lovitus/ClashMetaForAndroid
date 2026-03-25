package com.github.kr328.clash

import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.design.ProxyDesign
import com.github.kr328.clash.design.R as DesignR
import com.github.kr328.clash.design.model.ProxyState
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class ProxyActivity : BaseActivity<ProxyDesign>() {
    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun main() {
        val mode = withClash { queryOverride(Clash.OverrideSlot.Session).mode }
        val names = withClash { queryProxyGroupNames(uiStore.proxyExcludeNotSelectable) }
        val states = List(names.size) { ProxyState("?") }
        val unorderedStates = names.indices.map { names[it] to states[it] }.toMap()
        val reloadLock = Semaphore(10)
        val reloadVersions = IntArray(names.size)
        var shouldRestoreGlobalSelection =
            mode == TunnelState.Mode.Global && uiStore.proxyGlobalLastSelection.isNotBlank()

        val design = ProxyDesign(
            this,
            mode,
            names,
            uiStore
        )

        setContentDesign(design)

        design.requests.send(ProxyDesign.Request.ReloadAll)

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ProfileLoaded -> {
                            val newNames = withClash {
                                queryProxyGroupNames(uiStore.proxyExcludeNotSelectable)
                            }

                            if (newNames != names) {
                                startActivity(ProxyActivity::class.intent)

                                finish()
                            }
                        }
                        else -> Unit
                    }
                }
                onTimeout(AUTO_REFRESH_INTERVAL) {
                    names.indices.forEach { idx ->
                        design.requests.trySend(ProxyDesign.Request.Reload(idx))
                    }
                }
                design.requests.onReceive {
                    when (it) {
                        ProxyDesign.Request.ReLaunch -> {
                            startActivity(ProxyActivity::class.intent)

                            finish()
                        }
                        ProxyDesign.Request.ReloadAll -> {
                            names.indices.forEach { idx ->
                                design.requests.trySend(ProxyDesign.Request.Reload(idx))
                            }
                        }
                        is ProxyDesign.Request.Reload -> {
                            val version = ++reloadVersions[it.index]

                            launch {
                                val group = reloadLock.withPermit {
                                    withClash {
                                        queryProxyGroup(names[it.index], uiStore.proxySort)
                                    }
                                }

                                if (version != reloadVersions[it.index]) {
                                    return@launch
                                }

                                val state = states[it.index]

                                state.now = group.now

                                if (shouldRestoreGlobalSelection && names[it.index] == "GLOBAL") {
                                    shouldRestoreGlobalSelection = false

                                    val remembered = uiStore.proxyGlobalLastSelection
                                    if (remembered.isNotBlank() &&
                                        remembered != group.now &&
                                        group.proxies.any { p -> p.name == remembered }) {
                                        val restored = withClash {
                                            patchSelector("GLOBAL", remembered)
                                        }

                                        if (restored) {
                                            state.now = remembered
                                        }
                                    }
                                }

                                design.updateGroup(
                                    it.index,
                                    group.proxies,
                                    group.type,
                                    state,
                                    unorderedStates,
                                    group.fixed
                                )
                            }
                        }
                        is ProxyDesign.Request.Select -> {
                            val success = withClash {
                                patchSelector(names[it.index], it.name)
                            }

                            if (success) {
                                states[it.index].now = it.name

                                if (names[it.index] == "GLOBAL") {
                                    uiStore.proxyGlobalLastSelection = it.name
                                }
                            }

                            design.updateSelection(it.index)
                        }
                        is ProxyDesign.Request.Pin -> {
                            val success = withClash {
                                pinProxy(names[it.index], it.name)
                            }

                            if (!success) {
                                design.showToast(DesignR.string.proxy_fix_failed, ToastDuration.Short)
                            }

                            design.requests.send(ProxyDesign.Request.ReloadAll)
                        }
                        is ProxyDesign.Request.Unfix -> {
                            val success = withClash {
                                unfixProxy(names[it.index])
                            }

                            if (!success) {
                                design.showToast(DesignR.string.proxy_unfix_failed, ToastDuration.Short)
                            }

                            design.requests.send(ProxyDesign.Request.ReloadAll)
                        }
                        is ProxyDesign.Request.UrlTest -> {
                            launch {
                                withClash {
                                    healthCheck(names[it.index])
                                }

                                design.requests.send(ProxyDesign.Request.Reload(it.index))
                            }
                        }
                        is ProxyDesign.Request.PatchMode -> {
                            design.showModeSwitchTips()

                            withClash {
                                val o = queryOverride(Clash.OverrideSlot.Session)

                                o.mode = it.mode

                                patchOverride(Clash.OverrideSlot.Session, o)
                            }

                            val remembered = uiStore.proxyGlobalLastSelection
                            val restoredImmediately = if (
                                it.mode == TunnelState.Mode.Global && remembered.isNotBlank()
                            ) {
                                withClash {
                                    patchSelector("GLOBAL", remembered)
                                }
                            } else {
                                false
                            }

                            shouldRestoreGlobalSelection =
                                it.mode == TunnelState.Mode.Global &&
                                    remembered.isNotBlank() &&
                                    !restoredImmediately

                            design.requests.trySend(ProxyDesign.Request.ReloadAll)
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val AUTO_REFRESH_INTERVAL = 5_000L
    }
}
