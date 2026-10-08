package voice.core.data.repo.internals.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.binding

@ContributesIntoSet(scope = AppScope::class, binding = binding<Migration>())
public class Migration60 : IncrementalMigration(60) {

  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("ALTER TABLE content2 ADD COLUMN useGlobalPlaybackSpeed INTEGER NOT NULL DEFAULT 1")
    db.execSQL("ALTER TABLE content2 ADD COLUMN useGlobalSkipSilence INTEGER NOT NULL DEFAULT 1")
    db.execSQL("ALTER TABLE content2 ADD COLUMN useGlobalGain INTEGER NOT NULL DEFAULT 1")
    db.execSQL("UPDATE content2 SET useGlobalPlaybackSpeed = 0 WHERE playbackSpeed != 1")
    db.execSQL("UPDATE content2 SET useGlobalSkipSilence = 0 WHERE skipSilence != 0")
    db.execSQL("UPDATE content2 SET useGlobalGain = 0 WHERE gain != 0")
  }
}
