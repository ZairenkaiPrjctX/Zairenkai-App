package com.zairenkaiprjctx.integration.systemmodule

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Module-signing keys the app trusts (`integration/module-trust.json`, reviewed in Git and
 * copied into the APK). This identity is separate from the APK signing key: a module zip runs
 * as root, so its release must be signed by a key whose private half never leaves the release
 * signing process.
 */
@Serializable
data class TrustedKey(
    /** SHA-256 (hex) of the DER SubjectPublicKeyInfo. */
    @SerialName("key_id") val keyId: String,
    /** Base64 DER SubjectPublicKeyInfo of a P-256 key. */
    @SerialName("public_key") val publicKey: String,
    val owner: String,
    val added: String,
)

@Serializable
data class ModuleTrust(val schema: Int, val algorithm: String, val keys: List<TrustedKey>) {
    companion object {
        const val ALGORITHM = "ECDSA_P256_SHA256"
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): ModuleTrust {
            val t = json.decodeFromString<ModuleTrust>(text)
            require(t.schema == 1) { "unsupported trust schema ${t.schema}" }
            require(t.algorithm == ALGORITHM) { "unsupported algorithm ${t.algorithm}" }
            for (k in t.keys) {
                val der = Base64.getDecoder().decode(k.publicKey)
                require(sha256Hex(der) == k.keyId) { "key_id of ${k.owner} does not match its public key" }
            }
            return t
        }

        fun sha256Hex(b: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
    }
}

/** `system-modules.json.sig`: a detached signature over the exact catalog bytes. */
@Serializable
data class CatalogSignatureFile(
    val schema: Int,
    val algorithm: String,
    @SerialName("key_id") val keyId: String,
    /** Base64 DER ECDSA signature (as `openssl dgst -sha256 -sign` writes it). */
    val signature: String,
)

sealed interface SignatureVerdict {
    data class Verified(val keyId: String, val owner: String) : SignatureVerdict

    /** The release carries no signature: built without the module-signing key. */
    data object Unsigned : SignatureVerdict

    /** No key is trusted: the app cannot authenticate any module release. */
    data object NoTrustedKey : SignatureVerdict

    data class UnknownKey(val keyId: String) : SignatureVerdict
    data class Invalid(val detail: String) : SignatureVerdict
}

/** Checks the catalog signature. Pure: inputs are bytes and the trust list. */
object CatalogSignature {
    private val json = Json { ignoreUnknownKeys = true }

    fun verify(catalog: ByteArray, signatureFile: String?, trust: ModuleTrust): SignatureVerdict {
        if (trust.keys.isEmpty()) return SignatureVerdict.NoTrustedKey
        val sig = signatureFile ?: return SignatureVerdict.Unsigned
        val parsed = try {
            json.decodeFromString<CatalogSignatureFile>(sig)
        } catch (e: Exception) {
            return SignatureVerdict.Invalid("unreadable signature file: ${e.message}")
        }
        if (parsed.schema != 1 || parsed.algorithm != ModuleTrust.ALGORITHM) return SignatureVerdict.Invalid("unsupported signature format")
        val key = trust.keys.firstOrNull { it.keyId == parsed.keyId } ?: return SignatureVerdict.UnknownKey(parsed.keyId)
        return try {
            val pub: PublicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(key.publicKey)))
            val v = Signature.getInstance("SHA256withECDSA")
            v.initVerify(pub)
            v.update(catalog)
            if (v.verify(Base64.getDecoder().decode(parsed.signature))) SignatureVerdict.Verified(key.keyId, key.owner)
            else SignatureVerdict.Invalid("the signature does not match the catalog")
        } catch (e: Exception) {
            SignatureVerdict.Invalid("${e.javaClass.simpleName}: ${e.message}")
        }
    }
}

/** The packaged module release: the catalog, and whether its signature is trusted. */
data class ModuleRelease(val catalog: ModuleCatalog, val signature: SignatureVerdict) {
    val trusted: Boolean get() = signature is SignatureVerdict.Verified

    /** Why installation is blocked, in words, or null when the release is trusted. */
    val blocker: String?
        get() = when (val s = signature) {
            is SignatureVerdict.Verified -> null
            SignatureVerdict.NoTrustedKey ->
                "No module-signing key is trusted yet (integration/module-trust.json is empty). A maintainer must create the release signing key and add its public key before modules can be installed."
            SignatureVerdict.Unsigned -> "This build's module catalog is not signed (built without the module-signing key)."
            is SignatureVerdict.UnknownKey -> "The catalog is signed by key ${s.keyId.take(16)}…, which this app does not trust."
            is SignatureVerdict.Invalid -> "The catalog signature is invalid: ${s.detail}."
        }

    companion object {
        fun load(catalog: ByteArray, signatureFile: String?, trust: ModuleTrust): ModuleRelease =
            ModuleRelease(ModuleCatalog.parse(catalog.decodeToString()), CatalogSignature.verify(catalog, signatureFile, trust))
    }
}
