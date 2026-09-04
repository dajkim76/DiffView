package com.mdiwebma.diffviewer

import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
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
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.mdiwebma.diffview.SyntaxHighlighter
import com.mdiwebma.diffviewer.box.AppBoxStore
import com.mdiwebma.diffviewer.box.DiffEntity
import com.mdiwebma.diffviewer.box.DiffEntity_
import com.mdiwebma.diffviewer.box.DiffGroupEntity
import com.mdiwebma.diffviewer.box.DiffGroupEntity_
import com.mdiwebma.diffviewer.databinding.ActivityDiffViewerBinding
import com.mdiwebma.diffviewer.databinding.ItemDiffGroupBinding
import com.mdiwebma.diffviewer.dialog.ConfirmDeleteDialog
import com.mdiwebma.diffviewer.dialog.DiffTabsDialog
import com.mdiwebma.diffviewer.dialog.RenameDialog
import com.mdiwebma.diffviewer.ui.AddDiffFragment
import com.mdiwebma.diffviewer.ui.DiffPageFragment
import com.mdiwebma.diffviewer.ui.EnterDiffGroupFragment
import com.mdiwebma.diffviewer.ui.SettingsFragment
import com.mdiwebma.diffviewer.utils.GithubUtils
import com.mdiwebma.leetzsche.view.SimpleRecyclerAdapter
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
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



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        binding.viewPager2.offscreenPageLimit = 1
        binding.viewPager2.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateFilePathHeader(position)
            }
        })

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
            itemBinding.tvGroupTitle.text = item.title

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
            diffBox.query(DiffEntity_.historyId.equal(item.id)).build().use { it.remove() }
            diffGroupBox.remove(item)

            if (AppSettings.diffGroupId.value == item.id) {
                AppSettings.diffGroupId.value = 0L
            }
            loadDiffGroups()
        }
    }

    private fun loadDiffGroups() {
        val groups = diffGroupBox.query()
            .orderDesc(DiffGroupEntity_.favoriteTime)
            .orderDesc(DiffGroupEntity_.createdTime)
            .build()
            .find()
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

    private fun loadCurrentGroup(groupId: Long) {
        if (groupId == 0L) {
            currentGroup = null
            currentDiffs.clear()
            renderDiffPages()
            return
        }

        currentGroup = diffGroupBox.get(groupId)
        currentDiffs = diffBox.query(DiffEntity_.historyId.equal(groupId))
            .orderDesc(DiffEntity_.favoriteTime)
            .order(DiffEntity_.createdTime)
            .build()
            .find()

        renderDiffPages()
    }

    private fun renderDiffPages() {
        tabMediator?.detach()
        tabMediator = null
        binding.tabLayout.removeAllTabs()

        if (currentDiffs.isEmpty()) {
            binding.viewPager2.adapter = null
            pagerAdapter = null
            binding.tvEmptyDiffs.visibility = View.VISIBLE
            binding.viewPager2.visibility = View.GONE
            binding.tvFilePathHeader.visibility = View.GONE
            supportActionBar?.title = currentGroup?.title ?: getString(R.string.app_name)
            return
        }

        binding.tvEmptyDiffs.visibility = View.GONE
        binding.viewPager2.visibility = View.VISIBLE
        supportActionBar?.title = currentGroup?.title ?: getString(R.string.app_name)

        pagerAdapter = DiffPagerAdapter(this)
        binding.viewPager2.adapter = pagerAdapter

        tabMediator = TabLayoutMediator(binding.tabLayout, binding.viewPager2, true, false) { tab, position ->
            val diff = currentDiffs.getOrNull(position)
            val fullTitle = diff?.title.orEmpty()
            val isPathType = currentGroup?.type == DiffGroupEntity.TYPE_COMMIT_URL ||
                    currentGroup?.type == DiffGroupEntity.TYPE_GIT_PATCH
            val displayName = if (isPathType) {
                fullTitle.substringAfterLast('/')
            } else {
                fullTitle
            }
            tab.text = displayName.ifBlank { "Diff ${position + 1}" }
        }
        tabMediator?.attach()

        updateFilePathHeader(binding.viewPager2.currentItem)
    }

    private fun updateFilePathHeader(position: Int) {
        val isPathType = currentGroup?.type == DiffGroupEntity.TYPE_COMMIT_URL ||
                currentGroup?.type == DiffGroupEntity.TYPE_GIT_PATCH
        val diff = currentDiffs.getOrNull(position)
        if (isPathType && diff != null && diff.title.isNotBlank()) {
            binding.tvFilePathHeader.text = diff.title
            binding.tvFilePathHeader.visibility = View.VISIBLE
        } else {
            binding.tvFilePathHeader.visibility = View.GONE
        }
    }

    private fun showAddDiffFragment() {
        val group = currentGroup
        if (group == null) {
            Toast.makeText(this, R.string.msg_no_diff_groups, Toast.LENGTH_SHORT).show()
            showEnterDiffGroupFragment()
            return
        }

        val fragment = AddDiffFragment.newInstance()
        fragment.onDiffCreatedListener = { title, before, after ->
            val newDiff = DiffEntity(
                historyId = group.id,
                title = title.ifBlank { "Diff ${currentDiffs.size + 1}" },
                originalText = before,
                modifiedText = after,
                status = 1
            )
            diffBox.put(newDiff)

            group.diffCount++
            group.updatedTime = System.currentTimeMillis()
            diffGroupBox.put(group)

            loadCurrentGroup(group.id)
            binding.viewPager2.setCurrentItem(currentDiffs.size - 1, true)
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
                updateFilePathHeader(position)
            },
            onToggleFavorite = { item, _ ->
                val wasFavorite = item.favoriteTime > 0L
                item.favoriteTime = if (wasFavorite) 0L else System.currentTimeMillis()
                diffBox.put(item)
                val currentGroupId = currentGroup?.id ?: 0L
                if (currentGroupId != 0L) {
                    val currentSelectedDiffId = currentDiffs.getOrNull(binding.viewPager2.currentItem)?.id
                    loadCurrentGroup(currentGroupId)
                    tabsDialog?.updateList()
                    if (currentSelectedDiffId != null) {
                        val newIndex = currentDiffs.indexOfFirst { it.id == currentSelectedDiffId }
                        if (newIndex != -1) {
                            binding.viewPager2.setCurrentItem(newIndex, false)
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
                    renderDiffPages()
                }
            },
            onDelete = { item, _ ->
                ConfirmDeleteDialog.show(this, title = item.title, messageRes = R.string.msg_confirm_delete_diff) {
                    diffBox.remove(item)
                    val group = currentGroup
                    if (group != null) {
                        group.diffCount = maxOf(0, group.diffCount - 1)
                        group.updatedTime = System.currentTimeMillis()
                        diffGroupBox.put(group)
                        diffGroupAdapter.notifyDataSetChanged()
                        loadCurrentGroup(group.id)
                    }
                    tabsDialog?.updateList()
                }
            }
        )
        tabsDialog.show()
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
            return DiffPageFragment.newInstance(currentDiffs[position].id, groupId)
        }

        override fun getItemId(position: Int): Long {
            return currentDiffs[position].id
        }

        override fun containsItem(itemId: Long): Boolean {
            return currentDiffs.any { it.id == itemId }
        }
    }
}
