package com.mdiwebma.diffview_demo

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.mdiwebma.diffview.DefaultKotlinSyntaxHighlighter
import com.mdiwebma.diffview.DiffView
import com.mdiwebma.diffview.model.DiffMode

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.main)
        findViewById<View>(R.id.main).setOnClickListener {
            startActivity(Intent(this, ComposeDemoActivity::class.java))
        }
        findViewById<View>(R.id.demo).setOnClickListener {
            startActivity(Intent(this, DemoActivity::class.java))
        }

        val diffView = findViewById<DiffView>(R.id.diffview)
        // 1. 원본 및 수정본 소스 코드 설정 (비동기 계산 및 렌더링)
        diffView.setContent(original = SAMPLE_ORIGINAL, modified = SAMPLE_MODIFIED)

        // 2. Diff 모드 설정 (기본값: SIDE_BY_SIDE)
        diffView.setDiffMode(DiffMode.SIDE_BY_SIDE) // 좌우 2열 분할 모드
        // diffView.setDiffMode(DiffMode.UNIFIED)    // 위아래 단일 열 통합 모드

        // 3. 테마 설정 (Light / Dark), default (Auto)
        //diffView.setDiffColors(DiffColors.Dark)

        // 4. 글꼴 크기 변경 (SP 단위)
        diffView.setTextSize(13f)

        // 5. 미변경 라인 접기 설정 (문맥 라인 수, 접기 임계치)
        diffView.setFoldingEnabled(enabled = true, contextLines = 3, threshold = 8)

        // 6. 전체 펼치기 / 접기
        diffView.expandAll()
        diffView.collapseAll()

        // 7. 커스텀 문법 하이라이터 설정
        diffView.setSyntaxHighlighter(DefaultKotlinSyntaxHighlighter())
        diffView.setLineWrap(true)
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