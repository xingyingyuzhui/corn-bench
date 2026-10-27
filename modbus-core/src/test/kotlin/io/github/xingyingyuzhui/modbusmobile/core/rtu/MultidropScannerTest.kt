package io.github.xingyingyuzhui.modbusmobile.core.rtu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MultidropScannerTest {
    @Test
    fun f01_foreignExchangeThenLocalRequestInOneChunk() {
        val scanner = openScanner()
        val foreign = f("02 03 00 00 00 02")
        val response = f("02 03 04 00 0A 00 0B")
        val local = f("01 03 00 00 00 01")
        val frames = frameEvents(Feed(scanner).at(0, foreign + response + local))
        assertIs<ScanEvent.ForeignRequest>(frames[0])
        val reply = assertIs<ScanEvent.ForeignResponse>(frames[1])
        assertTrue(reply.solicited)
        assertIs<ScanEvent.LocalRequest>(frames[2])
        assertEquals(3, frames.size)
        assertEquals(1, scanner.stats.closed(CloseReason.RESPONDED))
    }

    @Test
    fun f02_secondIdenticalFc06IsTheForeignResponse() {
        val scanner = openScanner()
        val frame = f("02 06 00 01 00 05")
        val frames = frameEvents(Feed(scanner).at(0, frame) + Feed(scanner).at(1, frame))
        assertIs<ScanEvent.ForeignRequest>(frames[0])
        val response = assertIs<ScanEvent.ForeignResponse>(frames[1])
        assertTrue(response.solicited)
        assertEquals(2, frames.size)
    }

    @Test
    fun f03_localWriteInsideForeignResponseIsNotARequest() {
        val scanner = openScanner()
        val feed = Feed(scanner)
        val embedded = f("01 06 00 00 00 07")
        val events = feed.at(0, f("02 03 00 00 00 04")) +
            feed.at(1, f("02 03 08 ${embedded.toHexSpaced()}"))
        assertTrue(events.none { it is ScanEvent.LocalRequest })
        val response = events.filterIsInstance<ScanEvent.ForeignResponse>().single()
        assertTrue(response.solicited)
    }

    @Test
    fun f04_noiseBadCrcTruncationByteAtATimeAndBufferCap() {
        val config = serialConfig()
        val legal = f("01 03 00 00 00 01")
        val follow = f("01 06 00 01 00 09")

        val mixed = openScanner()
        val mixedFeed = Feed(mixed)
        val mixedEvents = mixedFeed.at(0, byteArrayOf(0xFF.toByte(), 0x00) + legal + follow)
        val recognized = frameEvents(mixedEvents).map { it.rawHex() }
        assertEquals(listOf(legal.toHexSpaced(), follow.toHexSpaced()), recognized)
        assertTrue(mixed.stats.dropped(DropReason.UNKNOWN_UNFRAMED_FC) >= 1)
        assertTrue(mixed.pendingBytes <= config.maxBufferBytes)

        val bad = openScanner()
        val corrupt = legal.copyOf().also { it[it.lastIndex] = (it[it.lastIndex].toInt() xor 0xFF).toByte() }
        Feed(bad).at(0, corrupt)
        assertTrue(bad.stats.dropped(DropReason.CRC) >= 1)
        assertTrue(frameEvents(emptyList()).isEmpty())
        assertTrue(bad.pendingBytes == 0 || bad.stats.dropped(DropReason.CRC) + bad.stats.dropped(DropReason.UNKNOWN_UNFRAMED_FC) > 0)

        val truncated = openScanner()
        val truncFeed = Feed(truncated)
        truncFeed.at(0, legal.copyOfRange(0, 5))
        val early = truncFeed.tick(10)
        assertTrue(early.none { it is ScanEvent.Dropped && it.reason == DropReason.STALE_PARTIAL_FRAME })
        val budgetMs = config.frameAssemblyBudgetNs / 1_000_000L + 1
        val expired = truncFeed.tick(budgetMs)
        assertTrue(expired.any { it is ScanEvent.Dropped && it.reason == DropReason.STALE_PARTIAL_FRAME })

        val paced = openScanner()
        val pacedFeed = Feed(paced)
        val pacedEvents = legal.indices.flatMap { index -> pacedFeed.at(index.toLong(), byteArrayOf(legal[index])) }
        assertEquals(legal.toHexSpaced(), frameEvents(pacedEvents).single().rawHex())

        val burst = openScanner()
        val burstEvents = frameEvents(Feed(burst).at(0, legal + follow))
        assertEquals(2, burstEvents.size)

        val flooded = openScanner()
        Feed(flooded).at(0, ByteArray(config.maxBufferBytes + 300) { 0x00 })
        assertTrue(flooded.pendingBytes <= config.maxBufferBytes)
        assertTrue(flooded.stats.dropped(DropReason.BUFFER_OVERFLOW) >= 1)
    }

    @Test
    fun f10_unmodeledFunctionIsDroppedWithoutAFrame() {
        val scanner = openScanner()
        val events = Feed(scanner).at(0, f("01 41 AA BB"))
        assertTrue(events.none { it is ScanEvent.LocalRequest || it is ScanEvent.Ambiguous })
        assertTrue(scanner.stats.dropped(DropReason.UNKNOWN_UNFRAMED_FC) >= 1)
    }

    @Test
    fun f16_localRequestPreemptsForeignTransaction() {
        val scanner = openScanner(timeoutMs = 1_000)
        val feed = Feed(scanner)
        val first = feed.at(0, f("02 03 00 00 00 02"))
        val second = feed.at(50, f("01 03 00 00 00 01"))
        assertIs<ScanEvent.ForeignRequest>(frameEvents(first).single())
        assertIs<ScanEvent.LocalRequest>(frameEvents(second).single())
        val closed = (first + second).filterIsInstance<ScanEvent.TransactionClosed>().single()
        assertEquals(CloseReason.PREEMPTED_BY_NEW_REQUEST, closed.reason)
        assertTrue((first + second).none { it is ScanEvent.TransactionClosed && it.reason == CloseReason.RESPONSE_TIMEOUT })
    }

    @Test
    fun f17_incompleteForeignResponseHidesEmbeddedLocalRequest() {
        val config = serialConfig()
        val scanner = openScanner()
        val feed = Feed(scanner)
        val local = f("01 06 00 00 00 07")
        val partial = byteArrayOf(0x02, 0x03, 0xF0.toByte()) + local
        feed.at(0, f("02 03 00 00 00 04"))
        val waiting = feed.at(1, partial) + feed.tick(100)
        assertTrue(waiting.none { it is ScanEvent.LocalRequest || it is ScanEvent.Dropped })
        val budgetMs = config.frameAssemblyBudgetNs / 1_000_000L + 3
        val expired = feed.tick(budgetMs)
        assertTrue(expired.any { it is ScanEvent.Dropped && it.reason == DropReason.STALE_PARTIAL_FRAME })
        assertTrue((waiting + expired).none { it is ScanEvent.LocalRequest })
    }

    @Test
    fun f18_truncatedLocalRequestExpires() {
        val config = serialConfig()
        val scanner = openScanner()
        val feed = Feed(scanner)
        feed.at(0, f("01 03 00 00 00 01").copyOfRange(0, 5))
        assertTrue(feed.tick(10).none { it is ScanEvent.LocalRequest })
        val budgetMs = config.frameAssemblyBudgetNs / 1_000_000L + 1
        val expired = feed.tick(budgetMs)
        assertTrue(expired.any { it is ScanEvent.Dropped && it.reason == DropReason.STALE_PARTIAL_FRAME })
        assertTrue(expired.none { it is ScanEvent.LocalRequest })
    }

    @Test
    fun f19_longFc10ArrivesIn16ByteChunks() {
        val config = serialConfig()
        val scanner = openScanner()
        val feed = Feed(scanner)
        val payload = ByteArray(253)
        payload[0] = 0x01
        payload[1] = 0x10
        payload[5] = 123
        payload[6] = 246.toByte()
        val frame = Crc16Modbus.append(payload)
        assertEquals(255, frame.size)
        val events = mutableListOf<ScanEvent>()
        frame.toList().chunked(16).forEachIndexed { index, part ->
            val at = index * 16L
            events += feed.at(at, part.toByteArray())
            events += feed.tick(at)
        }
        val request = frameEvents(events).single()
        assertIs<ScanEvent.LocalRequest>(request)
        assertEquals(frame.toHexSpaced(), request.rawHex())
        assertEquals(0, scanner.stats.dropped(DropReason.STALE_PARTIAL_FRAME))
        assertTrue(240 < config.frameAssemblyBudgetNs / 1_000_000L)
    }

    @Test
    fun f25_badCrcMakesTheNextLocalWriteAmbiguous() {
        val scanner = openScanner()
        val feed = Feed(scanner)
        val bad = byteArrayOf(0x01, 0x03, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00)
        val write = f("01 06 00 00 00 07")
        val next = f("01 03 00 00 00 01")
        val events = feed.at(0, bad + write + next)
        val frames = frameEvents(events)
        val ambiguous = assertIs<ScanEvent.Ambiguous>(frames[0])
        assertEquals("untrusted-start", ambiguous.reason)
        assertEquals(write.toHexSpaced(), ambiguous.rawHex())
        assertIs<ScanEvent.LocalRequest>(frames[1])
        assertEquals(next.toHexSpaced(), frames[1].rawHex())
    }
}

private fun ByteArray.toHexSpaced(): String = joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
