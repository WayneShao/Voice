package voice.core.data.repo.internals.internals.migrations

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.runner.RunWith
import voice.core.data.repo.internals.AppDb
import voice.core.data.repo.internals.allMigrations
import voice.core.data.repo.internals.getInt
import voice.core.data.repo.internals.mapRows
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class Migration60to61Test {

  @Rule
  @JvmField
  val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDb::class.java)

  @Test
  fun `old non defaults become independent overrides without losing progress`() {
    val name = InstrumentationRegistry.getInstrumentation().targetContext.getDatabasePath("playbackSettingsMigration").absolutePath
    helper.createDatabase(name, 60).use { db ->
      listOf(Triple(1F, false, 0F), Triple(1.5F, false, 0F), Triple(1F, true, 3F)).forEachIndexed { index, settings ->
        db.insert(
          "content2",
          SQLiteDatabase.CONFLICT_FAIL,
          ContentValues().apply {
            put("id", "book$index")
            put("name", "Book $index")
            put("playbackSpeed", settings.first)
            put("skipSilence", if (settings.second) 1 else 0)
            put("gain", settings.third)
            put("isActive", 1)
            put("lastPlayedAt", "2026-10-07T08:00:00Z")
            put("addedAt", "2026-10-07T08:00:00Z")
            put("chapters", "[\"chapter\"]")
            put("currentChapter", "chapter")
            put("positionInChapter", 1234)
          },
        )
      }
    }
    helper.runMigrationsAndValidate(name, 61, true, *allMigrations()).use { db ->
      val flags = db.query("SELECT * FROM content2 ORDER BY id").mapRows {
        listOf(getInt("useGlobalPlaybackSpeed"), getInt("useGlobalSkipSilence"), getInt("useGlobalGain"), getInt("positionInChapter"))
      }
      assertEquals(listOf(listOf(1, 1, 1, 1234), listOf(0, 1, 1, 1234), listOf(1, 0, 0, 1234)), flags)
    }
  }
}
