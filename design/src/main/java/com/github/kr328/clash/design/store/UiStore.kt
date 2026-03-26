package com.github.kr328.clash.design.store

import android.content.Context
import com.github.kr328.clash.common.store.Store
import com.github.kr328.clash.common.store.asStoreProvider
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.design.model.AppInfoSort
import com.github.kr328.clash.design.model.DarkMode
import org.json.JSONObject

class UiStore(context: Context) {
    private val store = Store(
        context
            .getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
            .asStoreProvider()
    )

    var enableVpn: Boolean by store.boolean(
        key = "enable_vpn",
        defaultValue = true
    )

    var darkMode: DarkMode by store.enum(
        key = "dark_mode",
        defaultValue = DarkMode.Auto,
        values = DarkMode.values()
    )

    var hideAppIcon: Boolean by store.boolean(
        key = "hide_app_icon",
        defaultValue = false
    )

    var hideFromRecents: Boolean by store.boolean(
        key = "hide_from_recents",
        defaultValue = false,
    )

    var proxyExcludeNotSelectable by store.boolean(
        key = "proxy_exclude_not_selectable",
        defaultValue = false,
    )

    var proxyLine: Int by store.int(
        key = "proxy_line",
        defaultValue = 2
    )

    var proxySort: ProxySort by store.enum(
        key = "proxy_sort",
        defaultValue = ProxySort.Default,
        values = ProxySort.values()
    )

    var proxyLastGroup: String by store.string(
        key = "proxy_last_group",
        defaultValue = ""
    )

    var proxyGlobalLastSelection: String by store.string(
        key = "proxy_global_last_selection",
        defaultValue = ""
    )

    private var proxyGroupFiltersRaw: String by store.string(
        key = "proxy_group_filters",
        defaultValue = "{}"
    )

    private var proxyGroupExpandedRaw: String by store.string(
        key = "proxy_group_expanded",
        defaultValue = "{}"
    )

    var accessControlSort: AppInfoSort by store.enum(
        key = "access_control_sort",
        defaultValue = AppInfoSort.Label,
        values = AppInfoSort.values(),
    )

    var accessControlReverse: Boolean by store.boolean(
        key = "access_control_reverse",
        defaultValue = false
    )

    var accessControlSystemApp: Boolean by store.boolean(
        key = "access_control_system_app",
        defaultValue = false,
    )

    fun getProxyGroupFilter(groupName: String): String {
        return readProxyGroupFilters()[groupName].orEmpty()
    }

    fun setProxyGroupFilter(groupName: String, keyword: String) {
        val filters = readProxyGroupFilters()
        val value = keyword.trim()

        if (value.isEmpty()) {
            filters.remove(groupName)
        } else {
            filters[groupName] = value
        }

        writeProxyGroupFilters(filters)
    }

    fun clearAllProxyGroupFilters() {
        proxyGroupFiltersRaw = "{}"
    }

    fun isProxyGroupExpanded(groupName: String): Boolean {
        return readProxyGroupExpandedStates()[groupName] ?: true
    }

    fun setProxyGroupExpanded(groupName: String, expanded: Boolean) {
        val states = readProxyGroupExpandedStates()

        if (expanded) {
            states.remove(groupName)
        } else {
            states[groupName] = false
        }

        writeProxyGroupExpandedStates(states)
    }

    private fun readProxyGroupFilters(): MutableMap<String, String> {
        return runCatching {
            val json = JSONObject(proxyGroupFiltersRaw)
            buildMap {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = json.optString(key, "").trim()

                    if (value.isNotEmpty()) {
                        put(key, value)
                    }
                }
            }.toMutableMap()
        }.getOrDefault(mutableMapOf())
    }

    private fun writeProxyGroupFilters(filters: Map<String, String>) {
        val json = JSONObject()
        filters.forEach { (name, keyword) ->
            if (keyword.isNotBlank()) {
                json.put(name, keyword)
            }
        }

        proxyGroupFiltersRaw = json.toString()
    }

    private fun readProxyGroupExpandedStates(): MutableMap<String, Boolean> {
        return runCatching {
            val json = JSONObject(proxyGroupExpandedRaw)
            buildMap {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (!json.optBoolean(key, true)) {
                        put(key, false)
                    }
                }
            }.toMutableMap()
        }.getOrDefault(mutableMapOf())
    }

    private fun writeProxyGroupExpandedStates(states: Map<String, Boolean>) {
        val json = JSONObject()
        states.forEach { (name, expanded) ->
            if (!expanded) {
                json.put(name, false)
            }
        }

        proxyGroupExpandedRaw = json.toString()
    }

    companion object {
        private const val PREFERENCE_NAME = "ui"
    }
}
