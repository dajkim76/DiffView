package com.mdiwebma.leetzsche.view

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
    private val mLock = Any()

    var onItemClickListener: ((item: T, position: Int) -> Unit)? = null
    var onItemLongClickListener: ((item: T, position: Int) -> Boolean)? = null

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
            synchronized(mLock) {
                mObjects[position] = item
            }
            notifyItemChanged(position)
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
        synchronized(mLock) {
            mObjects.add(objectItem)
        }
        notifyItemInserted(mObjects.size - 1)
    }

    fun addAll(collection: Collection<T>) {
        val start = mObjects.size
        synchronized(mLock) {
            mObjects.addAll(collection)
        }
        notifyItemRangeInserted(start, collection.size)
    }

    fun insert(objectItem: T, index: Int) {
        synchronized(mLock) {
            mObjects.add(index, objectItem)
        }
        notifyItemInserted(index)
    }

    fun remove(objectItem: T) {
        val index = mObjects.indexOf(objectItem)
        if (index != -1) {
            synchronized(mLock) {
                mObjects.removeAt(index)
            }
            notifyItemRemoved(index)
        }
    }

    fun clear() {
        val size = mObjects.size
        synchronized(mLock) {
            mObjects.clear()
        }
        notifyItemRangeRemoved(0, size)
    }

    fun swapItems(fromPosition: Int, toPosition: Int) {
        synchronized(mLock) {
            if (fromPosition < toPosition) {
                for (i in fromPosition until toPosition) {
                    Collections.swap(mObjects, i, i + 1)
                }
            } else {
                for (i in fromPosition downTo toPosition + 1) {
                    Collections.swap(mObjects, i, i - 1)
                }
            }
        }
        notifyItemMoved(fromPosition, toPosition)
    }



    abstract class SimpleViewHolder<T>(itemView: View) : RecyclerView.ViewHolder(itemView) {
        abstract fun onBind(item: T)
    }
}
