package com.classroom.rfdetector.core

import org.junit.Assert.*
import org.junit.Test

class AudioNameCatalogTest {
    @Test fun extractsFullVariantsAndKoreanNamesWithoutInventingGenerations() {
        val cases = listOf(
            "민수의 에어팟 프로2" to Triple("Apple", "에어팟 프로2", AudioKind.EARPHONES),
            "민수의에어팟프로2세대" to Triple("Apple", "에어팟프로2세대", AudioKind.EARPHONES),
            "AirPods Pro 2" to Triple("Apple", "AirPods Pro 2", AudioKind.EARPHONES),
            "AirPods Pro (2nd generation)" to Triple("Apple", "AirPods Pro (2nd generation)", AudioKind.EARPHONES),
            "AirPods Pro 2세대" to Triple("Apple", "AirPods Pro 2세대", AudioKind.EARPHONES),
            "AirPods Pro Gen 2" to Triple("Apple", "AirPods Pro Gen 2", AudioKind.EARPHONES),
            "AirPods Max" to Triple("Apple", "AirPods Max", AudioKind.HEADPHONES),
            "갤럭시 버즈3 프로" to Triple("Samsung", "갤럭시 버즈3 프로", AudioKind.EARPHONES),
            "Galaxy Buds2 Pro" to Triple("Samsung", "Galaxy Buds2 Pro", AudioKind.EARPHONES),
            "Sony WH-1000XM5" to Triple("Sony", "WH-1000XM5", AudioKind.HEADPHONES),
            "Sony WF-1000XM5" to Triple("Sony", "WF-1000XM5", AudioKind.EARPHONES),
            "Bose QuietComfort Ultra Earbuds" to Triple("Bose", "Bose QuietComfort Ultra Earbuds", AudioKind.EARPHONES),
            "Jabra Elite 10 Gen 2" to Triple("Jabra", "Jabra Elite 10 Gen 2", AudioKind.EARPHONES),
            "Pixel Buds Pro 2" to Triple("Google", "Pixel Buds Pro 2", AudioKind.EARPHONES),
            "Beats Studio Buds +" to Triple("Beats", "Beats Studio Buds +", AudioKind.EARPHONES),
            "Nothing Ear (a)" to Triple("Nothing", "Nothing Ear (a)", AudioKind.EARPHONES),
            "soundcore Liberty 4 NC" to Triple("soundcore", "soundcore Liberty 4 NC", AudioKind.EARPHONES),
        )
        cases.forEach { (name, expected) ->
            val match = AudioNameCatalog.match(name)
            assertNotNull(name, match)
            assertEquals(name, expected, Triple(match!!.brand, match.model, match.kind))
        }
        assertEquals("AirPods Pro", AudioNameCatalog.match("AirPods Pro")!!.model)
    }

    @Test fun genericOrEmbeddedWordsAreNotProductIdentification() {
        listOf("Elite router", "Elite 10", "NotAirPods", "AirPodsProMax", "smartwatch", "Buds", "Ear", "my pixel router").forEach {
            assertNull(it, AudioNameCatalog.match(it))
        }
        assertNull(AudioNameCatalog.match(null))
    }

    @Test fun unicodeCompatibilityNamesAreNormalized() {
        assertEquals("AirPods Pro 2", AudioNameCatalog.match("ＡｉｒＰｏｄｓ Ｐｒｏ ２")!!.model)
    }

    @Test fun identitySearchUsesAllEvidenceAndCompactMacAndKoreanAliases() {
        val identity = DeviceIdentityResolver(mapOf("001122" to "Interface Vendor"), mapOf(76 to "Apple, Inc.", 117 to "Samsung Electronics"))
            .resolve("00:11:22:33:44:55", "Galaxy Buds2 Pro", setOf(ScanSource.BLE), setOf(76, 117))
        assertTrue(identity.matchesQuery("삼성 buds2", "Galaxy Buds2 Pro", "00:11:22:33:44:55"))
        assertTrue(identity.matchesQuery("001122334455", "device", "00:11:22:33:44:55"))
        assertTrue(identity.matchesQuery("Interface Vendor", "device", "00:11:22:33:44:55"))
        assertTrue(identity.matchesQuery("Samsung", "device", "00:11:22:33:44:55"))
        assertFalse(identity.matchesQuery("삼성 Sony", "device", "00:11:22:33:44:55"))
        assertFalse(identity.matchesQuery(":--", "device", "00:11:22:33:44:55"))
    }

    @Test fun nameOnlyBrandIsMarkedAsCandidateAndNeverAppliedToWifiSsid() {
        val resolver = DeviceIdentityResolver(emptyMap(), emptyMap())
        val ble = resolver.resolve("random", "에어팟 프로", setOf(ScanSource.BLE), emptySet())
        assertEquals("Apple · 이름 기반 후보", ble.summary)
        assertEquals(AudioKind.EARPHONES, ble.audioNameMatch!!.kind)
        assertTrue(ble.matchesQuery("airpods pro", "에어팟 프로", "random"))
        val wifi = resolver.resolve("random", "AirPods Pro", setOf(ScanSource.WIFI), emptySet())
        assertNull(wifi.audioNameMatch)
        assertNull(wifi.modelHint)
    }

    @Test fun mismatchedCompanyAndNameAreVisibleAndUnknownIdDoesNotHideKnownCompany() {
        val resolver = DeviceIdentityResolver(emptyMap(), mapOf(76 to "Apple, Inc."))
        val identity = resolver.resolve("random", "Galaxy Buds3 Pro", setOf(ScanSource.BLE), setOf(1, 76))
        assertTrue(identity.summary.startsWith("Apple"))
        assertNotNull(identity.evidenceNote)
        assertEquals("Galaxy Buds3 Pro", identity.modelHint)
        assertNull(audioEvidenceNote(identity.audioNameMatch, setOf(76, 117)))
    }
}
