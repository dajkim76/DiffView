package com.mdiwebma.diffviewer.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object GithubUtils {

    data class CommitUrlInfo(
        val owner: String,
        val repo: String,
        val commitSha: String
    )

    data class CommitFileInfo(
        val filename: String,
        val status: String,
        val previousFilename: String? = null
    )

    data class CommitDetail(
        val parentSha: String?,
        val files: List<CommitFileInfo>
    )

    data class FileContent(
        val originalText: String,
        val modifiedText: String
    )

    private val COMMIT_URL_REGEX = Regex("""github\.com/([^/]+)/([^/]+)/commit/([0-9a-fA-F]+)""")

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

    fun parseCommitUrl(rawUrl: String): CommitUrlInfo? {
        val matchResult = COMMIT_URL_REGEX.find(rawUrl) ?: return null
        val (owner, repo, commitSha) = matchResult.destructured
        return CommitUrlInfo(owner, repo, commitSha)
    }

    fun isBinaryFile(filename: String): Boolean {
        val ext = filename.substringAfterLast('.', "").lowercase()
        return BINARY_EXTENSIONS.contains(ext)
    }

    fun hashKey(commitSha: String, filename: String): String {
        val input = "${commitSha}_$filename"
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun fetchStringFromUrl(urlString: String): String {
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

    suspend fun fetchCommit(cacheDir: File, commitInfo: CommitUrlInfo): CommitDetail = withContext(Dispatchers.IO) {
        val commitsCacheDir = File(cacheDir, "diff_cache/commits").apply { mkdirs() }
        val commitCacheFile = File(commitsCacheDir, "${commitInfo.commitSha}.json")

        val jsonString = if (commitCacheFile.exists() && commitCacheFile.length() > 0) {
            commitCacheFile.readText()
        } else {
            val apiUrl = "https://api.github.com/repos/${commitInfo.owner}/${commitInfo.repo}/commits/${commitInfo.commitSha}"
            val fetched = fetchStringFromUrl(apiUrl)
            commitCacheFile.writeText(fetched)
            fetched
        }

        val json = JSONObject(jsonString)
        val parentsArray = json.optJSONArray("parents")
        val parentSha = if (parentsArray != null && parentsArray.length() > 0) {
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
        CommitDetail(parentSha, parsedFiles)
    }

    suspend fun fetchFileContent(
        cacheDir: File,
        commitInfo: CommitUrlInfo,
        fileInfo: CommitFileInfo,
        parentSha: String?
    ): FileContent = withContext(Dispatchers.IO) {
        val contentsDir = File(cacheDir, "diff_cache/contents").apply { mkdirs() }
        val key = hashKey(commitInfo.commitSha, fileInfo.filename)
        val oldCacheFile = File(contentsDir, "${key}_old.txt")
        val newCacheFile = File(contentsDir, "${key}_new.txt")

        // 1. Modified (new) content
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

        // 2. Original (old) content
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

        FileContent(originalText = oldContent, modifiedText = newContent)
    }
}
