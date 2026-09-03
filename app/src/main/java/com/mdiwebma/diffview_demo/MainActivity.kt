package com.mdiwebma.diffview_demo

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.mdiwebma.diffview.DiffView
import com.mdiwebma.diffview.SyntaxHighlighter
import com.mdiwebma.diffview.model.DiffMode
import com.mdiwebma.diffview_demo.box.AppBoxStore
import com.mdiwebma.diffview_demo.box.CompareEntity
import io.objectbox.Box
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

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )
            insets
        }

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.overflowIcon?.setTint(Color.WHITE)

        etCommitUrl = findViewById(R.id.etCommitUrl)
        btnFetchCommit = findViewById(R.id.btnFetchCommit)
        btnSelectFile = findViewById(R.id.btnSelectFile)
        tvStatus = findViewById(R.id.tvStatus)
        progressBar = findViewById(R.id.progressBar)
        diffView = findViewById(R.id.diffview)

        // Load persisted DiffView preferences (diffMode, theme, textSize, folding, syntax, etc.)
        diffView.loadPreferences()
        diffView.setAutoSavePreferences(true)

        // Show initial sample code
        //diffView.setHeaderTitles("MainActivity.kt (Old)", "MainActivity.kt (New)")
        //diffView.setContent(original = SAMPLE_ORIGINAL, modified = SAMPLE_MODIFIED)
        //diffView.setTextNormalizer(TSVTextNormalizer)
        //diffView.setContent(TSV_BEFORE, TSV_AFTER)
        //diffView.setContent(JSON_BEFORE, JSON_AFTER)
        //diffView.setContent(XML_BEFORE, XML_AFTER)
        //diffView.setContent(SQL_BEFORE, SQL_AFTER)
        diffView.setContent(YAML_BEFORE, YAML_AFTER)
        //diffView.setLongTabAction(DiffLongTabAction.COMMENT)
        diffView.setCommentTextSizes(11f, 9f)
        diffView.setCommentContext("sample_initial_commit", "MainActivity.kt")
        //diffView.setSettingsButtonVisible(false)
