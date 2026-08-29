package com.mdiwebma.diffview_demo

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mdiwebma.diffview.DefaultKotlinSyntaxHighlighter
import com.mdiwebma.diffview.DiffView
import com.mdiwebma.diffview.model.DiffMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class MainActivity : AppCompatActivity() {

    private data class CommitUrlInfo(
        val owner: String,
        val repo: String,
        val commitSha: String
    )

    private data class CommitFileInfo(
        val filename: String,
        val status: String,
        val previousFilename: String?
    )

    private var currentCommitInfo: CommitUrlInfo? = null
    private var currentParentSha: String? = null
    private var commitFiles: List<CommitFileInfo> = emptyList()
    private var selectedFileIndex: Int = 0

    private lateinit var etCommitUrl: EditText
    private lateinit var btnFetchCommit: Button
    private lateinit var btnSelectFile: Button
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var diffView: DiffView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.main)

        findViewById<View>(R.id.btnComposeDemo).setOnClickListener {
            startActivity(Intent(this, ComposeDemoActivity::class.java))
        }
        findViewById<View>(R.id.btnViewDemo).setOnClickListener {
            startActivity(Intent(this, DemoActivity::class.java))
        }

        etCommitUrl = findViewById(R.id.etCommitUrl)
        btnFetchCommit = findViewById(R.id.btnFetchCommit)
        btnSelectFile = findViewById(R.id.btnSelectFile)
        tvStatus = findViewById(R.id.tvStatus)
        progressBar = findViewById(R.id.progressBar)
        diffView = findViewById(R.id.diffview)

        // DiffView 기본 설정
        diffView.setDiffMode(DiffMode.SIDE_BY_SIDE)
        diffView.setTextSize(12.5f)
        diffView.setFoldingEnabled(enabled = true, contextLines = 3, threshold = 8)
        diffView.setSyntaxHighlighter(DefaultKotlinSyntaxHighlighter())
        diffView.setLineWrap(false)
        diffView.setShowDiffSymbols(true)
        diffView.setGutterWidthDp(50)
        diffView.setTextIsSelectable(true)

        // 초기 샘플 코드 표시
        diffView.setContent(original = SAMPLE_ORIGINAL, modified = SAMPLE_MODIFIED)
