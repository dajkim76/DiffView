package com.mdiwebma.diffview.comment

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Manages in-memory cache and file persistence (JSON) for code line comments.
 */
class CodeCommentManager(private val baseDir: File) {

    constructor(context: Context) : this(File(context.filesDir, "code_comments"))

    private val gson = Gson()
    // Cache: [commitHash:filePath] -> [LineKey -> CodeComment]
    private val memoryCache = mutableMapOf<String, MutableMap<LineKey, CodeComment>>()

    private fun getCacheKey(commitHash: String, filePath: String): String = "$commitHash:$filePath"

    private fun getStorageDir(): File {
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        return baseDir
    }

    private fun getCommentFile(commitHash: String, filePath: String): File {
        val pathHash = MessageDigest.getInstance("SHA-256")
            .digest(filePath.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16)
        val shortCommit = commitHash.take(8)
        val fileName = "comments_${shortCommit}_${pathHash}.json"
        return File(getStorageDir(), fileName)
    }

    /**
     * Loads comments from memory or disk for given commit and file.
     */
    suspend fun loadComments(commitHash: String, filePath: String): Map<LineKey, CodeComment> {
        val cacheKey = getCacheKey(commitHash, filePath)
        memoryCache[cacheKey]?.let { return it }

        return withContext(Dispatchers.IO) {
            val file = getCommentFile(commitHash, filePath)
            val commentsMap = mutableMapOf<LineKey, CodeComment>()

            if (file.exists()) {
                try {
                    val json = file.readText()
                    val payload = gson.fromJson(json, FileCommentsPayload::class.java)
                    payload?.comments?.forEach { (keyStr, comment) ->
                        commentsMap[LineKey.fromKeyString(keyStr)] = comment
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            synchronized(memoryCache) {
                memoryCache[cacheKey] = commentsMap
            }
            commentsMap
        }
    }

    /**
     * Gets a comment synchronously from memory cache if available.
     */
    fun getComment(commitHash: String, filePath: String, key: LineKey): CodeComment? {
        val cacheKey = getCacheKey(commitHash, filePath)
        return memoryCache[cacheKey]?.get(key)
    }

    /**
     * Adds or updates a comment for a given line.
     */
    suspend fun saveComment(commitHash: String, filePath: String, key: LineKey, text: String): CodeComment {
        val cacheKey = getCacheKey(commitHash, filePath)
        val comment: CodeComment
        synchronized(memoryCache) {
            val map = memoryCache.getOrPut(cacheKey) { mutableMapOf() }
            comment = CodeComment(text = text, updatedAt = System.currentTimeMillis())
            map[key] = comment
        }
        persistToFile(commitHash, filePath)
        return comment
    }

    /**
     * Deletes a comment for a given line.
     */
    suspend fun deleteComment(commitHash: String, filePath: String, key: LineKey) {
        val cacheKey = getCacheKey(commitHash, filePath)
        synchronized(memoryCache) {
            memoryCache[cacheKey]?.remove(key)
        }
        persistToFile(commitHash, filePath)
    }

    private suspend fun persistToFile(commitHash: String, filePath: String) = withContext(Dispatchers.IO) {
        val cacheKey = getCacheKey(commitHash, filePath)
        val currentComments = synchronized(memoryCache) {
            memoryCache[cacheKey]?.toMap()
        } ?: return@withContext

        val file = getCommentFile(commitHash, filePath)

        if (currentComments.isEmpty()) {
            if (file.exists()) {
                file.delete()
            }
            return@withContext
        }

        val payloadMap = currentComments.mapKeys { it.key.toKeyString() }.toMutableMap()
        val payload = FileCommentsPayload(commitHash, filePath, payloadMap)
        file.writeText(gson.toJson(payload))
    }
}
