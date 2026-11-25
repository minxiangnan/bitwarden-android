package com.x8bit.bitwarden.data.authenticator.model

/**
 * Algorithm stored with an authenticator entry. Matches the vaultwarden field values.
 */
enum class AuthenticatorAlgorithm {
    SHA1,
    SHA256,
    SHA512,
}

/**
 * How a code is generated. Steam and HOTP are stored in the encrypted notes trailer.
 */
enum class AuthenticatorKind {
    TOTP,
    HOTP,
    STEAM,
}

/**
 * Extra fields packed into the encrypted notes so they round-trip without a schema change.
 */
data class AuthenticatorMeta(
    val kind: AuthenticatorKind = AuthenticatorKind.TOTP,
    val host: String = "",
    val tags: List<String> = emptyList(),
    val counter: Long = 0,
    val deletedAt: String? = null,
)

/**
 * Decrypted authenticator entry. [undecryptable] entries must not be saved again.
 */
data class AuthenticatorEntryView(
    val id: String,
    val name: String,
    val issuer: String,
    val secret: String,
    val algorithm: AuthenticatorAlgorithm,
    val digits: Int,
    val period: Int,
    val notes: String,
    val favorite: Boolean,
    val sortOrder: Int,
    val revisionDate: String,
    val creationDate: String,
    val undecryptable: Boolean = false,
    val kind: AuthenticatorKind = AuthenticatorKind.TOTP,
    val host: String = "",
    val tags: List<String> = emptyList(),
    val counter: Long = 0,
    val deletedAt: String? = null,
)

/**
 * Fields the user edits. Encryption happens when the entry is saved.
 */
data class AuthenticatorDraft(
    val name: String,
    val issuer: String,
    val secret: String,
    val algorithm: AuthenticatorAlgorithm = AuthenticatorAlgorithm.SHA1,
    val digits: Int = DEFAULT_DIGITS,
    val period: Int = DEFAULT_PERIOD,
    val notes: String = "",
    val favorite: Boolean = false,
    val kind: AuthenticatorKind = AuthenticatorKind.TOTP,
    val host: String = "",
    val tags: List<String> = emptyList(),
    val counter: Long = 0,
    val deletedAt: String? = null,
    /** Revision the edit was based on. A newer row on the server is rejected. */
    val baseRevision: String? = null,
)

/**
 * Ciphertext row returned by `GET /authenticator`.
 */
data class AuthenticatorCipherRow(
    val id: String,
    val name: String,
    val issuer: String?,
    val secret: String,
    val algorithm: String,
    val digits: Int,
    val period: Int,
    val notes: String?,
    val favorite: Boolean,
    val sortOrder: Int,
    val revisionDate: String,
    val creationDate: String,
)

const val DEFAULT_DIGITS: Int = 6
const val DEFAULT_PERIOD: Int = 30
const val MIN_DIGITS: Int = 5
const val MAX_DIGITS: Int = 10
const val MIN_PERIOD: Int = 1
const val MAX_PERIOD: Int = 86_400
const val STEAM_DIGITS: Int = 5

/**
 * Normalizes a server algorithm string. Unknown values stay SHA1.
 */
fun normalizeAlgorithm(value: String?): AuthenticatorAlgorithm {
    return when (value?.uppercase()) {
        "SHA256" -> AuthenticatorAlgorithm.SHA256
        "SHA512" -> AuthenticatorAlgorithm.SHA512
        else -> AuthenticatorAlgorithm.SHA1
    }
}
