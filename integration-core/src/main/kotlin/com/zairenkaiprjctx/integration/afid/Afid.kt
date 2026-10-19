package com.zairenkaiprjctx.integration.afid

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Device candidates from Zairenkai-Database-Source (AFID), packaged by
 * `integration/scripts/afid_asset.py`. Every record is community-reported and unverified; the
 * data is CC BY-SA 3.0 and the attribution must be shown wherever it is displayed.
 */
@Serializable
data class AfidSoc(val vendor: String? = null, val platform: String? = null, val architecture: String? = null)

@Serializable
data class AfidDevice(
    val codename: String,
    @SerialName("brand_id") val brandId: String,
    @SerialName("product_names") val productNames: List<String> = emptyList(),
    @SerialName("model_numbers") val modelNumbers: List<String> = emptyList(),
    val soc: AfidSoc = AfidSoc(),
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("source_url") val sourceUrl: String,
)

@Serializable
data class AfidGeneratedFrom(val repository: String, val commit: String, val path: String)

@Serializable
data class AfidLicense(val spdx: String, val attribution: String, @SerialName("share_alike") val shareAlike: Boolean)

@Serializable
data class AfidCatalog(
    val schema: Int,
    @SerialName("generated_from") val generatedFrom: AfidGeneratedFrom,
    val license: AfidLicense,
    val verification: String,
    val note: String = "",
    val count: Int,
    @SerialName("duplicate_codenames") val duplicateCodenames: List<String> = emptyList(),
    val devices: List<AfidDevice>,
) {
    /** Candidates whose codename equals this device's `Build.DEVICE` (case-insensitive). */
    fun lookup(deviceCodename: String): List<AfidDevice> {
        val key = deviceCodename.trim().lowercase()
        if (key.isEmpty()) return emptyList()
        return devices.filter { it.codename == key }
    }

    /** Substring search over codename, product names, model numbers and SoC platform. */
    fun search(query: String, limit: Int = 50): List<AfidDevice> {
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList()
        return devices.asSequence().filter { d ->
            d.codename.contains(q) ||
                d.productNames.any { it.lowercase().contains(q) } ||
                d.modelNumbers.any { it.lowercase().contains(q) } ||
                (d.soc.platform?.lowercase()?.contains(q) ?: false)
        }.take(limit).toList()
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): AfidCatalog {
            val c = json.decodeFromString<AfidCatalog>(text)
            require(c.schema == 1) { "unsupported AFID asset schema ${c.schema}" }
            require(c.count == c.devices.size) { "count ${c.count} does not match ${c.devices.size} records" }
            require(c.verification == "unverified") { "unexpected verification state ${c.verification}" }
            require(c.license.spdx == "CC-BY-SA-3.0") { "unexpected licence ${c.license.spdx}" }
            return c
        }
    }
}
