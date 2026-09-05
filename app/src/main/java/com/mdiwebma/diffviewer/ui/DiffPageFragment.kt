package com.mdiwebma.diffviewer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.mdiwebma.diffview.SyntaxHighlighter
import com.mdiwebma.diffview.TextNormalizer
import com.mdiwebma.diffviewer.R
import com.mdiwebma.diffviewer.box.AppBoxStore
import com.mdiwebma.diffviewer.box.DiffEntity
import com.mdiwebma.diffviewer.box.DiffGroupEntity
import com.mdiwebma.diffviewer.databinding.ItemDiffPageBinding
import com.mdiwebma.diffviewer.utils.GithubUtils
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DiffPageFragment : Fragment() {

    private var _binding: ItemDiffPageBinding? = null
    private val binding get() = _binding!!

    private var diffId: Long = 0L
    private var diffGroupId: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        diffId = arguments?.getLong(ARG_DIFF_ID) ?: 0L
        diffGroupId = arguments?.getLong(ARG_DIFF_GROUP_ID) ?: 0L
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ItemDiffPageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val appBoxStore = AppBoxStore.getInstance(requireContext().applicationContext)
        val diffBox: Box<DiffEntity> = appBoxStore.getBox()
        val diffGroupBox: Box<DiffGroupEntity> = appBoxStore.getBox()

        val diff = diffBox.get(diffId) ?: return
        val targetGroupId = if (diffGroupId != 0L) diffGroupId else diff.historyId
        val group = diffGroupBox.get(targetGroupId)

        val diffView = binding.diffView
        diffView.loadPreferences()
        diffView.setAutoSavePreferences(true)
        diff.textNormalizerKey?.let { key ->
            diffView.setTextNormalizer(TextNormalizer.forKey(key))
        }
        diff.syntaxHighlighterKey?.let { key ->
            diffView.setSyntaxHighlighter(SyntaxHighlighter.forKey(key))
        } ?: run {
            diffView.setSyntaxHighlighter(SyntaxHighlighter.forFileName(diff.title))
        }
        diffView.setCommentContext(targetGroupId.toString(), diffId.toString())
        diffView.setOnTextNormalizerChangedListener { normalizer ->
            diff.textNormalizerKey = normalizer?.key
            diffBox.put(diff)
        }
        diffView.setOnSyntaxHighlighterChangedListener { highlighter ->
            diff.syntaxHighlighterKey = highlighter?.key
            diffBox.put(diff)
        }

        val hasContent = diff.status == 1 || diff.originalText.isNotEmpty() || diff.modifiedText.isNotEmpty()

        if (group?.type == DiffGroupEntity.TYPE_COMMIT_URL && !hasContent && group.commitUrl != null) {
            val parsed = GithubUtils.parseCommitUrl(group.commitUrl!!)
            if (parsed != null) {
                val fetchingMsg = getString(R.string.msg_fetching_file)
                diffView.setContent(rawOriginal = fetchingMsg, rawModified = fetchingMsg)

                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val commitDetail = GithubUtils.fetchCommit(requireContext().cacheDir, parsed)
                        val fileInfo = commitDetail.files.find { it.filename == diff.title }
                        if (fileInfo != null) {
                            val content = GithubUtils.fetchFileContent(
                                requireContext().cacheDir,
                                parsed,
                                fileInfo,
                                commitDetail.parentSha
                            )
                            diff.originalText = content.originalText
                            diff.modifiedText = content.modifiedText
                            diff.status = 1
                            diffBox.put(diff)

                            withContext(Dispatchers.Main) {
                                if (_binding != null) {
                                    diffView.setContent(
                                        rawOriginal = content.originalText,
                                        rawModified = content.modifiedText
                                    )
                                }
                            }
                        } else {
                            diff.status = 2
                            diffBox.put(diff)
                            val notFoundMsg = getString(R.string.msg_file_not_found_in_commit)
                            withContext(Dispatchers.Main) {
                                if (_binding != null) {
                                    diffView.setContent(rawOriginal = notFoundMsg, rawModified = "")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        diff.status = 2
                        diffBox.put(diff)
                        val errorMsg = getString(
                            R.string.msg_failed_to_fetch_file,
                            e.message ?: e.javaClass.simpleName
                        )
                        withContext(Dispatchers.Main) {
                            if (_binding != null) {
                                diffView.setContent(rawOriginal = errorMsg, rawModified = "")
                            }
                        }
                    }
                }
            } else {
                diffView.setContent(rawOriginal = diff.originalText, rawModified = diff.modifiedText)
            }
        } else {
            diffView.setContent(rawOriginal = diff.originalText, rawModified = diff.modifiedText)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_DIFF_ID = "arg_diff_id"
        private const val ARG_DIFF_GROUP_ID = "arg_diff_group_id"

        fun newInstance(diffId: Long, diffGroupId: Long = 0L): DiffPageFragment {
            return DiffPageFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_DIFF_ID, diffId)
                    putLong(ARG_DIFF_GROUP_ID, diffGroupId)
                }
            }
        }
    }
}
