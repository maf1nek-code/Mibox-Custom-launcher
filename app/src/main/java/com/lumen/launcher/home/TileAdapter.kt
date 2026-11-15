package com.lumen.launcher.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.lumen.launcher.App
import com.lumen.launcher.R
import com.lumen.launcher.data.AppEntry

class TileAdapter(private val host: Host, private val inDock: Boolean) :
    RecyclerView.Adapter<TileAdapter.Holder>() {

    interface Host {
        val tileW: Int
        val tileH: Int
        val tileStyle: TileStyle
        val movingPkg: String?
        /** Dock tiles are smaller than grid tiles; the two fixed buttons are square. */
        val dockW: Int
        val dockH: Int
        fun onTileFocused(entry: AppEntry, tile: TileView, inDock: Boolean, position: Int)
        fun onTileClick(entry: AppEntry, tile: TileView)
        fun onTileLongClick(entry: AppEntry, tile: TileView, inDock: Boolean)
    }

    class Holder(val tile: TileView) : RecyclerView.ViewHolder(tile)

    var items: List<AppEntry> = emptyList()
        private set

    init {
        setHasStableIds(true)
    }

    fun submit(new: List<AppEntry>) {
        val old = items
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = new.size
            override fun areItemsTheSame(o: Int, n: Int) = old[o].pkg == new[n].pkg
            override fun areContentsTheSame(o: Int, n: Int) =
                old[o].version == new[n].version && old[o].label == new[n].label
        }, true)
        items = new
        diff.dispatchUpdatesTo(this)
    }

    override fun getItemCount() = items.size
    override fun getItemId(position: Int) = items[position].pkg.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val tile = LayoutInflater.from(parent.context).inflate(R.layout.item_tile, parent, false) as TileView
        val holder = Holder(tile)
        tile.setOnClickListener { tile.entry?.let { host.onTileClick(it, tile) } }
        tile.setOnLongClickListener {
            tile.entry?.let { host.onTileLongClick(it, tile, inDock) }
            true
        }
        tile.onFocus = { gained ->
            if (gained) tile.entry?.let { host.onTileFocused(it, tile, inDock, holder.bindingAdapterPosition) }
        }
        return holder
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = items[position]
        val tile = holder.tile
        tile.entry = entry
        tile.contentDescription = entry.label
        tile.label.text = entry.label

        val artW = when {
            !inDock -> host.tileW
            entry.isInternal -> host.dockH
            else -> host.dockW
        }
        val artH = if (inDock) host.dockH else host.tileH
        val wantWidth = if (inDock) artW else ViewGroup.LayoutParams.MATCH_PARENT
        val lp = tile.layoutParams
        if (lp.width != wantWidth) {
            lp.width = wantWidth
            tile.layoutParams = lp
        }
        val cardLp = tile.card.layoutParams
        if (cardLp.height != artH) {
            cardLp.height = artH
            tile.card.layoutParams = cardLp
        }
        tile.configure(host.tileStyle, !inDock)
        tile.animate().cancel()
        tile.translationY = 0f
        tile.alpha = if (host.movingPkg != null && entry.pkg != host.movingPkg) 0.4f else 1f
        App.tiles.bind(tile.art, entry, artW, artH)
    }
}
