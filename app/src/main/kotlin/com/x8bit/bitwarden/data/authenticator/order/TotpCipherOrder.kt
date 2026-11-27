package com.x8bit.bitwarden.data.authenticator.order

import com.bitwarden.vault.CipherView
import com.bitwarden.vault.FieldType
import com.bitwarden.vault.FieldView
import com.x8bit.bitwarden.data.authenticator.model.AuthenticatorEntryView
import com.x8bit.bitwarden.data.authenticator.otp.isValidTotpSecret
import com.x8bit.bitwarden.data.authenticator.otp.normalizeTotpSecret
import com.x8bit.bitwarden.data.authenticator.otp.parseOtpAuthUri
import com.x8bit.bitwarden.data.vault.manager.model.GetCipherResult
import com.x8bit.bitwarden.data.vault.repository.VaultRepository
import java.time.Instant

/**
 * The hidden custom field on a login cipher, when one is stored.
 */
fun CipherView.totpOrderValue(): String? =
    fields
        ?.firstOrNull { it.name == TOTP_ORDER_FIELD && it.type == FieldType.HIDDEN }
        ?.value
        ?.takeIf { parseTotpOrder(it) != null }

/**
 * Returns a copy whose hidden [TOTP_ORDER_FIELD] is [order]. Other fields stay.
 */
fun CipherView.withTotpOrder(order: String): CipherView {
    val kept = fields.orEmpty().filterNot { it.name == TOTP_ORDER_FIELD }
    return copy(
        fields = kept + FieldView(
            name = TOTP_ORDER_FIELD,
            value = order,
            type = FieldType.HIDDEN,
            linkedId = null,
        ),
    )
}

/**
 * Base32 secret shared by an authenticator entry and a login cipher's TOTP.
 * Accepts a raw secret, an otpauth URI, or a steam URI.
 */
fun totpSecretKey(raw: String?): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    if (trimmed.startsWith("otpauth://", ignoreCase = true)) {
        val secret = parseOtpAuthUri(trimmed)?.secret ?: return null
        return normalizeTotpSecret(secret).takeIf { it.isNotEmpty() }
    }
    val secret = if (trimmed.startsWith("steam://", ignoreCase = true)) {
        trimmed.substring("steam://".length)
    } else {
        trimmed
    }
    val normalized = normalizeTotpSecret(secret)
    return normalized.takeIf { isValidTotpSecret(normalized) }
}

/**
 * Fills missing authenticator orders from login ciphers that share the secret.
 * An order already stored on the authenticator entry wins.
 */
fun List<AuthenticatorEntryView>.withCipherOrders(
    ordersBySecret: Map<String, String>,
): List<AuthenticatorEntryView> {
    if (ordersBySecret.isEmpty()) return this
    return map { entry ->
        val fromCipher = if (entry.order != null) {
            null
        } else {
            ordersBySecret[normalizeTotpSecret(entry.secret)]
        }
        if (fromCipher == null) entry else entry.copy(order = fromCipher)
    }
}

/**
 * Hidden order fields on login ciphers, keyed by the normalized TOTP secret.
 */
suspend fun loginOrdersBySecret(vault: VaultRepository): Map<String, String> = runCatching {
    val ids = vault.getValidTotpCipherIds(isPremium = true, time = Instant.now())
    buildMap {
        for (id in ids) {
            val match = readTotpCipher(vault, id)
            val order = match?.first?.totpOrderValue()
            if (match != null && order != null) put(match.second, order)
        }
    }
}.getOrDefault(emptyMap())

/**
 * Writes [ordersBySecret] onto matching login ciphers. Failures stay local.
 */
suspend fun writeLoginOrders(vault: VaultRepository, ordersBySecret: Map<String, String>) {
    if (ordersBySecret.isEmpty()) return
    runCatching {
        val ids = vault.getValidTotpCipherIds(isPremium = true, time = Instant.now())
        for (id in ids) {
            val match = readTotpCipher(vault, id)
            val order = match?.second?.let { ordersBySecret[it] }
            if (match != null && order != null && match.first.totpOrderValue() != order) {
                vault.updateCipher(id, match.first.withTotpOrder(order))
            }
        }
    }
}

private suspend fun readTotpCipher(
    vault: VaultRepository,
    id: String,
): Pair<CipherView, String>? {
    val cipher = (vault.getCipher(id) as? GetCipherResult.Success)?.cipherView
    val secret = cipher?.let { totpSecretKey(it.login?.totp) }
    return if (cipher == null || cipher.deletedDate != null || secret == null) {
        null
    } else {
        cipher to secret
    }
}
