package com.x8bit.bitwarden.data.authenticator.sync

import com.bitwarden.data.datasource.disk.model.EnvironmentUrlDataJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AuthenticatorHubTest {

    @Test
    fun hubUrl_usesTheVaultBaseWhenNotificationsAreUnset() {
        val urls = EnvironmentUrlDataJson(base = "https://10.0.2.2:8443/")
        assertEquals(
            "wss://10.0.2.2:8443/notifications/hub?access_token=a.b",
            authenticatorHubUrl(urls, "a.b"),
        )
    }

    @Test
    fun hubUrl_usesAnExplicitNotificationsHost() {
        val urls = EnvironmentUrlDataJson(
            base = "https://vault.example.com",
            notifications = "http://notify.example.com",
        )
        assertEquals(
            "ws://notify.example.com/hub?access_token=tok",
            authenticatorHubUrl(urls, "tok"),
        )
    }

    @Test
    fun notificationType_readsAnAuthenticatorCreate() {
        assertEquals(AUTHENTICATOR_CREATED, authenticatorNotificationType(invocation(type = 30)))
        assertEquals(AUTHENTICATOR_UPDATED, authenticatorNotificationType(invocation(type = 31)))
        assertEquals(AUTHENTICATOR_DELETED, authenticatorNotificationType(invocation(type = 32)))
    }

    @Test
    fun notificationType_readsASignalRSizedFrame() {
        val framed = signalRFrame(invocation(AUTHENTICATOR_CREATED))
        assertEquals(AUTHENTICATOR_CREATED, authenticatorNotificationType(framed))
    }

    @Test
    fun notificationType_readsAFrameCapturedFromVaultwarden() {
        val hex = "c101950180c0ae526563656976654d6573736167659183a9436f6e746578744964d924" +
            "32323232323232322d323232322d323232322d323232322d323232323232323232323232a4547970651e" +
            "a75061796c6f616483a24964d92463663036363666312d633163632d343936372d396263392d61313766" +
            "6264646135323661a6557365724964d92461313634383066372d393432382d343463362d613466382d" +
            "643761666633376466643262ac5265766973696f6e44617465d7ff55d8e0446b061ec7"
        val bytes = ByteArray(hex.length / 2) { index ->
            hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
        assertEquals(AUTHENTICATOR_CREATED, authenticatorNotificationType(bytes))
    }

    @Test
    fun notificationType_ignoresTheHandshakeAndOtherInvocations() {
        assertNull(authenticatorNotificationType("{}".toByteArray() + bytes(0x1e)))
        assertNull(authenticatorNotificationType(invocation(type = 5, target = "Heartbeat")))
        assertNull(authenticatorNotificationType(byteArrayOf()))
    }
}

/**
 * One MessagePack SignalR invocation, matching vaultwarden's `create_update` layout.
 * The payload carries a timestamp extension so the reader has to skip it.
 */
private fun invocation(type: Int, target: String = "ReceiveMessage"): ByteArray {
    val payload = bytes(0x81, 0xa2) + "Id".toByteArray() +
        utf8("entry") +
        bytes(0xd7, 0xff) + ByteArray(8)
    val fields = utf8("ContextId") + utf8("device") +
        utf8("Type") + bytes(type) +
        utf8("Payload") + payload
    return bytes(0x95, 0x01, 0x80, 0xc0) + utf8(target) + bytes(0x91, 0x83) + fields
}

private fun utf8(value: String): ByteArray {
    val body = value.toByteArray()
    return bytes(0xa0 + body.size) + body
}

private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

/** The 7-bit length prefix SignalR puts in front of a MessagePack hub message. */
private fun signalRFrame(body: ByteArray): ByteArray {
    var size = body.size
    val prefix = ArrayList<Byte>()
    while (true) {
        var part = size and 0x7f
        size = size ushr 7
        if (size > 0) {
            part = part or 0x80
        }
        prefix.add(part.toByte())
        if (size == 0) {
            break
        }
    }
    return prefix.toByteArray() + body
}
