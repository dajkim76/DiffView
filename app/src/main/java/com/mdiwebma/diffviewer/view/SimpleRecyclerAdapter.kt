package com.mdiwebma.diffviewer.view

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import java.util.Collections

class SimpleRecyclerAdapter<T>(
    private val mContext: Context,
    private val itemLayoutId: Int,
    private val viewHolderCreator: (itemView: View) -> SimpleViewHolder<T>
) : RecyclerView.Adapter<SimpleRecyclerAdapter.SimpleViewHolder<T>>() {

    private val layoutInflater: LayoutInflater = LayoutInflater.from(mContext)
    protected val mObjects: MutableList<T> = ArrayList()
    //private val mLock = Any() // 메인쓰레드에서 호출하는 것이 올바른 동작이므로 lock은 의마가 없다.

    var onItemClickListener: ((item: T, position: Int) -> Unit)? = null
    var onItemLongClickListener: ((item: T, position: Int) -> Boolean)? = null
    var autoNotifyChanged = false //true로 하면 불필요한 notify가 생길 수 있어서 notify는 데이타 변경후 직접 호출한다.

    override fun getItemCount(): Int {
        return mObjects.size
    }

    val count: Int
        get() = mObjects.size

    val itemList: List<T>
        get() = mObjects

    fun getItem(position: Int): T? {
        return if (position in 0 until mObjects.size) {
            mObjects[position]
        } else {
            null
        }
    }

    fun setItem(position: Int, item: T): Boolean {
        if (position in 0 until mObjects.size) {
            mObjects[position] = item
            if (autoNotifyChanged) notifyItemChanged(position)
            return true
        }
        return false
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SimpleViewHolder<T> {
        val itemView = layoutInflater.inflate(itemLayoutId, parent, false)
        val viewHolder = viewHolderCreator.invoke(itemView)
        return viewHolder
    }

    override fun onBindViewHolder(viewHolder: SimpleViewHolder<T>, position: Int) {
        val item = getItem(position)
        if (item != null) {
            viewHolder.onBind(item)
            if (onItemClickListener != null) {
                viewHolder.itemView.setOnClickListener {
                    onItemClickListener?.invoke(item, viewHolder.bindingAdapterPosition)
                }
            }
            if (onItemLongClickListener != null) {
                viewHolder.itemView.setOnLongClickListener {
                    onItemLongClickListener?.invoke(item, viewHolder.bindingAdapterPosition) ?: false
                }
            }
        }
    }

    fun getPosition(item: T): Int {
        return mObjects.indexOf(item)
    }

    fun add(objectItem: T) {
        mObjects.add(objectItem)
        if (autoNotifyChanged) notifyItemInserted(mObjects.size - 1)
    }

    fun addAll(collection: Collection<T>) {
        if (collection.isEmpty()) return
        val start = mObjects.size
        mObjects.addAll(collection)
        if (autoNotifyChanged) notifyItemRangeInserted(start, collection.size)
    }

    fun insert(objectItem: T, index: Int) {
        mObjects.add(index, objectItem)
        if (autoNotifyChanged) notifyItemInserted(index)
    }

    fun remove(objectItem: T) {
        val index = mObjects.indexOf(objectItem)
        if (index != -1) {
            mObjects.removeAt(index)
            if (autoNotifyChanged) notifyItemRemoved(index)
        }
    }

    fun clear() {
        val size = mObjects.size
        if (size == 0) return
        mObjects.clear()
        if (autoNotifyChanged) notifyItemRangeRemoved(0, size)
    }

    fun swapItems(fromPosition: Int, toPosition: Int) {
        if (fromPosition == toPosition) return
        if (fromPosition !in 0 until mObjects.size || toPosition !in 0 until mObjects.size) return
        
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(mObjects, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(mObjects, i, i - 1)
            }
        }
        if (autoNotifyChanged) notifyItemMoved(fromPosition, toPosition)
    }


    abstract class SimpleViewHolder<T>(itemView: View) : RecyclerView.ViewHolder(itemView) {
        abstract fun onBind(item: T)
    }
}