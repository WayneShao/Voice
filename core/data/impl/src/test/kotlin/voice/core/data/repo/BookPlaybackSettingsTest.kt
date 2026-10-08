package voice.core.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.PlaybackSettings
import voice.core.data.repo.internals.AppDb
import voice.core.data.repo.internals.MemoryDataStore
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class BookPlaybackSettingsTest {

  @Test
  fun `every read resolves defaults while unrelated writes preserve raw settings`() = runTest {
    val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDb::class.java).build()
    try {
      val defaults = MemoryDataStore(PlaybackSettings(playbackSpeed = 1.5F, skipSilence = true, gain = 6F))
      val contentRepo = BookContentRepoImpl(db.bookContentDao())
      val chapterRepo = ChapterRepoImpl(db.chapterDao())
      val repo = BookRepositoryImpl(chapterRepo, contentRepo, defaults)
      val chapterId = ChapterId("chapter")
      chapterRepo.put(Chapter(chapterId, "Chapter", 10000, Instant.EPOCH, 0, emptyList()))
      val id = BookId("book")
      contentRepo.put(
        BookContent(
          id = id, playbackSpeed = 1F, skipSilence = false, isActive = true, lastPlayedAt = Instant.EPOCH,
          author = null, name = "Book", addedAt = Instant.EPOCH, chapters = listOf(chapterId), currentChapter = chapterId,
          positionInChapter = 42, cover = null, gain = 0F, genre = null, narrator = null, series = null, part = null,
        ),
      )
      val reads = listOf(repo.get(id)!!, repo.all().single(), repo.flow(id).first()!!, repo.flow().first().single())
      reads.forEach { book ->
        assertEquals(1.5F, book.content.playbackSpeed)
        assertTrue(book.content.skipSilence)
        assertEquals(6F, book.content.gain)
      }
      repo.updateBook(id) { it.copy(positionInChapter = 123) }
      assertEquals(1F, contentRepo.get(id)!!.playbackSpeed)
      assertEquals(false, contentRepo.get(id)!!.skipSilence)
      assertEquals(0F, contentRepo.get(id)!!.gain)
      assertTrue(contentRepo.get(id)!!.useGlobalPlaybackSpeed)
      assertTrue(contentRepo.get(id)!!.useGlobalSkipSilence)
      assertTrue(contentRepo.get(id)!!.useGlobalGain)
      assertEquals(123L, repo.get(id)!!.content.positionInChapter)

      val singleValues = mutableListOf<Float>()
      val listValues = mutableListOf<Float>()
      backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
        repo.flow(id).collect { singleValues += it!!.content.playbackSpeed }
      }
      backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
        repo.flow().collect { listValues += it.single().content.playbackSpeed }
      }
      defaults.updateData { it.copy(playbackSpeed = 2F) }
      testScheduler.runCurrent()
      assertEquals(2F, singleValues.last())
      assertEquals(2F, listValues.last())

      repo.updateBook(id) { it.copy(playbackSpeed = 1F, useGlobalPlaybackSpeed = false) }
      defaults.updateData { it.copy(playbackSpeed = 3F) }
      assertEquals(1F, repo.get(id)!!.content.playbackSpeed)
      repo.updateBook(id) { it.copy(useGlobalPlaybackSpeed = true) }
      assertEquals(3F, repo.get(id)!!.content.playbackSpeed)
    } finally {
      db.close()
    }
  }
}
