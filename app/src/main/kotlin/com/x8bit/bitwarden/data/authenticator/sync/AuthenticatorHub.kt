package com.x8bit.bitwarden.data.authenticator.sync

import com.bitwarden.data.datasource.disk.model.EnvironmentUrlDataJson
import java.net.URLEncoder

/** Vaultwarden pushes this when another device creates a standalone authenticator entry. */
internal const val AUTHENTICATOR_CREATED: Int = 30

/** Vaultwarden pushes this when another device edits a standalone authenticator entry. */
internal const val AUTHENTICATOR_UPDATED: Int = 31

/** Vaultwarden pushes this when another device deletes a standalone authenticator entry. */
internal const val AUTHENTICATOR_DELETED: Int = 32

private const val HTTPS: String = "https://"
private const val HTTP: String = "http://"
private const val WSS: String = "wss://"
private const val WS: String = "ws://"

/**
 * SignalR hub for one self-hosted or cloud environment.
 *
 * The browser extension connects to `{notifications}/hub`. A vaultwarden base URL serves that
 * hub at `{base}/notifications/hub`. The access token is the same query parameter SignalR sends
 * when negotiation is skipped.
 */
internal fun authenticatorHubUrl(urls: EnvironmentUrlDataJson, accessToken: String): String {
    val notifications = urls.notifications?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
    val httpRoot = notifications ?: (urls.base.trim().trimEnd('/') + "/notifications")
    val wsRoot = when {
        httpRoot.startsWith(HTTPS) -> WSS + httpRoot.removePrefix(HTTPS)
        httpRoot.startsWith(HTTP) -> WS + httpRoot.removePrefix(HTTP)
        httpRoot.startsWith(WSS) || httpRoot.startsWith(WS) -> httpRoot
        else -> WSS + httpRoot
    }
    val token = URLEncoder.encode(accessToken, "UTF-8")
    return "$wsRoot/hub?access_token=$token"
}

/**
 * Reads the SignalR `ReceiveMessage` type from one MessagePack frame.
 *
 * Vaultwarden sends an invocation whose fourth argument is a map with `Type` 30, 31, or 32.
 * Handshake bytes and pings are not invocations, so they return null.
 */
internal fun authenticatorNotificationType(bytes: ByteArray): Int? = try {
    val payload = messagePackPayload(bytes)
    val root = MsgReader(payload).read()
    val items = (root as? Msg.Arr)?.items ?: return null
    if (items.size < ARGUMENT_INDEX + 1) {
        return null
    }
    if ((items[TARGET_INDEX] as? Msg.Str)?.value != RECEIVE_MESSAGE) {
        return null
    }
    val argument = (items[ARGUMENT_INDEX] as? Msg.Arr)?.items?.firstOrNull()
    val fields = argument as? Msg.Map ?: return null
    val type = fields.entries.firstOrNull { it.first == TYPE_FIELD }?.second
    (type as? Msg.IntVal)?.value?.toInt()
} catch (_: IndexOutOfBoundsException) {
    null
}

/**
 * SignalR frames the MessagePack body with a 7-bit size prefix. A bare array, which the unit
 * tests build, is left as-is.
 */
private fun messagePackPayload(bytes: ByteArray): ByteArray {
    if (bytes.isEmpty() || bytes[0].toInt() and HIGH_NIBBLE == FIXARRAY_HIGH) {
        return bytes
    }
    var size = 0
    var shift = 0
    var index = 0
    while (index < bytes.size) {
        val part = bytes[index].toInt() and BYTE_MASK
        index += 1
        size = size or ((part and VARINT_MASK) shl shift)
        if (part and VARINT_CONTINUE == 0) {
            break
        }
        shift += VARINT_STEP
    }
    return bytes.copyOfRange(index, index + size)
}

