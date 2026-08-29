package com.example.splitdiff.diffui

import android.content.Context
import android.util.AttributeSet
import android.widget.HorizontalScrollView
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.max

/**
 * 특정 그룹(Left, Right, Unified)에 속한 모든 HorizontalScrollView의
 * 가로 스크롤 위치(scrollX) 및 최대 콘텐츠 너비(Max Content Width)를
 * 실시간으로 완벽하게 동기화하는 매니저.
 */
class HorizontalScrollSyncGroup {

    var currentScrollX: Int = 0
        private set

    var maxContentWidth: Int = 0
        private set

    private val views = Collections.newSetFromMap(WeakHashMap<SyncHorizontalScrollView, Boolean>())
    private var isDispatching = false

    fun register(view: SyncHorizontalScrollView) {
        views.add(view)
        view.applyContentMinWidth(maxContentWidth)
        view.scrollTo(currentScrollX, 0)
    }

    fun unregister(view: SyncHorizontalScrollView) {
        views.remove(view)
    }

    /**
     * 그룹의 최대 콘텐츠 너비를 갱신하고, 등록된 모든 뷰의 너비를 확장합니다.
     */
    fun reportContentWidth(width: Int) {
        if (width > maxContentWidth) {
            maxContentWidth = width
            for (v in views) {
                v.applyContentMinWidth(maxContentWidth)
            }
        }
    }

    fun onScrollChanged(source: SyncHorizontalScrollView, scrollX: Int) {
        if (isDispatching) return
        currentScrollX = scrollX
        isDispatching = true
        try {
            for (v in views) {
                if (v !== source && v.scrollX != scrollX) {
                    v.scrollTo(scrollX, 0)
                }
            }
        } finally {
            isDispatching = false
        }
    }

    fun reset() {
        currentScrollX = 0
        maxContentWidth = 0
        isDispatching = true
        try {
            for (v in views) {
                v.applyContentMinWidth(0)
                v.scrollTo(0, 0)
            }
        } finally {
            isDispatching = false
        }
    }
}

/**
 * [HorizontalScrollSyncGroup]과 연동되어 짧은 라인을 잡고 드래그해도
 * 전체 뷰 단위로 매끄럽게 가로 스크롤되는 커스텀 HorizontalScrollView.
 */
class SyncHorizontalScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    var syncGroup: HorizontalScrollSyncGroup? = null
        set(value) {
            if (field !== value) {
                field?.unregister(this)
                field = value
                value?.register(this)
            } else {
                value?.let {
                    applyContentMinWidth(it.maxContentWidth)
                    scrollTo(it.currentScrollX, 0)
                }
            }
        }

    fun applyContentMinWidth(minWidth: Int) {
        if (childCount > 0) {
            val child = getChildAt(0)
            if (child.minimumWidth != minWidth) {
                child.minimumWidth = minWidth
                child.requestLayout()
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxWidth = syncGroup?.maxContentWidth ?: 0
        if (childCount > 0) {
            val child = getChildAt(0)
            if (maxWidth > 0 && child.minimumWidth != maxWidth) {
                child.minimumWidth = maxWidth
            } else if (maxWidth == 0 && child.minimumWidth != 0) {
                child.minimumWidth = 0
            }
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        if (childCount > 0) {
            val child = getChildAt(0)
            val childMeasuredWidth = child.measuredWidth
            syncGroup?.reportContentWidth(childMeasuredWidth)
        }
    }

    override fun computeHorizontalScrollRange(): Int {
        val superRange = super.computeHorizontalScrollRange()
        val groupMax = syncGroup?.maxContentWidth ?: 0
        return max(superRange, groupMax)
    }

    override fun canScrollHorizontally(direction: Int): Boolean {
        val range = computeHorizontalScrollRange()
        val extent = computeHorizontalScrollExtent()
        val offset = computeHorizontalScrollOffset()
        val maxScroll = max(0, range - extent)
        return if (direction < 0) {
            offset > 0
        } else {
            offset < maxScroll
        }
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        syncGroup?.onScrollChanged(this, l)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        syncGroup?.register(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        syncGroup?.unregister(this)
    }
}