//        diffView.expandAll()

        btnFetchCommit.setOnClickListener {
            val url = etCommitUrl.text.toString().trim()
            fetchCommit(url)
        }

        btnSelectFile.setOnClickListener {
            showFileSelectionDialog()
        }

        // AppBoxStore
        val compareBox: Box<CompareEntity> = AppBoxStore.getInstance(this).getBox()
        compareBox.put(CompareEntity(beforeText = SAMPLE_ORIGINAL, afterText = SAMPLE_MODIFIED, count = 123))
        //compareBox.removeAll()
        compareBox.all.forEach {
            Log.e("__T", "id=${it.id} title2=${it.title2}, title3=${it.title3}, count=${it.count}")
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_save_image -> {
                saveDiffImage()
                true
            }

            R.id.action_toggle_mode -> {
                val newMode = if (diffView.getDiffMode() == DiffMode.SIDE_BY_SIDE) {
                    DiffMode.UNIFIED
                } else {
                    DiffMode.SIDE_BY_SIDE
                }
                diffView.setDiffMode(newMode)
                Toast.makeText(this, "모드: ${newMode.name}", Toast.LENGTH_SHORT).show()
                true
            }

            R.id.action_compose_demo -> {
                startActivity(Intent(this, ComposeDemoActivity::class.java))
                true
            }

            R.id.action_view_demo -> {
                startActivity(Intent(this, DemoActivity::class.java))
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun saveDiffImage() {
        diffView.showMoreMenu()
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
            Toast.makeText(this, "Please enter a valid GitHub commit URL.", Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true, "Fetching commit info...")

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
                        val filename = fileObj.getString("filename")
                        if (isBinaryFile(filename)) {
                            continue
                        }
                        parsedFiles.add(
                            CommitFileInfo(
                                filename = filename,
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
                setLoading(false, "Commit loaded (${files.size} files). Click 'Select File'.")

                if (files.isNotEmpty()) {
                    showFileSelectionDialog()
                } else {
                    Toast.makeText(this@MainActivity, "No changed files in this commit.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                setLoading(false, "Error: ${e.message}")
                Toast.makeText(this@MainActivity, "Failed to fetch commit: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showFileSelectionDialog() {
        if (commitFiles.isEmpty()) {
            Toast.makeText(this, "No files available to select.", Toast.LENGTH_SHORT).show()
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
            .setTitle("Select File (${commitFiles.size})")
            .setSingleChoiceItems(fileNames, tempSelectedIndex) { _, which ->
                tempSelectedIndex = which
            }
            .setPositiveButton("DiffView") { dialog, _ ->
                selectedFileIndex = tempSelectedIndex
                val selectedFile = commitFiles[selectedFileIndex]
                loadDiffForFile(selectedFile)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadDiffForFile(fileInfo: CommitFileInfo) {
        val commitInfo = currentCommitInfo ?: return
        val parentSha = currentParentSha

        setLoading(true, "Loading '${fileInfo.filename}'...")

        lifecycleScope.launch {
            try {
                val (origText, modText) = withContext(Dispatchers.IO) {
                    val contentsDir = File(cacheDir, "diff_cache/contents").apply { mkdirs() }
                    val key = hashKey(commitInfo.commitSha, fileInfo.filename)
                    val oldCacheFile = File(contentsDir, "${key}_old.txt")
                    val newCacheFile = File(contentsDir, "${key}_new.txt")

                    // 1. Cache and load Modified (new) content
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

                    // 2. Cache and load Original (old) content
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

                //diffView.setHeaderTitles(
                //    original = fileInfo.previousFilename ?: fileInfo.filename,
                //    modified = fileInfo.filename
                //)
                diffView.setSyntaxHighlighter(SyntaxHighlighter.forFileName(fileInfo.filename))
                diffView.setContent(rawOriginal = origText, rawModified = modText)
                diffView.setTextNormalizer(null)
                diffView.setCommentContext(commitInfo.commitSha, fileInfo.filename)
                //diffView.expandAll()
                setLoading(false, "Selected: ${fileInfo.filename}")
            } catch (e: Exception) {
                e.printStackTrace()
                setLoading(false, "File load error: ${e.message}")
                Toast.makeText(this@MainActivity, "Failed to load file: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setLoading(loading: Boolean, message: String) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        btnFetchCommit.isEnabled = !loading
        tvStatus.text = message
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            //diffView.setDiffMode(DiffMode.SIDE_BY_SIDE)
        } else {
            //diffView.setDiffMode(DiffMode.UNIFIED)
        }
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

        private const val GIT_PATCH = """diff --git a/src/App.tsx b/src/App.tsx
index b61a540..27c4240 100644
--- a/src/App.tsx
+++ b/src/App.tsx
@@ -1,8 +1,22 @@
+import { useEffect, useState } from 'react';
+
 export default function App() {
+    const [messages, setMessages] = useState<string[]>([]);
+
+    useEffect(() => {
+        setMessages(['git', 'diff', 'patch']);
+    }, []);
+
     return (
-        <div>
-            <h2>eslint + prettier로 포매팅 자동화하기</h2>
-            <p>저장을 해야만 에러가 사라집니다...</p>
-        </div>
+        <main>
+            <h2>git diff로 patch 파일 생성하기</h2>
+            <div>
+                <ul>
+                    {messages.map((message, index) => (
+                        <li key={index}>{message}</li>
+                    ))}
+                </ul>
+            </div>
+        </main>
     );
 }"""

        private val BINARY_EXTENSIONS = hashSetOf(
            // Images
            "png", "jpg", "jpeg", "gif", "webp", "ico", "bmp", "tiff", "tif", "heic", "heif", "psd", "ai", "raw", "svgz",
            // Archives & Compressed
            "zip", "tar", "gz", "tgz", "bz2", "xz", "7z", "rar", "jar", "aar", "war", "apk", "aab", "ipa",
            // Binaries & Libraries
            "so", "dylib", "dll", "class", "exe", "bin", "o", "a", "lib", "obj", "elf", "dex",
            // Documents & Fonts
            "pdf", "ttf", "otf", "woff", "woff2", "eot", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            // Media (Audio/Video)
            "mp3", "wav", "ogg", "flac", "m4a", "aac", "mp4", "mov", "avi", "mkv", "webm", "flv", "3gp",
            // Database & Box
            "db", "sqlite", "sqlite3", "mdb"
        )

        fun isBinaryFile(filename: String): Boolean {
            val ext = filename.substringAfterLast('.', "").lowercase()
            return BINARY_EXTENSIONS.contains(ext)
        }
    }

    val JSON_BEFORE = """{
  "user": {
    "id": 1001,
    "name": "홍길동",
    "email": "hong@example.com",
    "is_active": true,
    "roles": ["User", "Admin"]
  },
  "orders": [
    {
      "order_id": "ORD-2026-001",
      "item": "무선 키보드",
      "price": 45000,
      "quantity": 1
    },
    {
      "order_id": "ORD-2026-002",
      "item": "인체공학 마우스",
      "price": 32000,
      "quantity": 2
    }
  ]
}"""

    val JSON_AFTER =
        """{"user":{"id":1001,"name":"홍길동","email":"hong@example.com","is_active":false,"roles":["User","Admin"]},"shipping_address":{"city":"서울","zipcode":"04524","address":"세종대로 110"},"orders":[{"order_id":"ORD-2026-001","item":"무선","price":45000,"quantity":1},{"order_id":"ORD-2026-002","item":"마우스","price":312000,"quantity":2}]}"""

    val TSV_BEFORE = """id	name	address	score
1	김철수	서울시 강남구, 테헤란로	95
2	이영희	부산시 해운대구, 우동	88"""

    val TSV_AFTER = """id	name	address	score
1	김철수	서울시 강남구, 강남	95
2	영희	부산시 해운대구	88"""

    val XML_BEFORE = """<root>
  <message>Hello </message>
  <message>World!</message>
  <message></message>
</root>"""

    val XML_AFTER = "<root><message>Hello </message><message>World!</message><message></message></root>"

    val SQL_BEFORE = """
        SELECT  id, name 
        FROM users 
        WHERE id = 1  ; -- 유저 조회
    """.trimIndent()

    val SQL_AFTER = "/* comment */ select id,name from users where id=1"

    val YAML_BEFORE = """users:
- name: 'John Doe'
  active: yes
  age: 30
- name: "Jane Smith "
  active: no
  age: 25
    
server :
    ssl: yes
    port :   8080 # server port
    env: "production"
    title: 'my-app'
    enabled: On
    endpoints :
      -   /api/v1
      -  name : "test:endpoint"
version:   1.0.0
auth:
    require_auth: off
    admin: YES
    password: "yes" # quote maintained    
"""

    val YAML_AFTER = """users:
- active: true
  age: 30
  name: John Doe
- active: false
  age: 25
  name: Jane Smith
  
auth:
  admin: true
  password: "yes" # quote maintained
  require_auth: false
server:
  enabled: true
  endpoints:
    - /api/v1
    - name: "test:endpoint"
  env: production
  port: 8080 # server port
  ssl: true
  title: my-app
version: 1.0.0  
"""
}