// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.integration

import com.zairenkaiprjctx.integration.core.CoreCall
import com.zairenkaiprjctx.integration.core.CoreClient
import com.zairenkaiprjctx.integration.core.CoreVersion
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Core response envelope and the `modules` result. `modules-installed.json` is Core's own
 * inventory and `Response` serialising the module.prop packaged in zairenkai.zip
 * (capture-contracts.sh, core_fixture_main.rs): Core writes `version` as `{major, minor, patch}`.
 */
class CoreContractTest {
    private val base = "'/data/adb/modules/zairenkai/bin/zcored' 'ctl' '--dir' '/data/adb/zairenkai/core'"

    private fun core(method: String, stdout: String, exit: Int = 0): CoreClient {
        val runner = FakeRunner()
        runner.on(su("$base '$method'"), exit, stdout)
        return CoreClient(grantedRoot(runner))
    }

    @Test
    fun installedModuleVersionIsCoresVersionStruct() {
        val mods = (core("modules", contract("core/modules-installed.json")).modules() as CoreCall.Ok).value
        val m = mods.single { it.id == com.zairenkaiprjctx.integration.core.CorePaths.MODULE_ID }
        assertEquals(CoreVersion(0, 3, 0), m.version) // module 0.3.0: zcored, zfasd and ztune, with SHA256SUMS
        assertEquals("0.3.0", m.version.toString())
        assertTrue(m.compatible)
    }

    @Test
    fun notInstalledModulesHaveNoVersion() {
        val mods = (core("modules", contract("core/modules.json")).modules() as CoreCall.Ok).value
        assertTrue(mods.isNotEmpty())
        mods.forEach { assertNull(it.version) }
        // Core reports the single module itself; Zairenkai-Modules is no longer "no released component".
        assertEquals("not_installed", mods.single { it.id == "zairenkai" }.health)
        assertTrue(mods.none { it.id == "zairenkai_modules" })
    }

    @Test
    fun versionRoundTripsInCoresShape() {
        val producer = Json.parseToJsonElement(contract("core/modules-installed.json"))
            .jsonObject.getValue("result").jsonArray[0].jsonObject.getValue("version")
        val v = Json.decodeFromJsonElement(CoreVersion.serializer(), producer)
        assertEquals(producer, Json.parseToJsonElement(Json.encodeToString(v)))
    }

    @Test
    fun aVersionStringIsAContractMismatchNamingTheMethod() {
        val body = """{"v":1,"id":"x","ok":true,"result":[{"id":"zairenkai_core","version":"0.1.0","compatible":true,"health":"healthy","detail":"","requested_permissions":[],"owns":[]}]}"""
        val r = core("modules", body).modules()
        assertTrue(r is CoreCall.Failed)
        val detail = (r as CoreCall.Failed).detail
        assertTrue(detail, detail.contains("modules"))
        assertTrue(detail, detail.contains("version"))
    }

    @Test
    fun missingOrUnsupportedProtocolVersionIsRejected() {
        val missing = core("health", """{"id":"x","ok":true,"result":{}}""").health()
        assertTrue(missing is CoreCall.Failed)
        assertTrue((missing as CoreCall.Failed).detail.contains("protocol version"))
        val nullV = core("health", """{"v":null,"id":"x","ok":true,"result":{}}""").health()
        assertTrue(nullV is CoreCall.Failed)
        val future = core("health", """{"v":2,"id":"x","ok":true,"result":{}}""").health()
        assertTrue(future is CoreCall.Failed)
        assertTrue((future as CoreCall.Failed).detail.contains("unsupported Core protocol version 2"))
    }

    @Test
    fun missingAndMalformedPayloads() {
        assertTrue(core("modules", """{"v":1,"id":"x","ok":true}""").modules() is CoreCall.Failed)
        assertTrue(core("modules", """{"v":1,"id":"x","ok":true,"result":{"not":"a list"}}""").modules() is CoreCall.Failed)
        assertTrue(core("modules", """[1,2]""").modules() is CoreCall.Failed)
    }

    @Test
    fun structuredErrorsKeepCodeAndMessage() {
        val r = core("modules", """{"v":1,"id":"x","ok":false,"error":{"code":"permission_denied","message":"uid 1001 is not a configured Core client"}}""", exit = 1).modules()
        assertEquals(CoreCall.Error("permission_denied", "uid 1001 is not a configured Core client"), r)
    }
}
