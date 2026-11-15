package com.classroom.rfdetector.core

import org.junit.Assert.*
import org.junit.Test

class SnapshotArchiveTest {
    private fun snapshot(id: String, area: String, label: String, time: Long, sim: Boolean = false) = SignalSnapshot(id, area, label, time, 0, sim, emptyList())
    private val roomA = snapshot("a", "제1시험실", "입실 전", 1000)
    private val roomAAfter = snapshot("after", "제1시험실", "시험 중", 3000)
    private val roomB = snapshot("b", "제2시험실", "입실 전", 2000)
    private val simulated = roomA.copy(id = "sim", simulation = true)

    @Test fun archiveSearchCombinesRoomAndStageAndPreservesNewestFirst() {
        val rows = listOf(roomA, roomB, roomAAfter, simulated)
        assertEquals(listOf(roomAAfter, roomB, roomA), filterSnapshotArchive(rows, false, ""))
        assertEquals(listOf(roomA), filterSnapshotArchive(rows, false, "제1 입실"))
        assertEquals(listOf(simulated), filterSnapshotArchive(rows, true, "제1"))
    }
    @Test fun roomFilterDoesNotExposeOtherRoomsOrMutateTheArchive() {
        val rows = listOf(roomB, roomAAfter, roomA)
        assertEquals(listOf(roomAAfter, roomA), filterSnapshotArchive(rows, false, "", " 제1시험실 "))
        assertTrue(filterSnapshotArchive(rows, false, "", "제3시험실").isEmpty())
        assertEquals(listOf(roomB, roomAAfter, roomA), rows)
    }
}
