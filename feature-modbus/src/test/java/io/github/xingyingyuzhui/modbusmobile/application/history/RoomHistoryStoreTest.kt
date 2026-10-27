package io.github.xingyingyuzhui.modbusmobile.application.history

import androidx.room.Room
import io.github.xingyingyuzhui.modbusmobile.application.history.room.HistoryDatabase
import io.github.xingyingyuzhui.modbusmobile.application.history.room.RoomHistoryStore
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.toList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RoomHistoryStoreTest {
    private val database = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication(),
        HistoryDatabase::class.java,
    ).allowMainThreadQueries().build()
    private val store = RoomHistoryStore(database)

    @After
    fun close() {
        database.close()
    }

    @Test
    fun pagingUsesOrderedCompositeIndexWithoutTemporarySort() = runTest {
        val sql = "EXPLAIN QUERY PLAN SELECT * FROM samples WHERE sessionId = 1 AND id <= 700 AND " +
            "(sampleAtMs > 10 OR (sampleAtMs = 10 AND id > 256)) ORDER BY sampleAtMs ASC, id ASC LIMIT 256"
        val plan = database.openHelper.readableDatabase.query(sql).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(3)) }.joinToString("\n")
        }
        assertTrue(plan, plan.contains("index_samples_sessionId_sampleAtMs_id"))
        assertTrue(plan, !plan.contains("TEMP B-TREE"))
    }

    @Test
    fun versionOneDatabaseMigratesWithoutLosingSessionsOrSamples() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val name = "history-migration-test.db"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, 0, null).use { old ->
            old.execSQL("CREATE TABLE sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, role TEXT NOT NULL, target TEXT NOT NULL, epoch INTEGER NOT NULL, startedAtMs INTEGER NOT NULL, endedAtMs INTEGER, retained INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE samples (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sessionId INTEGER NOT NULL, pointId TEXT NOT NULL, sampleAtMs INTEGER NOT NULL, recordedAtMs INTEGER NOT NULL, epoch INTEGER NOT NULL, quality TEXT NOT NULL, engineering REAL, text TEXT, FOREIGN KEY(sessionId) REFERENCES sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            old.execSQL("CREATE INDEX index_samples_sessionId ON samples(sessionId)")
            old.execSQL("CREATE INDEX index_samples_pointId_sampleAtMs ON samples(pointId, sampleAtMs)")
            old.execSQL("INSERT INTO sessions VALUES (7, 'MASTER', 'USB', 3, 10, 20, 1)")
            old.execSQL("INSERT INTO samples VALUES (8, 7, 'temperature', 11, 12, 3, 'OK', 21.5, 'saved')")
            old.version = 1
        }
        val upgraded = Room.databaseBuilder(context, HistoryDatabase::class.java, name)
            .addMigrations(HistoryDatabase.MIGRATION_1_2).allowMainThreadQueries().build()
        try {
            val migrated = RoomHistoryStore(upgraded)
            assertTrue(migrated.sessions().single().retained)
            assertEquals(7L, migrated.sessions().single().id)
            assertEquals("saved", migrated.samples(7).single().text)
            assertEquals(21.5, migrated.samples(7).single().engineering)
            val pages = migrated.samplePages(7).toList()
            assertEquals("temperature", pages.single().single().pointId)
        } finally {
            upgraded.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun deletedPagesBecomeReusableCapacity() = runTest {
        val session = store.openSession("MASTER", "DEMO", 1, 0)
        store.insertSamples((0 until 600).map {
            HistorySample(session.id, "point-$it", it.toLong(), 0, 1, SampleQuality.OK, 1.0, "x".repeat(1000))
        })
        val used = store.usageBytes()
        store.deleteSessions(listOf(session.id))
        assertTrue("空闲数据库页应从使用容量扣除", store.usageBytes() < used)
    }

    @Test
    fun pagedExportKeepsTimeAndIdOrderAndHasAFixedUpperBoundary() = runTest {
        val session = store.openSession("MASTER", "DEMO", 1, 0)
        val rows = (0 until 700).map {
            HistorySample(session.id, "p-$it", (it % 11).toLong(), 0, 1, SampleQuality.OK, it.toDouble(), null)
        }
        store.insertSamples(rows)
        val output = mutableListOf<HistorySample>()
        store.samplePages(session.id).collect { page ->
            assertTrue(page.size <= 256)
            if (output.isEmpty()) {
                // 导出进行时持续采样，不延长本轮导出，也不重复/漏掉同时间戳行。
                store.insertSamples(listOf(rows.first().copy(pointId = "late", sampleAtMs = 999)))
            }
            output += page
        }
        assertEquals(rows.sortedBy { it.sampleAtMs }, output)
        assertTrue(store.samplePages(-1).toList().isEmpty())
    }

    @Test
    fun sessionAndSamplesRoundTripAndAKeptSessionSurvivesDelete() = runTest {
        val first = store.openSession("MASTER", "USB", 4, 1_000)
        store.insertSamples(
            listOf(
                HistorySample(first.id, "temp", 1_000, 1_100, 4, SampleQuality.OK, 21.5, null),
                HistorySample(first.id, "temp", 6_000, 6_100, 4, SampleQuality.TIMEOUT, null, null),
            ),
        )
        store.closeSession(first.id, 9_000)
        val kept = store.openSession("SLAVE", "LAB", 5, 2_000)
        store.setRetained(kept.id, true)
        store.insertSamples(listOf(HistorySample(kept.id, "coil", 2_000, 2_100, 5, SampleQuality.OK, null, "1")))
        store.deleteSessions(listOf(first.id, kept.id))

        assertEquals(listOf(kept.id), store.sessions().map { it.id })
        assertTrue(store.sessions().single().retained)
        assertEquals(1, store.samples(kept.id).size)
        assertEquals(0, store.samples(first.id).size)
        assertTrue(store.usageBytes() > 0)
    }

    @Test
    fun restartClosesOnlySessionsThatStartedBeforeThisProcess() = runTest {
        val previous = store.openSession("MASTER", "DEMO", 1, 10)
        val current = store.openSession("MASTER", "DEMO", 2, 50)
        store.closeDangling(endedAtMs = 40, startedBeforeMs = 40)
        assertEquals(40L, store.sessions().first { it.id == previous.id }.endedAtMs)
        assertNull(store.sessions().first { it.id == current.id }.endedAtMs)
    }
}
