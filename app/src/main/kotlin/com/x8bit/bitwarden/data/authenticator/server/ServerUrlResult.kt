package com.x8bit.bitwarden.data.authenticator.server

import java.net.URI

/**
 * A server URL the login screen can store, or a Chinese explanation of why it cannot.
 */
sealed class ServerUrlResult {
    /**
     * Normalized `https://` URL with no trailing slash.
     */
    data class Accepted(val url: String) : ServerUrlResult()

    /**
     * The address is missing or not HTTPS.
     */
    data class Rejected(val message: String) : ServerUrlResult()
}

/**
 * Requires HTTPS. A missing scheme is filled in as `https://`.
 * User-installed CA certificates are trusted by the app network config, so a self-hosted
 * certificate does not need a special exception here.
 */
fun normalizeServerUrl(raw: String): ServerUrlResult {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) {
        return ServerUrlResult.Rejected("请填写服务器地址。")
    }
    val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
    val uri = runCatching { URI(withScheme) }.getOrNull()
        ?: return ServerUrlResult.Rejected("服务器地址无法识别。")
    val scheme = uri.scheme?.lowercase()
    if (scheme != "https") {
        return ServerUrlResult.Rejected(
            "请使用 HTTPS。自建服务器可以安装你自己的 CA，把它装进手机后就能连接。",
        )
    }
    val host = uri.host?.lowercase()
    if (host.isNullOrBlank()) {
        return ServerUrlResult.Rejected("服务器地址无法识别。")
    }
    val port = if (uri.port > 0) ":${uri.port}" else ""
    val path = uri.rawPath?.trimEnd('/')?.takeIf { it.isNotEmpty() }.orEmpty()
    return ServerUrlResult.Accepted("https://$host$port$path")
}
