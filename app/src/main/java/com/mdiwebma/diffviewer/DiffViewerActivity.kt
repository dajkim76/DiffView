package com.mdiwebma.diffviewer

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.mdiwebma.diffviewer.box.AppBoxStore
import com.mdiwebma.diffviewer.box.DiffEntity
import com.mdiwebma.diffviewer.box.DiffEntity_
import com.mdiwebma.diffviewer.box.DiffGroupEntity
import com.mdiwebma.diffviewer.box.DiffGroupEntity_
import com.mdiwebma.diffviewer.databinding.ActivityDiffViewerBinding
import com.mdiwebma.diffviewer.databinding.ItemDiffGroupBinding
import com.mdiwebma.diffviewer.databinding.ItemTabHeaderBinding
import com.mdiwebma.diffviewer.dialog.ConfirmDeleteDialog
import com.mdiwebma.diffviewer.dialog.DiffTabsDialog
import com.mdiwebma.diffviewer.dialog.RenameDialog
import com.mdiwebma.diffviewer.dialog.ReviewStatusDialog
import com.mdiwebma.diffviewer.model.ReviewStatus
import com.mdiwebma.diffviewer.ui.AddDiffFragment
import com.mdiwebma.diffviewer.ui.DiffPageFragment
import com.mdiwebma.diffviewer.ui.EnterDiffGroupFragment
import com.mdiwebma.diffviewer.ui.SettingsFragment
import com.mdiwebma.diffviewer.view.SimpleRecyclerAdapter
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DiffViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDiffViewerBinding
    private lateinit var diffGroupAdapter: SimpleRecyclerAdapter<DiffGroupEntity>

    private val diffGroupBox: Box<DiffGroupEntity> by lazy {
        AppBoxStore.getInstance(applicationContext).getBox()
    }
    private val diffBox: Box<DiffEntity> by lazy {
        AppBoxStore.getInstance(applicationContext).getBox()
    }

    private var currentGroup: DiffGroupEntity? = null
    private var currentDiffs: MutableList<DiffEntity> = mutableListOf()
    private var pagerAdapter: DiffPagerAdapter? = null
    private var tabMediator: TabLayoutMediator? = null
    private var loadCurrentGroupJob: Job? = null
    private var loadDiffGroupsJob: Job? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MyApp.setAppContext(this)
        enableEdgeToEdge()
        binding = ActivityDiffViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.drawerLayout) { _, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomInset = maxOf(systemBars.bottom, ime.bottom)
            binding.mainContentPane.setPadding(systemBars.left, systemBars.top, systemBars.right, bottomInset)
            binding.drawerPane.setPadding(0, systemBars.top, 0, systemBars.bottom)
            binding.fragmentContainer.setPadding(systemBars.left, systemBars.top, systemBars.right, bottomInset)
            insets
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_menu)

        setupDrawerRecyclerView()

        binding.viewPager2.isUserInputEnabled = false
        binding.viewPager2.offscreenPageLimit = ViewPager2.OFFSCREEN_PAGE_LIMIT_DEFAULT
        binding.viewPager2.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val diff = currentDiffs.getOrNull(position)
                val group = currentGroup
                if (group != null && diff != null && group.diffId != diff.id) {
                    group.diffId = diff.id
                    lifecycleScope.launch(Dispatchers.IO) {
                        diffGroupBox.put(group)
                    }
                }
            }
        })

        pagerAdapter = DiffPagerAdapter(this)
        binding.viewPager2.adapter = pagerAdapter

        tabMediator = TabLayoutMediator(binding.tabLayout, binding.viewPager2, true, false) { tab, position ->
            val tabBinding = ItemTabHeaderBinding.inflate(layoutInflater)
            tab.customView = tabBinding.root
            updateTabHeaderView(tabBinding, position)
        }
        tabMediator?.attach()

        binding.btnAddDiff.setOnClickListener {
            showAddDiffFragment()
        }

        supportFragmentManager.addOnBackStackChangedListener {
            val hasFragment = supportFragmentManager.backStackEntryCount > 0
            binding.fragmentContainer.visibility = if (hasFragment) View.VISIBLE else View.GONE
        }

        loadDiffGroups()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_diff_viewer, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                binding.drawerLayout.openDrawer(GravityCompat.START)
                true
            }

            R.id.action_new_group -> {
                showEnterDiffGroupFragment()
                true
            }

            R.id.action_tabs_list -> {
                showDiffTabsDialog()
                true
            }

            R.id.action_settings -> {
                showSettingsFragment()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupDrawerRecyclerView() {
        binding.rvDiffGroups.layoutManager = LinearLayoutManager(this)

        diffGroupAdapter = SimpleRecyclerAdapter(this, R.layout.item_diff_group) { itemView ->
            DiffGroupViewHolder(itemView)
        }

        diffGroupAdapter.onItemClickListener = { item, _ ->
            AppSettings.diffGroupId.value = item.id
            diffGroupAdapter.notifyDataSetChanged()
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            loadCurrentGroup(item.id)
        }

        binding.rvDiffGroups.adapter = diffGroupAdapter

        val divider = DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        ContextCompat.getDrawable(this, R.drawable.divider_list)?.let {
            divider.setDrawable(it)
        }
        binding.rvDiffGroups.addItemDecoration(divider)
    }

    inner class DiffGroupViewHolder(itemView: View) :
        SimpleRecyclerAdapter.SimpleViewHolder<DiffGroupEntity>(itemView) {

        private val itemBinding = ItemDiffGroupBinding.bind(itemView)

        override fun onBind(item: DiffGroupEntity) {
            val titleBuilder = SpannableStringBuilder(item.title)
            val countText = " (${item.diffCount})"
            val start = titleBuilder.length
            titleBuilder.append(countText)
            val end = titleBuilder.length

            titleBuilder.setSpan(RelativeSizeSpan(0.8f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val countColor = ContextCompat.getColor(this@DiffViewerActivity, R.color.button_text_color)
            titleBuilder.setSpan(ForegroundColorSpan(countColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            itemBinding.tvGroupTitle.text = titleBuilder

            val isFavorite = item.favoriteTime > 0L
            itemBinding.ivFavorite.setImageResource(
                if (isFavorite) R.drawable.ic_star else R.drawable.ic_star_border
            )

            itemBinding.ivFavorite.setOnClickListener {
                item.favoriteTime = if (item.favoriteTime > 0L) 0L else System.currentTimeMillis()
                diffGroupBox.put(item)
                loadDiffGroups()
            }

            itemBinding.btnMore.setOnClickListener { v ->
                showGroupItemMenu(v, item, bindingAdapterPosition)
            }

            val isSelected = item.id == AppSettings.diffGroupId.value
            itemBinding.llItemGroupRoot.isActivated = isSelected
        }
    }

    private fun showGroupItemMenu(anchor: View, item: DiffGroupEntity, position: Int) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_diff_group_item, popup.menu)
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_rename -> {
                    showRenameDialog(item, position)
                    true
                }

                R.id.menu_favorite -> {
                    item.favoriteTime = if (item.favoriteTime > 0L) 0L else System.currentTimeMillis()
                    diffGroupBox.put(item)
                    loadDiffGroups()
                    true
                }

                R.id.menu_delete -> {
                    showDeleteConfirmDialog(item)
                    true
                }

                else -> false
            }
        }
        popup.show()
    }

    private fun showRenameDialog(item: DiffGroupEntity, position: Int) {
        RenameDialog.show(this, R.string.title_rename_group, item.title) { newTitle ->
            item.title = newTitle
            item.updatedTime = System.currentTimeMillis()
            diffGroupBox.put(item)
            diffGroupAdapter.notifyItemChanged(position)
            if (currentGroup?.id == item.id) {
                supportActionBar?.title = newTitle
            }
        }
    }

    private fun showDeleteConfirmDialog(item: DiffGroupEntity) {
        ConfirmDeleteDialog.show(this, title = item.title, messageRes = R.string.msg_confirm_delete_group) {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    diffBox.query(DiffEntity_.historyId.equal(item.id)).build().use { it.remove() }
                    diffGroupBox.remove(item)
                }

                if (AppSettings.diffGroupId.value == item.id) {
                    AppSettings.diffGroupId.value = 0L
                }
                loadDiffGroups()
            }
        }
    }

    private fun loadDiffGroups() {
        loadDiffGroupsJob?.cancel()
        loadDiffGroupsJob = lifecycleScope.launch {
            val groups = withContext(Dispatchers.IO) {
                diffGroupBox.query()
                    .orderDesc(DiffGroupEntity_.favoriteTime)
                    .orderDesc(DiffGroupEntity_.createdTime)
                    .build()
                    .find()
            }
            diffGroupAdapter.clear()
            diffGroupAdapter.addAll(groups)

            binding.tvEmptyGroups.visibility = if (groups.isEmpty()) View.VISIBLE else View.GONE

            var targetId = AppSettings.diffGroupId.value
            if (targetId == 0L && groups.isNotEmpty()) {
                targetId = groups[0].id
                AppSettings.diffGroupId.value = targetId
            }

            diffGroupAdapter.notifyDataSetChanged()
            loadCurrentGroup(targetId)
        }
    }

    private fun loadCurrentGroup(groupId: Long, onLoaded: (() -> Unit)? = null) {
        loadCurrentGroupJob?.cancel()
        if (groupId == 0L) {
            currentGroup = null
            updateDiffList(emptyList())
            onLoaded?.invoke()
            return
        }

        loadCurrentGroupJob = lifecycleScope.launch {
            val group = withContext(Dispatchers.IO) { diffGroupBox.get(groupId) }
            val diffs = withContext(Dispatchers.IO) {
                diffBox.query(DiffEntity_.historyId.equal(groupId))
                    .orderDesc(DiffEntity_.favoriteTime)
                    .order(DiffEntity_.createdTime)
                    .build()
                    .find()
            }

            currentGroup = group
            updateDiffList(diffs)

            if (diffs.isNotEmpty()) {
                val targetDiffId = group?.diffId ?: 0L
                val targetIndex = if (targetDiffId != 0L) {
                    diffs.indexOfFirst { it.id == targetDiffId }.takeIf { it != -1 } ?: 0
                } else {
                    0
                }
                binding.viewPager2.setCurrentItem(targetIndex, false)
                binding.tabLayout.getTabAt(targetIndex)?.select()
            }

            onLoaded?.invoke()
        }
    }

    private fun updateDiffList(newDiffs: List<DiffEntity>) {
        val diffCallback = object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = currentDiffs.size
            override fun getNewListSize(): Int = newDiffs.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return currentDiffs[oldItemPosition].id == newDiffs[newItemPosition].id
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val old = currentDiffs[oldItemPosition]
                val new = newDiffs[newItemPosition]
                return old.title == new.title &&
                        old.filePath == new.filePath &&
                        old.reviewStatus == new.reviewStatus &&
                        old.loadingStatus == new.loadingStatus &&
                        old.updatedTime == new.updatedTime &&
                        old.originalText == new.originalText &&
                        old.modifiedText == new.modifiedText
            }
        }
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        currentDiffs.clear()
        currentDiffs.addAll(newDiffs)
        pagerAdapter?.let { diffResult.dispatchUpdatesTo(it) }

        updateEmptyLayout()
    }

    private fun updateEmptyLayout() {
        if (currentDiffs.isEmpty()) {
            binding.tvEmptyDiffs.visibility = View.VISIBLE
            binding.viewPager2.visibility = View.GONE
        } else {
            binding.tvEmptyDiffs.visibility = View.GONE
            binding.viewPager2.visibility = View.VISIBLE
        }

        supportActionBar?.title = currentGroup?.title ?: getString(R.string.app_name)
    }

    private fun showAddDiffFragment() {
        val group = currentGroup
        if (group == null) {
            Toast.makeText(this, R.string.msg_no_diff_groups, Toast.LENGTH_SHORT).show()
            showEnterDiffGroupFragment()
            return
        }

        val fragment = AddDiffFragment.newInstance()
        fragment.onDiffCreatedListener = { title, before, after, beforeTitle, afterTitle ->
            lifecycleScope.launch {
                val newDiff = DiffEntity(
                    historyId = group.id,
                    title = title.ifBlank { "Diff ${currentDiffs.size + 1}" },
                    originalText = before,
                    modifiedText = after,
                    originalName = beforeTitle.takeIf { it.isNotBlank() } ?: getString(com.mdiwebma.diffview.R.string.diffview_header_original),
                    modifiedName = afterTitle.takeIf { it.isNotBlank() } ?: getString(com.mdiwebma.diffview.R.string.diffview_header_modified),
                    loadingStatus = 1
                )

                withContext(Dispatchers.IO) {
                    diffBox.put(newDiff)
                    group.diffId = newDiff.id
                    group.diffCount = group.diffs.size
                    group.updatedTime = System.currentTimeMillis()
                    diffGroupBox.put(group)
                }

                updateGroupItemInAdapter(group)

                val insertIndex = currentDiffs.size
                currentDiffs.add(newDiff)
                pagerAdapter?.notifyItemInserted(insertIndex)
                updateEmptyLayout()

                binding.viewPager2.setCurrentItem(insertIndex, false)
                binding.tabLayout.post {
                    val tab = binding.tabLayout.getTabAt(insertIndex)
                    tab?.select()
                    binding.tabLayout.setScrollPosition(insertIndex, 0f, true)
                }
            }
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun showDiffTabsDialog() {
        var tabsDialog: DiffTabsDialog? = null
        tabsDialog = DiffTabsDialog(
            context = this,
            currentGroup = currentGroup,
            getDiffs = { currentDiffs },
            getSelectedPosition = { binding.viewPager2.currentItem },
            onTabSelected = { position ->
                binding.viewPager2.setCurrentItem(position, false)
                binding.tabLayout.getTabAt(position)?.select()
            },
            onToggleFavorite = { item, _ ->
                val wasFavorite = item.favoriteTime > 0L
                item.favoriteTime = if (wasFavorite) 0L else System.currentTimeMillis()
                diffBox.put(item)
                val currentGroupId = currentGroup?.id ?: 0L
                if (currentGroupId != 0L) {
                    val currentSelectedDiffId = currentDiffs.getOrNull(binding.viewPager2.currentItem)?.id
                    loadCurrentGroup(currentGroupId) {
                        tabsDialog?.updateList()
                        if (currentSelectedDiffId != null) {
                            val newIndex = currentDiffs.indexOfFirst { it.id == currentSelectedDiffId }
                            if (newIndex != -1) {
                                binding.viewPager2.setCurrentItem(newIndex, false)
                            }
                        }
                    }
                }
            },
            onRename = { item, position ->
                RenameDialog.show(this, R.string.title_rename_diff, item.title) { newTitle ->
                    item.title = newTitle
                    item.updatedTime = System.currentTimeMillis()
                    diffBox.put(item)
                    tabsDialog?.notifyItemChanged(position)
                    // Update tab icon, text
                    val tab = binding.tabLayout.getTabAt(position)
                    val customView = tab?.customView
                    if (customView != null) {
                        val tabBinding = ItemTabHeaderBinding.bind(customView)
                        updateTabHeaderView(tabBinding, position)
                    }
                }
            },
            onDelete = { item, _ ->
                ConfirmDeleteDialog.show(this, title = item.title, messageRes = R.string.msg_confirm_delete_diff) {
                    diffBox.remove(item)
                    val group = currentGroup
                    if (group != null) {
                        group.diffCount = group.diffs.size
                        group.updatedTime = System.currentTimeMillis()
                        diffGroupBox.put(group)
                        updateGroupItemInAdapter(group)
                        loadCurrentGroup(group.id) {
                            tabsDialog?.updateList()
                        }
                    } else {
                        tabsDialog?.updateList()
                    }
                }
            },
            onStatusClick = { item, pos ->
                val status = ReviewStatus.fromDbValue(item.reviewStatus)
                ReviewStatusDialog.show(this@DiffViewerActivity, item.title, status) { newStatus ->
                    if (status != newStatus) {
                        updateDiffReviewStatus(item, newStatus)
                        tabsDialog?.notifyItemChanged(pos)
                    }
                }
            }
        )
        tabsDialog.show()
    }

    private fun updateTabHeaderView(tabBinding: ItemTabHeaderBinding, position: Int) {
        val diff = currentDiffs.getOrNull(position) ?: return
        val status = ReviewStatus.fromDbValue(diff.reviewStatus)
        tabBinding.tvTabStatusIcon.text = status.emoji
        val title = diff.title
        tabBinding.tvTabTitle.text = title.ifBlank { "Diff ${position + 1}" }
        tabBinding.tvTabStatusIcon.setOnClickListener {
            ReviewStatusDialog.show(this@DiffViewerActivity, diff.title, status) { newStatus ->
                if (status != newStatus) {
                    updateDiffReviewStatus(diff, newStatus)
                }
            }
        }
    }

    private fun updateDiffReviewStatus(diff: DiffEntity, newStatus: ReviewStatus) {
        diff.reviewStatus = newStatus.dbValue
        diffBox.put(diff)
        val index = currentDiffs.indexOfFirst { it.id == diff.id }
        if (index != -1) {
            currentDiffs[index].reviewStatus = newStatus.dbValue
            val tab = binding.tabLayout.getTabAt(index)
            val customView = tab?.customView
            if (customView != null) {
                val tabBinding = ItemTabHeaderBinding.bind(customView)
                updateTabHeaderView(tabBinding, index)
            }
        }
    }

    private fun updateGroupItemInAdapter(group: DiffGroupEntity) {
        val index = diffGroupAdapter.itemList.indexOfFirst { it.id == group.id }
        if (index != -1) {
            val item = diffGroupAdapter.getItem(index)
            if (item != null) {
                item.diffCount = group.diffCount
                item.updatedTime = group.updatedTime
                diffGroupAdapter.notifyItemChanged(index)
            }
        }
    }

    private fun showEnterDiffGroupFragment() {
        val fragment = EnterDiffGroupFragment.newInstance()
        fragment.onGroupCreatedListener = { newGroupId ->
            loadDiffGroups()
            loadCurrentGroup(newGroupId)
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun showSettingsFragment() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, SettingsFragment.newInstance())
            .addToBackStack(null)
            .commit()
    }

    inner class DiffPagerAdapter(activity: AppCompatActivity) :
        FragmentStateAdapter(activity) {

        override fun getItemCount(): Int = currentDiffs.size

        override fun createFragment(position: Int): Fragment {
            val groupId = currentGroup?.id ?: 0L
            val diffId = currentDiffs.getOrNull(position)?.id ?: 0L
            return DiffPageFragment.newInstance(diffId, groupId)
        }

        override fun getItemId(position: Int): Long {
            return currentDiffs.getOrNull(position)?.id ?: position.toLong()
        }

        override fun containsItem(itemId: Long): Boolean {
            return currentDiffs.any { it.id == itemId }
        }
    }
}
