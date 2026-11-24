package com.example.greetingcard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BackupTest {
    private val data = AppData(
        entries = listOf(
            FoodEntry(
                date = "2026-10-19",
                meal = Meal.LUNCH,
                name = "Five Guys Cheeseburger",
                perServing = Nutrients(protein = 47.0),
            ),
        ),
        saved = listOf(SavedFood(name = "Banana", perServing = Nutrients(fruit = 1.0))),
        settings = Settings(name = "Sam"),
        notes = mapOf("2026-10-19" to "Burger day"),
    )

    @Test
    fun aBackupRestoresEverything() {
        val text = Backup.encode(data, createdAt = "2026-10-19T12:30:00", appVersion = "0.1.10")
        val result = Backup.decode(text) as Backup.Result.Ok
        assertEquals(data, result.data)
        assertEquals("2026-10-19T12:30:00", result.createdAt)
    }

    @Test
    fun theAppsOwnDataFileCanBeRestoredToo() {
        val result = Backup.decode(AppDataJson.encode(data)) as Backup.Result.Ok
        assertEquals(data, result.data)
        assertEquals(null, result.createdAt)
    }

    /** Restoring replaces everything, so a file that isn't ours must never load as empty data. */
    @Test
    fun otherFilesAreRefused() {
        listOf("""{"name":"something else"}""", "not json at all", "", "[1, 2, 3]").forEach { text ->
            assertTrue(text, Backup.decode(text) is Backup.Result.Invalid)
        }
    }

    @Test
    fun backupsFromANewerFormatAskForAnUpdate() {
        val newer = """{"app":"FoodTracker","backupFormat":2,"data":{"version":1}}"""
        val result = Backup.decode(newer) as Backup.Result.Invalid
        assertTrue(result.reason, result.reason.contains("newer version"))
    }

    @Test
    fun fileNameHasTheDate() {
        assertEquals("food-tracker-backup-2026-10-19.json", Backup.fileName(LocalDate.of(2026, 10, 19)))
    }
}
