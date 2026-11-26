package com.x8bit.bitwarden.data.platform.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Unauthenticated GitHub client. The vault's OkHttp client is not reused, so vault headers
 * are not sent to github.com.
 */
@Singleton
class GithubAppUpdateClient @Inject constructor() : AppUpdateClient {
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_SECONDS, TimeUnit.SECONDS)
        .build()

    override suspend fun fetchReleases(): List<GithubRelease> = withContext(Dispatchers.IO) {
        val body = execute(ZH_RELEASES_URL).use { response ->
            if (!response.isSuccessful) {
                error("GitHub releases HTTP ${response.code}")
            }
            response.body.string()
        }
        parseReleases(body)
    }

    override suspend fun download(
        url: String,
        dest: File,
        onProgress: (read: Long, total: Long) -> Unit,
    ) {
        withContext(Dispatchers.IO) {
            dest.parentFile?.mkdirs()
            val call = http.newCall(request(url))
            coroutineContext.job.invokeOnCompletion { error ->
                if (error != null) call.cancel()
            }
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    error("Download HTTP ${response.code}")
                }
                val body = response.body
                val total = body.contentLength()
                body.byteStream().use { input ->
                    dest.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var read = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            read += count
                            onProgress(read, total)
                        }
                    }
                }
            }
        }
    }

    override suspend fun downloadText(url: String): String = withContext(Dispatchers.IO) {
        execute(url).use { response ->
            if (!response.isSuccessful) {
                error("Download HTTP ${response.code}")
            }
            response.body.string()
        }
    }

    override fun sha256(file: File): String = sha256Hex(file.readBytes())

    private fun execute(url: String) = http.newCall(request(url)).execute()

    private fun request(url: String): Request = Request.Builder()
        .url(url)
        .header("Accept", "application/vnd.github+json")
        .header("User-Agent", USER_AGENT)
        .build()
}

private const val CONNECT_SECONDS: Long = 20
private const val READ_SECONDS: Long = 120
private const val BUFFER_SIZE: Int = 8 * 1024
private const val USER_AGENT: String = "BitwardenZhAuthenticator"
