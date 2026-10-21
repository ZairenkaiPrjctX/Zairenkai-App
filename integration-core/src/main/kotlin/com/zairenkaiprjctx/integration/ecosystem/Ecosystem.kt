// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.integration.ecosystem

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The organization inventory (`integration/organization-repositories.json`), packaged as an
 * asset so the app can show every repository's disposition with its audited revision.
 */
@Serializable
data class RepositoryRecord(
    val name: String,
    @SerialName("full_name") val fullName: String,
    val url: String,
    @SerialName("default_branch") val defaultBranch: String,
    @SerialName("audited_branch") val auditedBranch: String,
    @SerialName("audited_commit") val auditedCommit: String,
    @SerialName("code_present") val codePresent: Boolean,
    val languages: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val disposition: String,
    @SerialName("integration_status") val integrationStatus: String,
    @SerialName("app_capabilities") val appCapabilities: List<String> = emptyList(),
    @SerialName("integration_mechanism") val integrationMechanism: String? = null,
    @SerialName("runtime_requirements") val runtimeRequirements: String? = null,
    val license: String,
    val evidence: String,
)

@Serializable
data class OutsideRepository(@SerialName("full_name") val fullName: String, val reason: String)

@Serializable
data class OrganizationInventory(
    val schema: Int,
    val organization: String,
    val surveyed: String,
    val discovered: Int,
    val repositories: List<RepositoryRecord>,
    @SerialName("outside_organization_not_inspected") val outside: List<OutsideRepository> = emptyList(),
) {
    fun byName(name: String): RepositoryRecord? = repositories.firstOrNull { it.name == name }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): OrganizationInventory {
            val inv = json.decodeFromString<OrganizationInventory>(text)
            require(inv.schema == 1) { "unsupported inventory schema ${inv.schema}" }
            require(inv.discovered == inv.repositories.size) { "discovered count does not match the list" }
            require(inv.repositories.all { it.auditedCommit.matches(Regex("^[0-9a-f]{40}$")) }) {
                "every repository needs a full audited commit"
            }
            return inv
        }
    }
}
