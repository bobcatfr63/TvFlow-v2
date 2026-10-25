package com.tvflow.app.data.local

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class EpgTransactionDeviceTest {
    @Test fun failedInsertionRollsBackDeletionAndOtherProfilesRemainIntact() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, TVFlowDatabase::class.java).build()
        try {
            val dao = db.dao()
            val old = EpgProgramEntity("audit", 1, 1, 2, "old", null)
            dao.upsertEpg(listOf(old, old.copy(profileId = "other")))
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER audit_abort BEFORE INSERT ON epg_programs WHEN NEW.title = 'reject' BEGIN SELECT RAISE(ABORT, 'audit rollback'); END")
            val failure = runCatching { dao.replaceEpg("audit", 1, listOf(old.copy(title = "reject"))) }
            assertTrue(failure.isFailure)
            assertEquals("old", dao.observeEpg("audit", 1).first().single().title)
            dao.replaceEpg("audit", 1, listOf(old.copy(title = "new")))
            assertEquals("new", dao.observeEpg("audit", 1).first().single().title)
            assertEquals("old", dao.observeEpg("other", 1).first().single().title)
        } finally { db.close() }
    }
}
