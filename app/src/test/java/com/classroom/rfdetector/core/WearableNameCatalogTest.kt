package com.classroom.rfdetector.core

import org.junit.Assert.*
import org.junit.Test

class WearableNameCatalogTest {
    private val resolver = DeviceIdentityResolver(emptyMap(), mapOf(76 to "Apple, Inc."))

    @Test fun glassesNamesPreserveOnlyAdvertisedModelDetails() {
        val rayBan = WearableNameCatalog.match("Lee's Ray-Ban Meta (Gen 2)")!!
        assertEquals(WearableKind.SMART_GLASSES, rayBan.kind)
        assertEquals("Ray-Ban / Meta", rayBan.brand)
        assertEquals("Ray-Ban Meta (Gen 2)", rayBan.model)
        assertEquals("Ray-Ban Meta", WearableNameCatalog.match("Ray-Ban Meta")!!.model)
        assertEquals("Meta Ray-Ban Display", WearableNameCatalog.match("Meta Ray-Ban Display")!!.model)
        assertEquals("Oakley Meta Vanguard", WearableNameCatalog.match("Oakley Meta Vanguard")!!.model)
    }

    @Test fun koreanAndFullWidthNamesAreRecognized() {
        assertEquals(WearableKind.SMART_GLASSES, WearableNameCatalog.match("준석의 레이밴 메타")!!.kind)
        assertEquals(WearableKind.SMARTWATCH, WearableNameCatalog.match("민수의갤럭시 워치 울트라")!!.kind)
        assertEquals("Apple Watch Series 10", WearableNameCatalog.match("Ａｐｐｌｅ Ｗａｔｃｈ Ｓｅｒｉｅｓ １０")!!.model)
        assertEquals(WearableKind.CAMERA, WearableNameCatalog.match("GoPro HERO13 Black")!!.kind)
    }

    @Test fun genericWordsBrandOnlyAndPartialNamesDoNotIdentifyHardware() {
        listOf("Meta", "Ray-Ban", "Wayfarer", "glasses", "Smart Watch", "Watch", "HERO13", "Camera", "NotApple Watch", "Galaxy Watchdog").forEach {
            assertNull("Must not identify $it", WearableNameCatalog.match(it))
        }
        val companyOnly = resolver.resolve("DA:11:22:33:44:55", null, setOf(ScanSource.BLE), setOf(76))
        assertNull(companyOnly.wearableNameMatch)
        assertNull(companyOnly.modelHint)
    }

    @Test fun wifiSsidCannotBecomeGlassesAndCompanyCannotOverrideCandidate() {
        val wifi = resolver.resolve("sim", "Ray-Ban Meta", setOf(ScanSource.WIFI), emptySet())
        assertNull(wifi.wearableNameMatch)
        assertNull(wifi.modelHint)
        val ble = resolver.resolve("sim", "Ray-Ban Meta", setOf(ScanSource.BLE), setOf(76))
        assertEquals("Ray-Ban / Meta", ble.wearableNameMatch!!.brand)
        assertEquals(listOf("Apple, Inc."), ble.advertisedCompanies)
        assertEquals("Ray-Ban Meta", ble.modelHint)
    }
}
