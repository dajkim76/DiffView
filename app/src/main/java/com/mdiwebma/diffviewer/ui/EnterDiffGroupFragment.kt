package com.mdiwebma.diffviewer.ui

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.mdiwebma.diffview.engine.GitPatchParser
import com.mdiwebma.diffviewer.AppSettings
import com.mdiwebma.diffviewer.R
import com.mdiwebma.diffviewer.box.AppBoxStore
import com.mdiwebma.diffviewer.box.DiffEntity
import com.mdiwebma.diffviewer.box.DiffGroupEntity
import com.mdiwebma.diffviewer.databinding.FragmentEnterDiffGroupBinding
import com.mdiwebma.diffviewer.utils.GithubUtils
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EnterDiffGroupFragment : Fragment() {

    private var _binding: FragmentEnterDiffGroupBinding? = null
    private val binding get() = _binding!!

    var onGroupCreatedListener: ((groupId: Long) -> Unit)? = null

    private fun readFileContent(uri: Uri): String? {
        return requireContext().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    }

    private fun updateTitleWithFileName(uri: Uri) {
        if (binding.etGroupTitle.text.isNullOrBlank()) {
            val fileName = queryFileName(uri)
            if (!fileName.isNullOrBlank()) {
                binding.etGroupTitle.setText(fileName)
            }
        }
    }

    private fun showErrorToast(e: Exception) {
        e.printStackTrace()
        context?.let { ctx ->
            Toast.makeText(
                ctx,
                getString(R.string.msg_file_load_error, e.message ?: ""),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun checkTextFile(uri: Uri): Boolean {
        val fileName = queryFileName(uri) ?: return true
        if (GithubUtils.isBinaryFile(fileName)) {
            context?.let { ctx ->
                Toast.makeText(
                    ctx,
                    getString(R.string.msg_binary_file_not_supported, fileName),
                    Toast.LENGTH_SHORT
                ).show()
            }
            return false
        }
        return true
    }

    private val pickSimpleBeforeLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        if (!checkTextFile(uri)) return@registerForActivityResult
        try {
            val text = readFileContent(uri)
            if (text != null) {
                binding.etSimpleBefore.setText(text)
                updateTitleWithFileName(uri)
            }
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }

    private val pickSimpleAfterLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        if (!checkTextFile(uri)) return@registerForActivityResult
        try {
            val text = readFileContent(uri)
            if (text != null) {
                binding.etSimpleAfter.setText(text)
                updateTitleWithFileName(uri)
            }
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }

    private val pickSimpleTwoFilesLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        val validUris = uris.filter { checkTextFile(it) }
        if (validUris.size < 2) {
            context?.let { Toast.makeText(it, R.string.msg_need_two_files, Toast.LENGTH_SHORT).show() }
            if (validUris.size == 1) {
                try {
                    val text = readFileContent(validUris[0])
                    if (text != null) {
                        binding.etSimpleBefore.setText(text)
                        updateTitleWithFileName(validUris[0])
                    }
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }
            return@registerForActivityResult
        }
        try {
            val beforeText = readFileContent(validUris[0])
            val afterText = readFileContent(validUris[1])
            if (beforeText != null) binding.etSimpleBefore.setText(beforeText)
            if (afterText != null) binding.etSimpleAfter.setText(afterText)
            if (binding.etGroupTitle.text.isNullOrBlank()) {
                val name1 = queryFileName(validUris[0]) ?: "File1"
                val name2 = queryFileName(validUris[1]) ?: "File2"
                binding.etGroupTitle.setText("$name1 vs $name2")
            }
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }

    private val pickPatchFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        if (!checkTextFile(uri)) return@registerForActivityResult
        try {
            val content = readFileContent(uri)
            if (content != null) {
                binding.etGitPatch.setText(content)
                updateTitleWithFileName(uri)
            }
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEnterDiffGroupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.toolbar.inflateMenu(R.menu.menu_enter_diff_group)
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.action_compare) {
                handleCompare()
                true
            } else {
                false
            }
        }

        binding.rgType.setOnCheckedChangeListener { _, checkedId ->
            binding.llSimpleLayout.visibility = if (checkedId == R.id.rbSimple) View.VISIBLE else View.GONE
            binding.llCommitUrlLayout.visibility = if (checkedId == R.id.rbCommitUrl) View.VISIBLE else View.GONE
            binding.llGitPatchLayout.visibility = if (checkedId == R.id.rbGitPatch) View.VISIBLE else View.GONE
        }

        binding.btnPickBeforeFile.setOnClickListener {
            pickSimpleBeforeLauncher.launch("*/*")
        }

        binding.btnPickAfterFile.setOnClickListener {
            pickSimpleAfterLauncher.launch("*/*")
        }

        binding.btnPickTwoFilesSimple.setOnClickListener {
            pickSimpleTwoFilesLauncher.launch("*/*")
        }

        binding.btnPickPatchFile.setOnClickListener {
            pickPatchFileLauncher.launch("*/*")
        }

        setupEditTextScrolls()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupEditTextScrolls() {
        val touchListener = View.OnTouchListener { v, event ->
            if (v.hasFocus()) {
                v.parent.requestDisallowInterceptTouchEvent(true)
                if ((event.action and MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP) {
                    v.parent.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }
        binding.etSimpleBefore.setOnTouchListener(touchListener)
        binding.etSimpleAfter.setOnTouchListener(touchListener)
        binding.etGitPatch.setOnTouchListener(touchListener)
    }

    private fun handleCompare() {
        val titleInput = binding.etGroupTitle.text.toString().trim()
        val title = if (titleInput.isEmpty()) {
            val defaultTitle = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            binding.etGroupTitle.setText(defaultTitle)
            defaultTitle
        } else {
            titleInput
        }

        val appBoxStore = AppBoxStore.Companion.getInstance(requireContext().applicationContext)
        val diffGroupBox: Box<DiffGroupEntity> = appBoxStore.getBox()
        val diffBox: Box<DiffEntity> = appBoxStore.getBox()

        when (binding.rgType.checkedRadioButtonId) {
            R.id.rbSimple -> {
                val before = binding.etSimpleBefore.text.toString()
                val after = binding.etSimpleAfter.text.toString()

                if (before.isEmpty() && after.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.msg_input_required, Toast.LENGTH_SHORT).show()
                    return
                }

                setLoading(true)
                lifecycleScope.launch {
                    val groupId = withContext(Dispatchers.IO) {
                        val group = DiffGroupEntity(
                            title = title,
                            type = DiffGroupEntity.Companion.TYPE_TEXT,
                            diffCount = 1
                        )
                        diffGroupBox.put(group)

                        val diff = DiffEntity(
                            historyId = group.id,
                            title = title,
                            originalText = before,
                            modifiedText = after
                        )
                        diffBox.put(diff)
                        group.id
                    }
                    setLoading(false)
                    onSuccess(groupId)
                }
            }

            R.id.rbCommitUrl -> {
                val url = binding.etCommitUrl.text.toString().trim()
                val parsed = GithubUtils.parseCommitUrl(url)
                if (parsed == null) {
                    Toast.makeText(requireContext(), R.string.msg_invalid_commit_url, Toast.LENGTH_SHORT).show()
                    return
                }

                setLoading(true)
                lifecycleScope.launch {
                    try {
                        val detail = GithubUtils.fetchCommit(requireContext().cacheDir, parsed)
                        val commitTitle = detail.commitMessage?.lines()?.firstOrNull()?.trim()
                        val finalTitle = when {
                            titleInput.isNotEmpty() -> titleInput
                            !commitTitle.isNullOrBlank() -> {
                                binding.etGroupTitle.setText(commitTitle)
                                commitTitle
                            }

                            else -> title
                        }

                        val groupId = withContext(Dispatchers.IO) {
                            val group = DiffGroupEntity(
                                title = finalTitle,
                                type = DiffGroupEntity.Companion.TYPE_COMMIT_URL,
                                commitUrl = url,
                                diffCount = detail.files.size
                            )
                            diffGroupBox.put(group)

                            val diffEntities = detail.files.map { file ->
                                DiffEntity(
                                    historyId = group.id,
                                    title = file.filename,
                                    originalText = "",
                                    modifiedText = "",
                                    status = 0
                                )
                            }
                            if (diffEntities.isNotEmpty()) {
                                diffBox.put(diffEntities)
                            }
                            group.id
                        }

                        onSuccess(groupId)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        val errorMsg = e.message ?: ""
                        context?.let { ctx ->
                            Toast.makeText(
                                ctx,
                                ctx.getString(R.string.msg_failed_fetch_commit, errorMsg),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } finally {
                        setLoading(false)
                    }
                }
            }

            R.id.rbGitPatch -> {
                val patch = binding.etGitPatch.text.toString()
                if (patch.isBlank()) {
                    Toast.makeText(requireContext(), R.string.msg_input_required, Toast.LENGTH_SHORT).show()
                    return
                }

                setLoading(true)
                lifecycleScope.launch {
                    val groupId = withContext(Dispatchers.IO) {
                        val parsedFiles = GitPatchParser.parse(patch)
                        if (parsedFiles.isEmpty()) {
                            val group = DiffGroupEntity(
                                title = title,
                                type = DiffGroupEntity.Companion.TYPE_GIT_PATCH,
                                data = patch,
                                diffCount = 1
                            )
                            diffGroupBox.put(group)

                            val diff = DiffEntity(
                                historyId = group.id,
                                title = title,
                                originalText = patch,
                                modifiedText = "",
                                status = 1
                            )
                            diffBox.put(diff)
                            group.id
                        } else {
                            val group = DiffGroupEntity(
                                title = title,
                                type = DiffGroupEntity.Companion.TYPE_GIT_PATCH,
                                data = patch,
                                diffCount = parsedFiles.size
                            )
                            diffGroupBox.put(group)

                            val diffEntities = parsedFiles.mapIndexed { index, p ->
                                val fileName = p.modifiedFileName ?: p.originalFileName ?: "File ${index + 1}"
                                DiffEntity(
                                    historyId = group.id,
                                    title = fileName,
                                    originalName = p.originalFileName ?: "Original",
                                    modifiedName = p.modifiedFileName ?: "Modified",
                                    originalText = p.originalText,
                                    modifiedText = p.modifiedText,
                                    status = 1
                                )
                            }
                            diffBox.put(diffEntities)
                            group.id
                        }
                    }
                    setLoading(false)
                    onSuccess(groupId)
                }
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.toolbar.menu.findItem(R.id.action_compare)?.isEnabled = !loading
    }

    private fun onSuccess(groupId: Long) {
        AppSettings.diffGroupId.value = groupId
        onGroupCreatedListener?.invoke(groupId)
        parentFragmentManager.popBackStack()
    }

    private fun queryFileName(uri: Uri): String? {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            val cursor = requireContext().contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        return it.getString(index)
                    }
                }
            }
        }
        return uri.lastPathSegment
    }

    override fun onDestroyView() {
        super.onDestroyView()
        onGroupCreatedListener = null
        _binding = null
    }

    companion object {
        fun newInstance(): EnterDiffGroupFragment = EnterDiffGroupFragment()
    }
}