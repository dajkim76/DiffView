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
import com.mdiwebma.diffviewer.R
import com.mdiwebma.diffviewer.databinding.FragmentAddDiffBinding
import com.mdiwebma.diffviewer.utils.GithubUtils

class AddDiffFragment : Fragment() {

    private var _binding: FragmentAddDiffBinding? = null
    private val binding get() = _binding!!

    var onDiffCreatedListener: ((title: String, before: String, after: String) -> Unit)? = null

    private fun readFileContent(uri: Uri): String? {
        return requireContext().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    }

    private fun queryFileName(uri: Uri): String? {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            val cursor = requireContext().contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) return it.getString(index)
                }
            }
        }
        return uri.lastPathSegment
    }

    private fun updateTitleWithFileName(uri: Uri) {
        if (binding.etDiffTitle.text.isNullOrBlank()) {
            val fileName = queryFileName(uri)
            if (!fileName.isNullOrBlank()) {
                binding.etDiffTitle.setText(fileName)
            }
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

    private val pickBeforeLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        if (!checkTextFile(uri)) return@registerForActivityResult
        try {
            val text = readFileContent(uri)
            if (text != null) {
                binding.etDiffBefore.setText(text)
                updateTitleWithFileName(uri)
            }
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }

    private val pickAfterLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        if (!checkTextFile(uri)) return@registerForActivityResult
        try {
            val text = readFileContent(uri)
            if (text != null) {
                binding.etDiffAfter.setText(text)
                updateTitleWithFileName(uri)
            }
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }

    private val pickTwoFilesLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        val validUris = uris.filter { checkTextFile(it) }
        if (validUris.size < 2) {
            context?.let { Toast.makeText(it, R.string.msg_need_two_files, Toast.LENGTH_SHORT).show() }
            if (validUris.size == 1) {
                try {
                    val text = readFileContent(validUris[0])
                    if (text != null) {
                        binding.etDiffBefore.setText(text)
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
            if (beforeText != null) binding.etDiffBefore.setText(beforeText)
            if (afterText != null) binding.etDiffAfter.setText(afterText)
            if (binding.etDiffTitle.text.isNullOrBlank()) {
                val name1 = queryFileName(validUris[0]) ?: "File1"
                val name2 = queryFileName(validUris[1]) ?: "File2"
                binding.etDiffTitle.setText("$name1 vs $name2")
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
        _binding = FragmentAddDiffBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.toolbar.inflateMenu(R.menu.menu_add_diff)
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_save) {
                handleSave()
                true
            } else {
                false
            }
        }

        binding.btnPickBeforeDiff.setOnClickListener {
            pickBeforeLauncher.launch("*/*")
        }

        binding.btnPickAfterDiff.setOnClickListener {
            pickAfterLauncher.launch("*/*")
        }

        binding.btnPickTwoFilesDiff.setOnClickListener {
            pickTwoFilesLauncher.launch("*/*")
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
        binding.etDiffBefore.setOnTouchListener(touchListener)
        binding.etDiffAfter.setOnTouchListener(touchListener)
    }

    private fun handleSave() {
        val title = binding.etDiffTitle.text.toString().trim()
        val before = binding.etDiffBefore.text.toString()
        val after = binding.etDiffAfter.text.toString()

        if (before.isEmpty() && after.isEmpty()) {
            Toast.makeText(requireContext(), R.string.msg_input_required, Toast.LENGTH_SHORT).show()
            return
        }

        onDiffCreatedListener?.invoke(title, before, after)
        parentFragmentManager.popBackStack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        onDiffCreatedListener = null
        _binding = null
    }

    companion object {
        fun newInstance(): AddDiffFragment {
            return AddDiffFragment()
        }
    }
}
