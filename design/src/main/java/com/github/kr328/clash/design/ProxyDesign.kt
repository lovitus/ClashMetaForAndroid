package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.design.adapter.ProxyGroupAdapter
import com.github.kr328.clash.design.component.ProxyMenu
import com.github.kr328.clash.design.component.ProxyViewConfig
import com.github.kr328.clash.design.databinding.DesignProxyBinding
import com.github.kr328.clash.design.model.ProxyState
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.bindInsets
import com.github.kr328.clash.design.util.getPixels
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProxyDesign(
    context: Context,
    overrideMode: TunnelState.Mode?,
    groupNames: List<String>,
    uiStore: UiStore,
) : Design<ProxyDesign.Request>(context) {
    sealed class Request {
        object ReloadAll : Request()
        object ReLaunch : Request()

        data class PatchMode(val mode: TunnelState.Mode?) : Request()
        data class Reload(val index: Int) : Request()
        data class Select(val index: Int, val name: String) : Request()
        data class Pin(val index: Int, val name: String) : Request()
        data class Unfix(val index: Int) : Request()
        data class UrlTest(val index: Int) : Request()
    }

    private val binding = DesignProxyBinding
        .inflate(context.layoutInflater, context.root, false)

    private var config = ProxyViewConfig(context, uiStore.proxyLine)

    private val adapter = ProxyGroupAdapter(
        context = context,
        config = config,
        uiStore = uiStore,
        groupNames = groupNames,
        groupInteracted = { index ->
            if (index in groupNames.indices) {
                uiStore.proxyLastGroup = groupNames[index]
            }
        },
        clicked = { index, name ->
            requests.trySend(Request.Select(index, name))
        },
        longClicked = { index, proxy, fixed ->
            showPinDialog(index, proxy, fixed)
        },
        urlTestClicked = { index ->
            requestUrlTesting(index)
        }
    )

    private val menu: ProxyMenu by lazy {
        ProxyMenu(context, binding.menuView, overrideMode, uiStore, requests) {
            config.proxyLine = uiStore.proxyLine
            adapter.updateConfig()
            binding.groupsView.recycledViewPool.clear()
        }
    }

    override val root: View = binding.root

    suspend fun updateGroup(
        position: Int,
        proxies: List<Proxy>,
        type: Proxy.Type,
        parent: ProxyState,
        links: Map<String, ProxyState>,
        fixed: String,
    ) {
        withContext(Dispatchers.Main) {
            adapter.updateGroup(position, type, proxies, parent, links, fixed)
            adapter.requestRedrawVisible()
        }
    }

    suspend fun updateSelection(position: Int) {
        withContext(Dispatchers.Main) {
            adapter.updateSelection(position)
            adapter.requestRedrawVisible()
        }
    }

    suspend fun requestRedrawVisible() {
        withContext(Dispatchers.Main) {
            adapter.requestRedrawVisible()
        }
    }

    suspend fun showModeSwitchTips() {
        withContext(Dispatchers.Main) {
            android.widget.Toast.makeText(context, R.string.mode_switch_tips, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun collapseAllGroups() {
        adapter.collapseAll()
    }

    fun clearAllFilters() {
        adapter.clearAllFilters()
    }

    private fun requestUrlTesting(index: Int) {
        adapter.setUrlTesting(index, true)
        requests.trySend(Request.UrlTest(index))
    }

    private fun showPinDialog(index: Int, proxy: Proxy, fixed: Boolean) {
        val actions = if (fixed) {
            arrayOf(context.getString(R.string.proxy_unfix_action))
        } else {
            arrayOf(context.getString(R.string.proxy_fix_action))
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(proxy.title.ifBlank { proxy.name })
            .setItems(actions) { _, actionIndex ->
                when {
                    fixed && actionIndex == 0 -> requests.trySend(Request.Unfix(index))
                    !fixed && actionIndex == 0 -> requests.trySend(Request.Pin(index, proxy.name))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    init {
        binding.self = this

        binding.activityBarLayout.applyFrom(context)
        binding.menuView.setOnClickListener {
            menu.show()
        }

        binding.groupsView.apply {
            layoutManager = GridLayoutManager(context, 6).apply {
                spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        return this@ProxyDesign.adapter.getSpanSize(position)
                    }
                }
            }

            adapter = this@ProxyDesign.adapter

            val toolbarHeight = context.getPixels(R.dimen.toolbar_height)
            bindInsets(surface, toolbarHeight * 2)

            val lastGroup = uiStore.proxyLastGroup
            if (lastGroup.isNotBlank()) {
                this.post {
                    this@ProxyDesign.adapter.findHeaderPosition(lastGroup)?.let { position ->
                        scrollToPosition(position)
                    }
                }
            }
        }

        if (groupNames.isEmpty()) {
            binding.emptyView.visibility = View.VISIBLE
            binding.groupsView.visibility = View.GONE
            binding.collapseBarView.visibility = View.GONE
            binding.elevationView.visibility = View.GONE
        }
    }
}
