package com.github.kr328.clash.design.adapter

import android.content.Context
import android.widget.EditText
import android.view.ViewGroup
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.component.ProxyView
import com.github.kr328.clash.design.component.ProxyViewConfig
import com.github.kr328.clash.design.component.ProxyViewState
import com.github.kr328.clash.design.databinding.AdapterProxyGroupBinding
import com.github.kr328.clash.design.model.ProxyState
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.util.invalidateChildren
import com.github.kr328.clash.design.util.layoutInflater
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

class ProxyGroupAdapter(
    private val context: Context,
    private val config: ProxyViewConfig,
    private val uiStore: UiStore,
    groupNames: List<String>,
    private val groupInteracted: (Int) -> Unit,
    private val clicked: (Int, String) -> Unit,
    private val longClicked: (Int, Proxy, Boolean) -> Unit,
    private val urlTestClicked: (Int) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private data class GroupState(
        val name: String,
        val fixed: ProxyState = ProxyState(""),
        var type: Proxy.Type = Proxy.Type.Unknown,
        var parent: ProxyState = ProxyState("?"),
        var allProxies: List<Proxy> = emptyList(),
        var links: Map<String, ProxyState> = emptyMap(),
        var proxies: List<ProxyViewState> = emptyList(),
        var filterKeyword: String = "",
        var selectable: Boolean = false,
        var fixable: Boolean = false,
        var expanded: Boolean = true,
        var urlTesting: Boolean = false,
        var lastParentNow: String = "?",
        var lastFixedNow: String = "",
    )

    private sealed class Item {
        data class Header(val groupIndex: Int) : Item()
        data class ProxyNode(val groupIndex: Int, val proxyIndex: Int) : Item()
    }

    class GroupHolder(val binding: AdapterProxyGroupBinding) : RecyclerView.ViewHolder(binding.root)
    class ProxyHolder(val view: ProxyView) : RecyclerView.ViewHolder(view)

    private val groups = groupNames.map {
        GroupState(
            name = it,
            filterKeyword = uiStore.getProxyGroupFilter(it),
            expanded = uiStore.isProxyGroupExpanded(it),
        )
    }
    private var items = rebuildItems()
    private var recyclerView: RecyclerView? = null

    fun findHeaderPosition(groupName: String): Int? {
        val groupIndex = groups.indexOfFirst { it.name == groupName }
        if (groupIndex < 0) return null

        return findHeaderPosition(items, groupIndex)
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is Item.Header -> VIEW_TYPE_GROUP
            is Item.ProxyNode -> VIEW_TYPE_PROXY
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_GROUP -> GroupHolder(
                AdapterProxyGroupBinding.inflate(
                    context.layoutInflater,
                    parent,
                    false
                )
            )
            VIEW_TYPE_PROXY -> ProxyHolder(ProxyView(context, config))
            else -> error("unknown view type $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is Item.Header -> bindGroupHolder(holder as GroupHolder, item.groupIndex)
            is Item.ProxyNode -> bindProxyHolder(holder as ProxyHolder, item.groupIndex, item.proxyIndex)
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
        recyclerView.isFocusable = false
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = null
    }

    fun getSpanSize(position: Int): Int {
        return if (getItemViewType(position) == VIEW_TYPE_GROUP) {
            FULL_SPAN
        } else {
            when (config.proxyLine) {
                1 -> FULL_SPAN
                2 -> 3
                else -> 2
            }
        }
    }

    fun updateGroup(
        position: Int,
        type: Proxy.Type,
        proxies: List<Proxy>,
        parent: ProxyState,
        links: Map<String, ProxyState>,
        fixed: String,
    ) {
        val group = groups[position]
        val oldProxyCount = group.proxies.size
        val oldExpanded = group.expanded
        val oldType = group.type
        val oldAllProxies = group.allProxies
        val oldLinks = group.links
        val oldSelectable = group.selectable
        val oldFixable = group.fixable
        val oldUrlTesting = group.urlTesting
        val oldParentNow = group.lastParentNow
        val oldFixedNow = group.lastFixedNow

        group.type = type
        group.parent = parent
        group.fixed.now = fixed
        group.selectable = type == Proxy.Type.Selector
        group.fixable = type == Proxy.Type.URLTest || type == Proxy.Type.Fallback
        group.urlTesting = false
        group.allProxies = proxies
        group.links = links
        group.lastParentNow = parent.now
        group.lastFixedNow = fixed

        val shouldRefresh = oldType != group.type ||
            oldAllProxies != group.allProxies ||
            oldLinks != group.links ||
            oldSelectable != group.selectable ||
            oldFixable != group.fixable ||
            oldUrlTesting != group.urlTesting ||
            oldParentNow != group.lastParentNow ||
            oldFixedNow != group.lastFixedNow

        if (!shouldRefresh) {
            return
        }

        rebuildGroupProxies(group)
        notifyGroupContentChanged(position, oldProxyCount, group.proxies.size, oldExpanded && group.expanded)
    }

    fun updateSelection(position: Int) {
        if (position !in groups.indices) {
            return
        }

        notifyGroupHeaderChanged(position)
    }

    fun setUrlTesting(position: Int, urlTesting: Boolean) {
        if (position !in groups.indices) {
            return
        }

        groups[position].urlTesting = urlTesting
        notifyGroupHeaderChanged(position)
    }

    fun visibleGroupIndices(): IntArray {
        val recyclerView = recyclerView ?: return IntArray(0)
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return IntArray(0)
        val first = layoutManager.findFirstVisibleItemPosition()
        val last = layoutManager.findLastVisibleItemPosition()

        if (first == RecyclerView.NO_POSITION || last == RecyclerView.NO_POSITION || items.isEmpty()) {
            return IntArray(0)
        }

        val from = first.coerceIn(0, items.lastIndex)
        val to = last.coerceIn(0, items.lastIndex)
        if (from > to) {
            return IntArray(0)
        }

        val groupIndices = LinkedHashSet<Int>()
        for (position in from..to) {
            when (val item = items[position]) {
                is Item.Header -> groupIndices.add(item.groupIndex)
                is Item.ProxyNode -> groupIndices.add(item.groupIndex)
            }
        }

        return groupIndices.toIntArray()
    }

    fun collapseAll() {
        if (groups.none { it.expanded }) {
            return
        }

        groups.forEach {
            it.expanded = false
            uiStore.setProxyGroupExpanded(it.name, false)
        }

        rebuildAndNotify()
    }

    fun clearAllFilters() {
        if (groups.none { it.filterKeyword.isNotBlank() }) {
            return
        }

        uiStore.clearAllProxyGroupFilters()

        groups.forEach { group ->
            group.filterKeyword = ""
            rebuildGroupProxies(group)
        }

        rebuildAndNotify()
    }

    fun requestRedrawVisible() {
        recyclerView?.invalidateChildren()
    }

    fun updateConfig() {
        notifyDataSetChanged()
    }

    private fun bindGroupHolder(holder: GroupHolder, groupIndex: Int) {
        val group = groups[groupIndex]
        val binding = holder.binding

        binding.groupTitleView.text = group.name
        binding.groupSubtitleView.text = buildGroupSubtitle(group)

        binding.urlTestView.visibility = if (group.urlTesting) {
            View.GONE
        } else {
            View.VISIBLE
        }
        binding.urlTestProgressView.visibility = if (group.urlTesting) {
            View.VISIBLE
        } else {
            View.GONE
        }
        binding.clearFilterView.visibility = if (group.filterKeyword.isBlank()) {
            View.GONE
        } else {
            View.VISIBLE
        }
        binding.expandView.rotation = if (group.expanded) {
            270f
        } else {
            180f
        }

        binding.root.setOnClickListener {
            groupInteracted(groupIndex)
            group.expanded = !group.expanded
            uiStore.setProxyGroupExpanded(group.name, group.expanded)
            rebuildAndNotify()
        }

        binding.urlTestView.setOnClickListener {
            groupInteracted(groupIndex)
            urlTestClicked(groupIndex)
        }
        binding.filterView.setOnClickListener {
            groupInteracted(groupIndex)
            showFilterDialog(groupIndex)
        }
        binding.clearFilterView.setOnClickListener {
            groupInteracted(groupIndex)
            clearGroupFilter(groupIndex)
        }
    }

    private fun bindProxyHolder(holder: ProxyHolder, groupIndex: Int, proxyIndex: Int) {
        val group = groups[groupIndex]
        val current = group.proxies[proxyIndex]

        holder.view.apply {
            state = current

            if (group.selectable) {
                setOnClickListener {
                    groupInteracted(groupIndex)
                    clicked(groupIndex, current.proxy.name)
                }
            } else {
                setOnClickListener(null)
            }

            if (group.fixable) {
                setOnLongClickListener {
                    groupInteracted(groupIndex)
                    longClicked(groupIndex, current.proxy, group.fixed.now == current.proxy.name)
                    true
                }
            } else {
                setOnLongClickListener(null)
            }

            isFocusable = group.selectable || group.fixable
            isClickable = group.selectable
            isLongClickable = group.fixable

            current.update(true)
        }
    }

    private fun buildGroupSubtitle(group: GroupState): String {
        val parts = mutableListOf(group.type.name)

        if (group.parent.now.isNotBlank()) {
            parts.add(group.parent.now)
        }

        if (group.fixed.now.isNotBlank()) {
            parts.add("${context.getString(R.string.proxy_fixed_badge)}: ${group.fixed.now}")
        }

        if (group.filterKeyword.isNotBlank()) {
            parts.add(
                context.getString(
                    R.string.proxy_group_filter_summary,
                    group.filterKeyword,
                    group.proxies.size,
                    group.allProxies.size
                )
            )
        }

        return parts.joinToString(separator = " · ")
    }

    private fun rebuildGroupProxies(group: GroupState) {
        val keyword = group.filterKeyword.trim()
        val normalizedKeyword = keyword.lowercase(Locale.ROOT)
        val filtered = if (normalizedKeyword.isBlank()) {
            group.allProxies
        } else {
            group.allProxies.filter { proxy ->
                proxy.name.lowercase(Locale.ROOT).contains(normalizedKeyword) ||
                    proxy.title.lowercase(Locale.ROOT).contains(normalizedKeyword) ||
                    proxy.subtitle.lowercase(Locale.ROOT).contains(normalizedKeyword)
            }
        }

        group.proxies = filtered.map { proxy ->
            val link = if (proxy.type.group) group.links[proxy.name] else null
            ProxyViewState(config, proxy, group.parent, link, group.fixed)
        }
    }

    private fun showFilterDialog(groupIndex: Int) {
        val group = groups[groupIndex]
        val input = EditText(context).apply {
            setText(group.filterKeyword)
            setHint(R.string.proxy_group_filter_hint)
            setSelection(text.length)
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.proxy_group_filter_title, group.name))
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                applyGroupFilter(groupIndex, input.text?.toString().orEmpty())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun applyGroupFilter(groupIndex: Int, keyword: String) {
        val group = groups[groupIndex]
        val trimmed = keyword.trim()

        if (trimmed == group.filterKeyword) {
            return
        }

        groupInteracted(groupIndex)
        group.filterKeyword = trimmed
        uiStore.setProxyGroupFilter(group.name, trimmed)
        val oldProxyCount = group.proxies.size
        val oldExpanded = group.expanded
        rebuildGroupProxies(group)
        notifyGroupContentChanged(groupIndex, oldProxyCount, group.proxies.size, oldExpanded && group.expanded)
    }

    private fun clearGroupFilter(groupIndex: Int) {
        val group = groups[groupIndex]
        if (group.filterKeyword.isBlank()) {
            return
        }

        groupInteracted(groupIndex)
        val oldProxyCount = group.proxies.size
        val oldExpanded = group.expanded
        group.filterKeyword = ""
        uiStore.setProxyGroupFilter(group.name, "")
        rebuildGroupProxies(group)
        notifyGroupContentChanged(groupIndex, oldProxyCount, group.proxies.size, oldExpanded && group.expanded)
    }

    private fun rebuildAndNotify() {
        items = rebuildItems()
        notifyDataSetChanged()
    }

    private fun notifyGroupContentChanged(
        groupIndex: Int,
        oldProxyCount: Int,
        newProxyCount: Int,
        expanded: Boolean,
    ) {
        val oldItems = items
        val oldHeader = findHeaderPosition(oldItems, groupIndex) ?: run {
            rebuildAndNotify()
            return
        }

        items = rebuildItems()

        val newHeader = findHeaderPosition(items, groupIndex) ?: run {
            rebuildAndNotify()
            return
        }

        if (oldHeader != newHeader) {
            notifyDataSetChanged()
            return
        }

        notifyItemChanged(newHeader)

        if (!expanded) {
            return
        }

        val changed = minOf(oldProxyCount, newProxyCount)
        if (changed > 0) {
            notifyItemRangeChanged(newHeader + 1, changed)
        }

        when {
            newProxyCount > oldProxyCount ->
                notifyItemRangeInserted(newHeader + 1 + oldProxyCount, newProxyCount - oldProxyCount)
            newProxyCount < oldProxyCount ->
                notifyItemRangeRemoved(newHeader + 1 + newProxyCount, oldProxyCount - newProxyCount)
        }
    }

    private fun notifyGroupHeaderChanged(groupIndex: Int) {
        findHeaderPosition(items, groupIndex)?.let(::notifyItemChanged)
    }

    private fun findHeaderPosition(items: List<Item>, groupIndex: Int): Int? {
        val position = items.indexOfFirst { item ->
            item is Item.Header && item.groupIndex == groupIndex
        }

        return position.takeIf { it >= 0 }
    }

    private fun rebuildItems(): List<Item> {
        return buildList {
            groups.forEachIndexed { index, group ->
                add(Item.Header(index))

                if (group.expanded) {
                    repeat(group.proxies.size) { proxyIndex ->
                        add(Item.ProxyNode(index, proxyIndex))
                    }
                }
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_GROUP = 0
        private const val VIEW_TYPE_PROXY = 1
        private const val FULL_SPAN = 6
    }
}