private const val HIGH_NIBBLE: Int = 0xf0
private const val BYTE_MASK: Int = 0xff
private const val FIXARRAY_HIGH: Int = 0x90
private const val VARINT_MASK: Int = 0x7f
private const val VARINT_CONTINUE: Int = 0x80
private const val VARINT_STEP: Int = 7
private const val TARGET_INDEX: Int = 3
private const val ARGUMENT_INDEX: Int = 4
private const val RECEIVE_MESSAGE: String = "ReceiveMessage"
private const val TYPE_FIELD: String = "Type"

private sealed interface Msg {
    data class IntVal(val value: Long) : Msg
    data class Str(val value: String) : Msg
    data class Arr(val items: List<Msg>) : Msg
    data class Map(val entries: List<Pair<String, Msg>>) : Msg
    data object Other : Msg
}

/**
 * Just enough of the MessagePack spec to read one notifications-hub invocation.
 * The opcode numbers are the format tags from the spec.
 */
@Suppress("MagicNumber")
private class MsgReader(private val bytes: ByteArray) {
    private var index: Int = 0

    fun read(): Msg {
        val marker = next()
        return when {
            marker <= 0x7f -> Msg.IntVal(marker.toLong())
            marker >= 0xe0 -> Msg.IntVal((marker - 0x100).toLong())
            marker in 0x80..0xbf -> decodePack(marker)
            else -> decodeTyped(marker)
        }
    }

    private fun decodePack(marker: Int): Msg = when (marker) {
        in 0x80..0x8f -> grouped(marker and 0x0f, asMap = true)
        in 0x90..0x9f -> grouped(marker and 0x0f, asMap = false)
        else -> Msg.Str(String(take(marker and 0x1f), Charsets.UTF_8))
    }

    private fun decodeTyped(marker: Int): Msg = when (marker) {
        in 0xcc..0xd3 -> Msg.IntVal(integer(marker))
        in 0xd9..0xdf -> sized(marker)
        else -> skip(marker)
    }

    private fun integer(marker: Int): Long = when (marker) {
        0xcc -> next().toLong()
        0xcd -> be(2)
        0xce -> be(4)
        0xcf, 0xd3 -> be(8)
        0xd0 -> next().toByte().toLong()
        0xd1 -> be(2).toShort().toLong()
        else -> be(4).toInt().toLong()
    }

    private fun sized(marker: Int): Msg {
        val count = when (marker) {
            0xd9, 0xdc, 0xde -> if (marker == 0xd9) next() else be(2).toInt()
            0xda -> be(2).toInt()
            else -> be(4).toInt()
        }
        return when (marker) {
            0xd9, 0xda, 0xdb -> Msg.Str(String(take(count), Charsets.UTF_8))
            0xdc, 0xdd -> grouped(count, asMap = false)
            else -> grouped(count, asMap = true)
        }
    }

    private fun grouped(count: Int, asMap: Boolean): Msg = if (asMap) {
        Msg.Map(List(count) { ((read() as? Msg.Str)?.value ?: "") to read() })
    } else {
        Msg.Arr(List(count) { read() })
    }

    private fun skip(marker: Int): Msg {
        val length = when (marker) {
            0xc4, 0xc7 -> next()
            0xc5, 0xc8 -> be(2).toInt()
            0xc6, 0xc9 -> be(4).toInt()
            0xca -> 4
            0xcb -> 8
            0xd4 -> 1
            0xd5 -> 2
            0xd6 -> 4
            0xd7 -> 8
            0xd8 -> 16
            else -> 0
        }
        val typeByte = if (marker in 0xc7..0xc9 || marker in 0xd4..0xd8) 1 else 0
        index += length + typeByte
        return Msg.Other
    }

    private fun take(count: Int): ByteArray {
        val slice = bytes.copyOfRange(index, index + count)
        index += count
        return slice
    }

    private fun next(): Int = bytes[index++].toInt() and 0xff

    private fun be(count: Int): Long {
        var value = 0L
        repeat(count) { value = (value shl 8) or next().toLong() }
        return value
    }
}
