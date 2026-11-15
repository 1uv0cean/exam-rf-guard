package com.classroom.rfdetector.ui

import org.junit.Assert.*
import org.junit.Test

class WifiChannelTest {
    @Test fun mapsBandEdgesAndSpecialChannels() {
        assertEquals(1, channelNumber(2412))
        assertEquals(13, channelNumber(2472))
        assertEquals(14, channelNumber(2484))
        assertEquals(36, channelNumber(5180))
        assertEquals(2, channelNumber(5935))
        assertEquals(1, channelNumber(5955))
        assertEquals(233, channelNumber(7115))
        assertNull(channelNumber(2413))
        assertNull(channelNumber(4000))
    }
    @Test fun sixGhzIsNeverLabelledAsFiveGhz() {
        assertEquals("6 GHz · 채널 1", channelLabel(5955))
        assertEquals("5 GHz · 채널 36", channelLabel(5180))
    }
}
