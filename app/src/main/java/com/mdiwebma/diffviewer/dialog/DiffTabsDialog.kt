package com.mdiwebma.diffviewer.dialog

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mdiwebma.diffviewer.R
import com.mdiwebma.diffviewer.box.DiffEntity
import com.mdiwebma.diffviewer.box.DiffGroupEntity
import com.mdiwebma.diffviewer.databinding.DialogDiffTabsBinding
import com.mdiwebma.diffviewer.databinding.ItemDiffTabBinding
import com.mdiwebma.leetzsche.view.SimpleRecyclerAdapter

class DiffTabsDialog(
    private val context: Context,
    private val currentGroup: DiffGroupEntity?,
    private val getDiffs: () -> List<DiffEntity>,
    private val getSelectedPosition: () -> Int,
    private val onTabSelected: (position: Int) -> Unit,
    private val onToggleFavorite: (item: DiffEntity, position: Int) -> Unit,
    private val onRename: (item: DiffEntity, position: Int) -> Unit,
    private val onDelete: (item: DiffEntity, position: Int) -> Unit
) {

    private lateinit var dialog: AlertDialog
    private lateinit var binding: DialogDiffTabsBinding
    private lateinit var tabsAdapter: SimpleRecyclerAdapter<DiffEntity>

    fun show() {
        binding = DialogDiffTabsBinding.inflate(LayoutInflater.from(context))
        dialog = AlertDialog.Builder(context)
            .setTitle(R.string.title_tabs_list)
            .setView(binding.root)
            .setNegativeButton(R.string.btn_cancel, null)
            .create()

        tabsAdapter = SimpleRecyclerAdapter(context, R.layout.item_diff_tab) { itemView ->
            DiffTabViewHolder(itemView)
        }

        tabsAdapter.onItemClickListener = { item, position ->
            val currentList = getDiffs()
            val targetPos = if (position != RecyclerView.NO_POSITION) position else currentList.indexOfFirst { it.id == item.id }
            if (targetPos in 0 until currentList.size) {
                onTabSelected(targetPos)
            }
            dialog.dismiss()
        }

        binding.rvDiffTabs.layoutManager = LinearLayoutManager(context)
        binding.rvDiffTabs.adapter = tabsAdapter

        updateList()
        dialog.show()
    }

    fun updateList() {
        val currentList = getDiffs()
        tabsAdapter.clear()
        tabsAdapter.addAll(currentList)
        binding.tvEmptyTabs.visibility = if (currentList.isEmpty()) View.VISIBLE else View.GONE
        binding.rvDiffTabs.visibility = if (currentList.isEmpty()) View.GONE else View.VISIBLE
        tabsAdapter.notifyDataSetChanged()
    }

    fun notifyItemChanged(position: Int) {
        tabsAdapter.notifyItemChanged(position)
    }

    fun dismiss() {
        if (::dialog.isInitialized && dialog.isShowing) {
            dialog.dismiss()
        }
    }

    inner class DiffTabViewHolder(itemView: View) :
        SimpleRecyclerAdapter.SimpleViewHolder<DiffEntity>(itemView) {

        private val itemBinding = ItemDiffTabBinding.bind(itemView)

        override fun onBind(item: DiffEntity) {
            val isPathType = currentGroup?.type == DiffGroupEntity.TYPE_COMMIT_URL ||
                    currentGroup?.type == DiffGroupEntity.TYPE_GIT_PATCH
            val displayName = if (isPathType) {
                item.title.substringAfterLast('/')
            } else {
                item.title
            }
            itemBinding.tvTabTitle.text = displayName.ifBlank { "Diff ${bindingAdapterPosition + 1}" }

            val isFavorite = item.favoriteTime > 0L
            itemBinding.ivFavorite.setImageResource(
                if (isFavorite) R.drawable.ic_star else R.drawable.ic_star_border
            )

            itemBinding.ivFavorite.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onToggleFavorite(item, pos)
                }
            }

            val isSelected = bindingAdapterPosition == getSelectedPosition()
            itemBinding.llItemTabRoot.setBackgroundColor(
                if (isSelected) context.getColor(R.color.selected_item_bg) else Color.TRANSPARENT
            )

            itemBinding.btnDelete.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onDelete(item, pos)
                }
            }

            itemBinding.btnMore.setOnClickListener { v ->
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    showItemMenu(v, item, pos)
                }
            }
        }

        private fun showItemMenu(anchor: View, item: DiffEntity, position: Int) {
            val popup = PopupMenu(context, anchor)
            popup.menuInflater.inflate(R.menu.menu_diff_group_item, popup.menu)
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.menu_rename -> {
                        onRename(item, position)
                        true
                    }
                    R.id.menu_favorite -> {
                        onToggleFavorite(item, position)
                        true
                    }
                    R.id.menu_delete -> {
                        onDelete(item, position)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }
}
