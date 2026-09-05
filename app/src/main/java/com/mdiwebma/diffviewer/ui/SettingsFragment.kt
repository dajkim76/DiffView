package com.mdiwebma.diffviewer.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.mdiwebma.diffviewer.AppSettings
import com.mdiwebma.diffviewer.BuildConfig
import com.mdiwebma.diffviewer.MyApp
import com.mdiwebma.diffviewer.R
import com.mdiwebma.diffviewer.databinding.DialogSettingInputBinding
import com.mdiwebma.diffviewer.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        updateThemeDisplay()
        updateGithubTokenDisplay()

        binding.tvAppVersion.text = getString(R.string.setting_app_version, BuildConfig.VERSION_NAME)

        binding.layoutSettingTheme.setOnClickListener {
            showThemeSelectionDialog()
        }


        binding.layoutSettingGithubToken.setOnClickListener {
            showGithubTokenDialog()
        }

        binding.layoutSettingOpenSource.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/dajkim76/DiffView"))
            startActivity(intent)
        }


        binding.layoutSettingAppIconCredit.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.flaticon.com/free-icons/compare"))
            startActivity(intent)
        }

        binding.layoutSettingPlayStore.setOnClickListener {
            val appPackageName = requireContext().packageName
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$appPackageName")))
            } catch (e: ActivityNotFoundException) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName")))
            }
        }
    }

    private fun updateThemeDisplay() {
        val themeTextRes = when (AppSettings.appTheme.value) {
            AppSettings.THEME_LIGHT -> R.string.theme_light
            AppSettings.THEME_DARK -> R.string.theme_dark
            else -> R.string.theme_auto
        }
        binding.tvThemeValue.setText(themeTextRes)
    }

    private fun updateGithubTokenDisplay() {
        val token = AppSettings.githubApiKey.value
        if (token.isNullOrEmpty()) {
            binding.tvGithubTokenValue.setText(R.string.setting_not_set)
        } else {
            binding.tvGithubTokenValue.text = if (token.length > 8) {
                "${token.take(4)}••••${token.takeLast(4)}"
            } else {
                "••••••••"
            }
        }
    }

    private fun showThemeSelectionDialog() {
        val themes = arrayOf(
            getString(R.string.theme_auto),
            getString(R.string.theme_light),
            getString(R.string.theme_dark)
        )
        val themeValues = arrayOf(
            AppSettings.THEME_AUTO,
            AppSettings.THEME_LIGHT,
            AppSettings.THEME_DARK
        )

        val currentIndex = when (AppSettings.appTheme.value) {
            AppSettings.THEME_LIGHT -> 1
            AppSettings.THEME_DARK -> 2
            else -> 0
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.setting_app_theme)
            .setSingleChoiceItems(themes, currentIndex) { dialog, which ->
                val selectedTheme = themeValues[which]
                AppSettings.appTheme.value = selectedTheme
                MyApp.applyTheme(selectedTheme)
                updateThemeDisplay()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showGithubTokenDialog() {
        val dialogBinding = DialogSettingInputBinding.inflate(layoutInflater)
        val currentToken = AppSettings.githubApiKey.value.orEmpty()
        dialogBinding.etInput.setText(currentToken)
        dialogBinding.etInput.setSelection(currentToken.length)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.setting_github_token_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.btn_ok) { dialog, _ ->
                val newToken = dialogBinding.etInput.text.toString().trim()
                AppSettings.githubApiKey.value = newToken
                updateGithubTokenDisplay()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): SettingsFragment = SettingsFragment()
    }
}