package com.github.kr328.clash.design.adapter

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.component.ProxyView
import com.github.kr328.clash.design.component.ProxyViewConfig
import com.github.kr328.clash.design.component.ProxyViewState
import com.github.kr328.clash.design.databinding.AdapterProxyGroupBinding
import com.github.kr328.clash.design.model.ProxyState
import com.github.kr328.clash.design.util.invalidateChildren
import com.github.kr328.clash.design.util.layoutInflater

class ProxyGroupAdapter(
    private val context: Context,
    private val config: ProxyViewConfig,
    groupNames: List<String>,
    private val clicked: (Int, String) -> Unit,
    private val longClicked: (Int, Proxy, Boolean) -> Unit,
    private val urlTestClicked: (Int) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private data class GroupState(
        val name: String,
        val fixed: ProxyState = ProxyState(""),
        var type: Proxy.Type = Proxy.Type.Unknown,
        var parent: ProxyState = ProxyState("?"),
        var proxies: List<ProxyViewState> = emptyList(),
        var selectable: Boolean = false,
        var fixable: Boolean = false,
        var expanded: Boolean = true,
        var urlTesting: Boolean = false,
    )

    private sealed class Item {
        data class Header(val groupIndex: Int) : Item()
        data class ProxyNode(val groupIndex: Int, val proxyIndex: Int) : Item()
    }

    class GroupHolder(val binding: AdapterProxyGroupBinding) : RecyclerView.ViewHolder(binding.root)
    class ProxyHolder(val view: ProxyView) : RecyclerView.ViewHolder(view)

    private val groups = groupNames.map { GroupState(it) }
    private var items = rebuildItems()
    private var recyclerView: RecyclerView? = null

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

        group.type = type
        group.parent = parent
        group.fixed.now = fixed
        group.selectable = type == Proxy.Type.Selector
        group.fixable = type == Proxy.Type.URLTest || type == Proxy.Type.Fallback
        group.urlTesting = false
        group.proxies = proxies.map {
            val link = if (it.type.group) links[it.name] else null

            ProxyViewState(config, it, parent, link, group.fixed)
        }

        rebuildAndNotify()
    }

    fun updateSelection(position: Int) {
        if (position !in groups.indices) {
            return
        }

        notifyDataSetChanged()
    }

    fun setUrlTesting(position: Int, urlTesting: Boolean) {
        if (position !in groups.indices) {
            return
        }

        groups[position].urlTesting = urlTesting
        notifyDataSetChanged()
    }

    fun collapseAll() {
        if (groups.none { it.expanded }) {
            return
        }

        groups.forEach {
            it.expanded = false
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
            android.view.View.GONE
        } else {
            android.view.View.VISIBLE
        }
        binding.urlTestProgressView.visibility = if (group.urlTesting) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }
        binding.expandView.rotation = if (group.expanded) {
            270f
        } else {
            180f
        }

        binding.root.setOnClickListener {
            group.expanded = !group.expanded
            rebuildAndNotify()
        }

        binding.urlTestView.setOnClickListener {
            urlTestClicked(groupIndex)
        }
    }

    private fun bindProxyHolder(holder: ProxyHolder, groupIndex: Int, proxyIndex: Int) {
        val group = groups[groupIndex]
        val current = group.proxies[proxyIndex]

        holder.view.apply {
            state = current

            if (group.selectable) {
                setOnClickListener {
                    clicked(groupIndex, current.proxy.name)
                }
            } else {
                setOnClickListener(null)
            }

            if (group.fixable) {
                setOnLongClickListener {
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

        return parts.joinToString(separator = " · ")
    }

    private fun rebuildAndNotify() {
        items = rebuildItems()
        notifyDataSetChanged()
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
