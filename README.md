# SplitDiff

Android Studio Diff Editor 스타일의 **Side-by-Side (Split) & Unified DiffView** Android 재사용 가능한 커스텀 뷰 라이브러리입니다.

---

## 🌟 주요 기능

1. **2가지 Diff 모드 지원**:
   - **Side-by-Side (Split) 모드**: 좌(Original) / 우(Modified) 2열 나란히 표시하며 좌우 라인을 완벽 정렬.
   - **Unified (통합 위아래) 모드**: 하나의 뷰 안에서 변경 사항을 위아래 단일 열(`+` / `-`)로 표시.
2. **세로 스크롤 완전 동기화**:
   - 단일 `RecyclerView` 기반 뷰 재활용 구조로 좌우 세로 스크롤이 1px의 오차 없이 완벽히 동기화되며, 수만 줄의 대용량 파일도 부드럽게 렌더링.
3. **컬럼/뷰 단위 가로 스크롤 동기화**:
   - 짧은 줄이든 긴 줄이든 동일한 가상 캔버스 너비를 공유하여, 어느 줄을 잡고 드래그해도 해당 사이드의 모든 줄이 일체형으로 함께 가로 스크롤됨.
   - 좌측과 우측의 가로 스크롤은 서로 독립적으로 동작.
4. **인라인 Diff (문자/토큰 단위 정밀 하이라이트)**:
   - 변경된(`MODIFIED`) 라인에 대해 문자/토큰 단위 LCS 알고리즘을 적용하여 실제로 수정된 텍스트 부분만 진하게 강조.
5. **Git / Android Studio 스타일 Context-aware Folding (미변경 라인 접기)**:
   - 변경점 주변의 앞/뒤 문맥 라인(`contextLines`, 기본 3줄)은 유지하고, 중간의 긴 미변경 구간만 `⋯ N lines unchanged ⋯` 배너로 접기.
   - 배너 클릭 시 개별 블록 펼치기/접기 및 전체 일괄 펼치기/접기 지원.
6. **Syntax Highlighting & Dark Theme 지원**:
   - Kotlin/Java 기본 문법 하이라이터 내장 및 `SyntaxHighlighter` 인터페이스를 통한 커스텀 하이라이터 확장 가능.
   - Android Studio 스타일의 Light / Dark 테마 색상 팔레트 기본 제공.
7. **텍스트 선택 및 복사**:
   - 시스템 텍스트 드래그 선택 및 복사(`setTextIsSelectable(true)`) 완벽 지원.

---

## 📦 필요한 의존성 (Dependencies)

프로젝트의 `build.gradle.kts` (또는 `libs.versions.toml`)에 아래 의존성을 추가합니다:

```kotlin
dependencies {
    // Diff 계산 엔진
    implementation("io.github.petertrr:kotlin-multiplatform-diff:1.3.0")

    // Android 기본 UI 컴포넌트
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.15.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
```

---

## 📁 프로젝트에 복사해야 할 파일 목록

다른 프로젝트에 본 컴포넌트를 적용하려면 아래 **`com.mdiwebma.diffview` 패키지의 10개 파일**을 복사하여 사용하시면 됩니다:

```text
com.mdiwebma.diffview
├── DiffView.kt                   # 최종 커스텀 FrameLayout 뷰 컴포넌트
├── DiffViewAdapter.kt            # Side-by-Side & Unified 지원 RecyclerView 어댑터
├── DiffColors.kt                 # Android Studio Light / Dark 색상 테마 팔레트
├── SyntaxHighlighter.kt          # 문법 하이라이팅 인터페이스 및 Kotlin 구현체
├── FoldingManager.kt             # Git/AS 스타일 문맥 기반 라인 접기 매니저
├── SyncHorizontalScrollView.kt   # 컬럼 단위 가로 스크롤 동기화 ScrollView & Manager
├── model/
│   └── DiffModels.kt             # DiffRow, DiffLine, DiffRowType, DiffMode 등 데이터 모델
└── engine/
    ├── DiffEngine.kt             # Diff 계산 인터페이스
    ├── InlineDiffCalculator.kt   # 문자/토큰 단위 인라인 Diff 계산기
    └── KotlinDiffEngine.kt       # kotlin-multiplatform-diff 기반 구현체
```

---

## 🚀 기본 사용법

### 1. XML 레이아웃에 추가

```xml
<com.mdiwebma.diffview.DiffView
    android:id="@+id/diffView"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

### 2. Kotlin 코드에서 제어

```kotlin
import com.mdiwebma.diffview.DefaultKotlinSyntaxHighlighter
import com.mdiwebma.diffview.DiffColors
import com.mdiwebma.diffview.DiffView
import com.mdiwebma.diffview.model.DiffMode

val diffView = findViewById<DiffView>(R.id.diffView)

// 1. 원본 및 수정본 소스 코드 설정 (비동기 계산 및 렌더링)
val originalCode = """
    fun calculate(x: Int): Int {
        return x * 2
    }
""".trimIndent()

val modifiedCode = """
    fun calculate(x: Int, factor: Int = 2): Int {
        return x * factor
    }
""".trimIndent()

diffView.setContent(original = originalCode, modified = modifiedCode)

// 2. Diff 모드 설정 (기본값: SIDE_BY_SIDE)
diffView.setDiffMode(DiffMode.SIDE_BY_SIDE) // 좌우 2열 분할 모드
// diffView.setDiffMode(DiffMode.UNIFIED)    // 위아래 단일 열 통합 모드

// 3. 테마 설정 (Light / Dark), Default(Auto)
diffView.setDiffColors(DiffColors.Dark)

// 4. 글꼴 크기 변경 (SP 단위)
diffView.setTextSize(13f)

// 5. 미변경 라인 접기 설정 (문맥 라인 수, 접기 임계치)
diffView.setFoldingEnabled(enabled = true, contextLines = 3, threshold = 8)

// 6. 전체 펼치기 / 접기
diffView.expandAll()
diffView.collapseAll()

// 7. 공백 무시 비교 옵션 설정 (NONE, TRIM_LEADING_TRAILING, COLLAPSE_WHITESPACE, IGNORE_ALL)
diffView.setWhitespaceIgnoreMode(WhitespaceIgnoreMode.TRIM_LEADING_TRAILING)

// 8. 커스텀 문법 하이라이터 설정
diffView.setSyntaxHighlighter(DefaultKotlinSyntaxHighlighter())
```

---

## 📄 License

```text
GNU GENERAL PUBLIC LICENSE
Version 3, 29 June 2007

Copyright (C) 2026 SplitDiff Project

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
```
