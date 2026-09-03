package com.mdiwebma.leetzsche.view

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

class DragSortRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    fun interface DropListener {
        fun drop(from: Int, to: Int)
    }

    fun interface RemoveListener {
        fun remove(which: Int)
    }

    var dropListener: DropListener? = null
    var removeListener: RemoveListener? = null
    var isDragEnabled: Boolean = false

    private var dragStartIndex: Int = -1
    private var dragEndIndex: Int = -1

    private val itemTouchHelperCallback = object : ItemTouchHelper.Callback() {
        override fun isLongPressDragEnabled(): Boolean = isDragEnabled
        override fun isItemViewSwipeEnabled(): Boolean = false

        override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: ViewHolder): Int {
            val dragFlags = if (isDragEnabled) ItemTouchHelper.UP or ItemTouchHelper.DOWN else 0
            val swipeFlags = 0
            return makeMovementFlags(dragFlags, swipeFlags)
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: ViewHolder,
            target: ViewHolder
        ): Boolean {
            if (!isDragEnabled) return false
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition

            val adapter = recyclerView.adapter as? SimpleRecyclerAdapter<*>
            adapter?.swapItems(from, to)
            dragEndIndex = to
            return true
        }

        override fun onSwiped(viewHolder: ViewHolder, direction: Int) {
            val position = viewHolder.bindingAdapterPosition
            removeListener?.remove(position)
        }

        override fun onSelectedChanged(viewHolder: ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                dragStartIndex = viewHolder.bindingAdapterPosition
                dragEndIndex = dragStartIndex
            } else if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
                if (dragStartIndex != -1 && dragEndIndex != -1 && dragStartIndex != dragEndIndex) {
                    dropListener?.drop(dragStartIndex, dragEndIndex)
                }
                dragStartIndex = -1
                dragEndIndex = -1
            }
        }
    }

    private val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)

    init {
        itemTouchHelper.attachToRecyclerView(this)
    }

    fun startDrag(viewHolder: ViewHolder) {
        if (isDragEnabled) {
            itemTouchHelper.startDrag(viewHolder)
        }
    }
}
