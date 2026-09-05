package com.mdiwebma.diffviewer

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.mdiwebma.diffview.SyntaxHighlighter
import com.mdiwebma.diffview.model.DiffMode
import com.mdiwebma.diffviewer.box.AppBoxStore
import com.mdiwebma.diffviewer.box.DiffEntity
import com.mdiwebma.diffviewer.box.DiffGroupEntity
import com.mdiwebma.diffviewer.databinding.MainBinding
import com.mdiwebma.diffviewer.utils.GithubUtils
import io.objectbox.Box
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: MainBinding

    private var currentCommitInfo: GithubUtils.CommitUrlInfo? = null
    private var currentParentSha: String? = null
    private var commitFiles: List<GithubUtils.CommitFileInfo> = emptyList()
    private var selectedFileIndex: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = MainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.e("__T", "runcount=${AppSettings.runCount.value}")
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

        setSupportActionBar(binding.toolbar)
        binding.toolbar.overflowIcon?.setTint(Color.WHITE)

        // Load persisted DiffView preferences (diffMode, theme, textSize, folding, syntax, etc.)
        binding.diffview.loadPreferences()
        binding.diffview.setAutoSavePreferences(true)

        // Show initial sample code
        binding.diffview.setContent(YAML_BEFORE, YAML_AFTER)
        binding.diffview.setCommentTextSizes(11f, 9f)
        binding.diffview.setCommentContext("sample_initial_commit", "MainActivity.kt")

        binding.btnFetchCommit.setOnClickListener {
            val url = binding.etCommitUrl.text.toString().trim()
            fetchCommit(url)
        }

        binding.btnSelectFile.setOnClickListener {
            showFileSelectionDialog()
        }

        // AppBoxStore
        val diffGroupBox: Box<DiffGroupEntity> = AppBoxStore.getInstance(this).getBox()
        val diffBox: Box<DiffEntity> = AppBoxStore.getInstance(this).getBox()

        val diffGroupEntity = DiffGroupEntity(title = "title 1", type = 0)
        diffGroupBox.put(diffGroupEntity)

//        diffGroupBox.query().equal(DiffGroupEntity_.id, 1).build().use {
//            it.findFirst()?.let { diffGroup ->
//                val diff1 = DiffEntity(title = "diff 3")
//                val diff2 = DiffEntity(title = "diff 4")
//                diffGroup.diffs.add(diff1)
//                diffGroup.diffs.add(diff2)
//                diffGroupBox.put(diffGroup)
//                Lx("new  Id1 = " + diff1.id)
//                Lx("new  Id2 = " + diff2.id)
//                Lx("history .. diff size=" + diffGroup.diffs.size)
//            }
//        }

        AppSettings.runCount.value++
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
                val newMode = if (binding.diffview.getDiffMode() == DiffMode.SIDE_BY_SIDE) {
                    DiffMode.UNIFIED
                } else {
                    DiffMode.SIDE_BY_SIDE
                }
                binding.diffview.setDiffMode(newMode)
                Toast.makeText(this, getString(R.string.msg_diff_mode, newMode.name), Toast.LENGTH_SHORT).show()
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
        binding.diffview.showMoreMenu()
    }

    private fun fetchCommit(rawUrl: String) {
        val parsed = GithubUtils.parseCommitUrl(rawUrl)
        if (parsed == null) {
            Toast.makeText(this, R.string.msg_invalid_commit_url, Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true, getString(R.string.msg_fetching_commit))

        lifecycleScope.launch {
            try {
                val detail = GithubUtils.fetchCommit(cacheDir, parsed)

                currentCommitInfo = parsed
                currentParentSha = detail.parentSha
                commitFiles = detail.files
                selectedFileIndex = 0

                binding.btnSelectFile.isEnabled = detail.files.isNotEmpty()
                setLoading(false, getString(R.string.msg_commit_loaded, detail.files.size))

                if (detail.files.isNotEmpty()) {
                    showFileSelectionDialog()
                } else {
                    Toast.makeText(this@MainActivity, R.string.msg_no_changed_files, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val errorMsg = e.message ?: ""
                setLoading(false, getString(R.string.msg_file_load_error, errorMsg))
                Toast.makeText(this@MainActivity, getString(R.string.msg_failed_fetch_commit, errorMsg), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showFileSelectionDialog() {
        if (commitFiles.isEmpty()) {
            Toast.makeText(this, R.string.msg_no_files_to_select, Toast.LENGTH_SHORT).show()
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
            .setTitle(getString(R.string.dialog_title_select_file, commitFiles.size))
            .setSingleChoiceItems(fileNames, tempSelectedIndex) { _, which ->
                tempSelectedIndex = which
            }
            .setPositiveButton(R.string.btn_diff_view) { dialog, _ ->
                selectedFileIndex = tempSelectedIndex
                val selectedFile = commitFiles[selectedFileIndex]
                loadDiffForFile(selectedFile)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun loadDiffForFile(fileInfo: GithubUtils.CommitFileInfo) {
        val commitInfo = currentCommitInfo ?: return
        val parentSha = currentParentSha

        setLoading(true, getString(R.string.msg_loading_file, fileInfo.filename))

        lifecycleScope.launch {
            try {
                val content = GithubUtils.fetchFileContent(cacheDir, commitInfo, fileInfo, parentSha)

                binding.diffview.apply {
                    setSyntaxHighlighter(SyntaxHighlighter.forFileName(fileInfo.filename))
                    setContent(rawOriginal = content.originalText, rawModified = content.modifiedText)
                    setTextNormalizer(null)
                    setCommentContext(commitInfo.commitSha, fileInfo.filename)
                }
                setLoading(false, getString(R.string.msg_selected_file, fileInfo.filename))
            } catch (e: Exception) {
                e.printStackTrace()
                val errorMsg = e.message ?: ""
                setLoading(false, getString(R.string.msg_file_load_error, errorMsg))
                Toast.makeText(this@MainActivity, getString(R.string.msg_failed_load_file, errorMsg), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setLoading(loading: Boolean, message: String) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnFetchCommit.isEnabled = !loading
        binding.tvStatus.text = message
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