//        diffView.expandAll()

        btnFetchCommit.setOnClickListener {
            val url = etCommitUrl.text.toString().trim()
            fetchCommit(url)
        }

        btnSelectFile.setOnClickListener {
            showFileSelectionDialog()
        }
    }

    private fun parseCommitUrl(rawUrl: String): CommitUrlInfo? {
        val regex = Regex("""github\.com/([^/]+)/([^/]+)/commit/([0-9a-fA-F]+)""")
        val matchResult = regex.find(rawUrl) ?: return null
        val (owner, repo, commitSha) = matchResult.destructured
        return CommitUrlInfo(owner, repo, commitSha)
    }

    private fun hashKey(commitSha: String, filename: String): String {
        val input = "${commitSha}_$filename"
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun fetchStringFromUrl(urlString: String): String {
        val url = URL(urlString)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("User-Agent", "Android-DiffView-App")
        }
        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            throw Exception("HTTP $responseCode: ${connection.responseMessage}")
        }
        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    private fun fetchCommit(rawUrl: String) {
        val parsed = parseCommitUrl(rawUrl)
        if (parsed == null) {
            Toast.makeText(this, "올바른 GitHub 커밋 URL을 입력하세요.", Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true, "커밋 정보 가져오는 중...")

        lifecycleScope.launch {
            try {
                val (parentSha, files) = withContext(Dispatchers.IO) {
                    val commitsCacheDir = File(cacheDir, "diff_cache/commits").apply { mkdirs() }
                    val commitCacheFile = File(commitsCacheDir, "${parsed.commitSha}.json")

                    val jsonString = if (commitCacheFile.exists() && commitCacheFile.length() > 0) {
                        commitCacheFile.readText()
                    } else {
                        val apiUrl = "https://api.github.com/repos/${parsed.owner}/${parsed.repo}/commits/${parsed.commitSha}"
                        val fetched = fetchStringFromUrl(apiUrl)
                        commitCacheFile.writeText(fetched)
                        fetched
                    }

                    val json = JSONObject(jsonString)

                    val parentsArray = json.optJSONArray("parents")
                    val pSha = if (parentsArray != null && parentsArray.length() > 0) {
                        parentsArray.getJSONObject(0).getString("sha")
                    } else null

                    val filesArray = json.getJSONArray("files")
                    val parsedFiles = mutableListOf<CommitFileInfo>()
                    for (i in 0 until filesArray.length()) {
                        val fileObj = filesArray.getJSONObject(i)
                        parsedFiles.add(
                            CommitFileInfo(
                                filename = fileObj.getString("filename"),
                                status = fileObj.optString("status", "modified"),
                                previousFilename = if (fileObj.has("previous_filename")) fileObj.getString("previous_filename") else null
                            )
                        )
                    }
                    Pair(pSha, parsedFiles)
                }

                currentCommitInfo = parsed
                currentParentSha = parentSha
                commitFiles = files
                selectedFileIndex = 0

                btnSelectFile.isEnabled = files.isNotEmpty()
                setLoading(false, "커밋 로드 완료 (${files.size}개 파일). 파일 선택을 눌러주세요.")

                if (files.isNotEmpty()) {
                    showFileSelectionDialog()
                } else {
                    Toast.makeText(this@MainActivity, "커밋에 변경된 파일이 없습니다.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                setLoading(false, "오류: ${e.message}")
                Toast.makeText(this@MainActivity, "커밋 가져오기 실패: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showFileSelectionDialog() {
        if (commitFiles.isEmpty()) {
            Toast.makeText(this, "선택할 파일이 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        var tempSelectedIndex = selectedFileIndex.coerceIn(0, commitFiles.size - 1)
        val fileNames = commitFiles.map { file ->
            val statusPrefix = when (file.status.lowercase()) {
                "added" -> "[+]"
                "removed" -> "[-]"
                "modified" -> "[~]"
                "renamed" -> "[R]"
                else -> "[*]"
            }
            "$statusPrefix ${file.filename}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("파일 선택 (${commitFiles.size}개)")
            .setSingleChoiceItems(fileNames, tempSelectedIndex) { _, which ->
                tempSelectedIndex = which
            }
            .setPositiveButton("DiffView") { dialog, _ ->
                selectedFileIndex = tempSelectedIndex
                val selectedFile = commitFiles[selectedFileIndex]
                loadDiffForFile(selectedFile)
                dialog.dismiss()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun loadDiffForFile(fileInfo: CommitFileInfo) {
        val commitInfo = currentCommitInfo ?: return
        val parentSha = currentParentSha

        setLoading(true, "'${fileInfo.filename}' 내용 로드 중...")

        lifecycleScope.launch {
            try {
                val (origText, modText) = withContext(Dispatchers.IO) {
                    val contentsDir = File(cacheDir, "diff_cache/contents").apply { mkdirs() }
                    val key = hashKey(commitInfo.commitSha, fileInfo.filename)
                    val oldCacheFile = File(contentsDir, "${key}_old.txt")
                    val newCacheFile = File(contentsDir, "${key}_new.txt")

                    // 1. Modified(신규) 내용 캐시 및 로드
                    val newContent = if (fileInfo.status == "removed") {
                        ""
                    } else if (newCacheFile.exists() && newCacheFile.length() > 0) {
                        newCacheFile.readText()
                    } else {
                        val rawNewUrl =
                            "https://raw.githubusercontent.com/${commitInfo.owner}/${commitInfo.repo}/${commitInfo.commitSha}/${fileInfo.filename}"
                        val fetched = fetchStringFromUrl(rawNewUrl)
                        newCacheFile.writeText(fetched)
                        fetched
                    }

                    // 2. Original(이전) 내용 캐시 및 로드
                    val oldContent = if (fileInfo.status == "added" || parentSha == null) {
                        ""
                    } else if (oldCacheFile.exists() && oldCacheFile.length() > 0) {
                        oldCacheFile.readText()
                    } else {
                        val origPath = fileInfo.previousFilename ?: fileInfo.filename
                        val rawOldUrl = "https://raw.githubusercontent.com/${commitInfo.owner}/${commitInfo.repo}/$parentSha/$origPath"
                        val fetched = fetchStringFromUrl(rawOldUrl)
                        oldCacheFile.writeText(fetched)
                        fetched
                    }

                    Pair(oldContent, newContent)
                }

                diffView.setHeaderTitles(
                    original = fileInfo.previousFilename ?: fileInfo.filename,
                    modified = fileInfo.filename
                )
                diffView.setContent(original = origText, modified = modText)
                //diffView.expandAll()
                setLoading(false, "선택됨: ${fileInfo.filename}")
            } catch (e: Exception) {
                e.printStackTrace()
                setLoading(false, "파일 로드 오류: ${e.message}")
                Toast.makeText(this@MainActivity, "파일 로드 실패: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setLoading(loading: Boolean, message: String) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        btnFetchCommit.isEnabled = !loading
        tvStatus.text = message
    }

    companion object {
        private val SAMPLE_ORIGINAL = """
package com.example.splitdiff

import java.util.Date

class UserProfile(
    val id: Long,
    val name: String,
    val age: Int,
    val city: String = "Seoul"
) {
    fun printInfo() {
        println("User: ${'$'}name, Age: ${'$'}age")
        println("Created at: ${'$'}{Date()}")
    }

    fun helper1() = 1
    fun helper2() = 2
    fun helper3() = 3
    fun helper4() = 4
    fun helper5() = 5
    fun helper6() = 6
    fun helper7() = 7
    fun helper8() = 8
    fun helper9() = 9
    fun helper10() = 10

    fun calculateDiscount(price: Double): Double {
        val discountRate = 0.10
        val finalPrice = price * (1.0 - discountRate)
        println("Old discount logic")
        return finalPrice
    }
}
""".trimIndent()

        private val SAMPLE_MODIFIED = """
package com.example.splitdiff

import java.util.Date
import java.time.Instant

data class UserProfile(
    val id: Long,
    val name: String,
    val age: Int,
    val email: String? = null,
    val city: String = "Jeju"
) {
    fun printInfo() {
        println("User: ${'$'}name, Age: ${'$'}age, Email: ${'$'}email")
        println("Created at: ${'$'}{Instant.now()}")
    }

    fun helper1() = 1
    fun helper2() = 2
    fun helper3() = 3
    fun helper4() = 4
    fun helper5() = 5
    fun helper6() = 6
    fun helper7() = 7
    fun helper8() = 8
    fun helper9() = 9
    fun helper10() = 10

    fun calculateDiscount(price: Double, isVip: Boolean = false): Double {
        val discountRate = if (isVip) 0.20 else 0.10
        val finalPrice = price * (1.0 - discountRate)
        return finalPrice
    }
}
""".trimIndent()
    }
}