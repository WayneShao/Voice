package voice.core.playback.player

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.test.utils.FakeMediaSource
import androidx.media3.test.utils.FakeTimeline
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import voice.core.data.Book
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.ChapterMark
import voice.core.data.ListeningEvent
import voice.core.data.MarkData
import voice.core.data.repo.BookRepository
import voice.core.logging.api.LogWriter
import voice.core.logging.api.Logger
import voice.core.playback.MemoryDataStore
import voice.core.playback.history.CommandSourceResolver
import voice.core.playback.history.ListeningHistoryRecorder
import voice.core.playback.history.PlaybackPosition
import voice.core.playback.history.RecordingRepo
import voice.core.playback.misc.Decibel
import voice.core.playback.misc.VolumeGain
import voice.core.playback.session.MediaItemProvider
import voice.core.playback.session.realChapterId
import voice.core.playback.session.search.book
import voice.core.playback.session.toMediaIdOrNull
import voice.core.sleeptimer.SleepTimer
import voice.core.sleeptimer.SleepTimerMode
import voice.core.sleeptimer.SleepTimerState
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class VoicePlayerTest {

  init {
    Logger.install(
      object : LogWriter {
        override fun log(
          severity: Logger.Severity,
          message: String,
          throwable: Throwable?,
        ) {
          println("$severity: $message")
          throwable?.printStackTrace()
        }
      },
    )
  }

  private val seekTimeStore = MemoryDataStore(2)
  private val autoRewindAmountStore = MemoryDataStore(2)

  private val internalPlayer = TestExoPlayerBuilder(ApplicationProvider.getApplicationContext())
    .setMediaSourceFactory(
      mockk {
        every { createMediaSource(any()) } answers {
          val mediaItem = arg<MediaItem>(0)
          val mediaId = mediaItem.mediaId
          val chapter = currentBook.chapters.single {
            it.id == mediaId.toMediaIdOrNull()!!.realChapterId
          }
          FakeMediaSource(
            FakeTimeline(
              FakeTimeline.TimelineWindowDefinition.Builder()
                .setPeriodCount(1)
                .setSeekable(true)
                .setDurationUs(TimeUnit.MILLISECONDS.toMicros(chapter.duration))
                .setMediaItem(mediaItem)
                .build(),
            ),
          )
        }
      },
    )
    .build()

  private val scope = TestScope()
  private val mediaItemProvider = MediaItemProvider(mockk(), mockk(), mockk(), mockk(), mockk(), mockk())
  private val bookId = BookId(Uuid.random().toString())
  private lateinit var currentBook: Book
  private val sleepTimer = FakeSleepTimer()
  private val historyRepo = RecordingRepo()
  private val chapterMarkPlayer = ChapterMarkPlayer(internalPlayer, mediaItemProvider)
  private val bookUpdates = MutableStateFlow<Book?>(null)
  private val volumeGain = mockk<VolumeGain>(relaxed = true)
  private val repository = mockk<BookRepository> {
    coEvery { get(any<BookId>()) } answers { currentBook }
    every { flow(any()) } answers { flowOf(currentBook) }
    coEvery { updateBook(any(), any()) } just Runs
  }
  private val player = VoicePlayer(
    player = chapterMarkPlayer,
    repo = repository,
    currentBookStoreId = mockk {
      every { data } returns flowOf(bookId)
    },
    seekTimeStore = seekTimeStore,
    autoRewindAmountStore = autoRewindAmountStore,
    scope = scope,
    volumeGain = volumeGain,
    sleepTimer = sleepTimer,
    analytics = mockk(relaxed = true),
    historyRecorder = ListeningHistoryRecorder(
      repo = historyRepo,
      clock = Clock.fixed(Instant.parse("2026-10-07T10:00:00Z"), ZoneOffset.UTC),
      scope = scope,
    ),
    commandSourceResolver = CommandSourceResolver(ApplicationProvider.getApplicationContext()),
  )

  @Test
  fun `loading and reactive settings updates never write book overrides`() = scope.runTest {
    every { repository.flow(any()) } returns bookUpdates
    setMediaItems(listOf(chapter(ChapterMark(name = null, startMs = 0, endMs = 20000))))
    player.prepare()
    awaitReady()
    player.seekTo(3000)
    runCurrent()
    coVerify(exactly = 0) { repository.updateBook(any(), any()) }
    bookUpdates.value = currentBook.update { it.copy(playbackSpeed = 1.5F, skipSilence = true, gain = 3F) }
    runCurrent()
    assertEquals(1.5F, player.playbackParameters.speed)
    assertEquals(true, internalPlayer.skipSilenceEnabled)
    verify { volumeGain.gain = Decibel(3F) }
    // Returning to global values updates the player without a write or position reset.
    bookUpdates.value = currentBook.update { it.copy(playbackSpeed = 1F, skipSilence = false, gain = 0F) }
    runCurrent()
    assertEquals(1F, player.playbackParameters.speed)
    assertEquals(false, internalPlayer.skipSilenceEnabled)
    verify { volumeGain.gain = Decibel(0F) }
    assertEquals(3000L, player.currentPosition)
    coVerify(exactly = 0) { repository.updateBook(any(), any()) }
    player.release()
    bookUpdates.value = currentBook.update { it.copy(gain = 9F) }
    runCurrent()
    verify(exactly = 0) { volumeGain.gain = Decibel(9F) }
  }

  @Test
  fun `switching books cancels observation of the old book`() = scope.runTest {
    val nextUpdates = MutableStateFlow<Book?>(null)
    every { repository.flow(any()) } answers { if (firstArg<BookId>() == bookId) bookUpdates else nextUpdates }
    setMediaItems(listOf(chapter(ChapterMark(name = null, startMs = 0, endMs = 20000))))
    val oldBook = currentBook
    currentBook = oldBook.update { it.copy(id = BookId("next"), playbackSpeed = 1.25F) }
    player.setMediaItem(mediaItemProvider.mediaItem(currentBook))
    runCurrent()
    bookUpdates.value = oldBook.update { it.copy(playbackSpeed = 3F, gain = 9F) }
    runCurrent()
    assertEquals(1.25F, player.playbackParameters.speed)
    verify(exactly = 0) { volumeGain.gain = Decibel(9F) }
    nextUpdates.value = currentBook.update { it.copy(playbackSpeed = 1.5F) }
    runCurrent()
    assertEquals(1.5F, player.playbackParameters.speed)
    player.release()
  }

  @Test
  fun `explicit edit captures loaded book and marks only the edited setting`() = scope.runTest {
    setMediaItems(listOf(chapter(ChapterMark(name = null, startMs = 0, endMs = 20000))))
    val original = currentBook
    val edits = mutableListOf<Pair<BookId, BookContent>>()
    coEvery { repository.updateBook(any(), any()) } answers {
      edits += firstArg<BookId>() to secondArg<(BookContent) -> BookContent>()(original.content)
    }
    player.setPlaybackSpeed(1F)
    currentBook = currentBook.update { it.copy(id = BookId("other")) }
    player.setMediaItem(mediaItemProvider.mediaItem(currentBook))
    runCurrent()
    val speedEdit = edits.single()
    assertEquals(original.id, speedEdit.first)
    assertFalse(speedEdit.second.useGlobalPlaybackSpeed)
    assertEquals(true, speedEdit.second.useGlobalSkipSilence)
    assertEquals(true, speedEdit.second.useGlobalGain)
    player.release()
  }

  @Test
  fun `seekToNext carries over into the next chapter mark`() = scope.runTest {
    setMediaItems(
      listOf(
        chapter(
          ChapterMark(startMs = 0, endMs = 19_999, name = null),
          ChapterMark(startMs = 20_000, endMs = 30_000, name = null),
        ),
        chapter(
          ChapterMark(startMs = 0, endMs = 19_999, name = null),
          ChapterMark(startMs = 20_000, endMs = 30_000, name = null),
        ),
      ),
    )

    seekTimeStore.updateData { 7 }

    player.prepare()
    awaitReady()
    player.shouldHavePosition(0, 0)

    player.seekToNext()
    player.shouldHavePosition(0, 7_000)

    player.seekToNext()
    player.shouldHavePosition(0, 14_000)

    // the first mark ends at 19_999, so the remainder carries over into the second one
    player.seekToNext()
    player.shouldHavePosition(1, 1_001)

    player.seekToNext()
    player.shouldHavePosition(1, 8_001)

    player.seekToNext()
    player.shouldHavePosition(2, 5_002)

    player.seekToNext()
    player.shouldHavePosition(2, 12_002)
  }

  @Test
  fun `seekToPrevious carries over into the previous chapter mark`() = scope.runTest {
    setMediaItems(
      listOf(
        chapter(
          ChapterMark(startMs = 0, endMs = 4_999, name = null),
          ChapterMark(startMs = 5_000, endMs = 12_000, name = null),
        ),
        chapter(
          ChapterMark(startMs = 0, endMs = 4_999, name = null),
          ChapterMark(startMs = 5_000, endMs = 12_001, name = null),
        ),
      ),
    )

    seekTimeStore.updateData { 5 }

    player.seekTo(1, 12_000)
    player.prepare()
    awaitReady()

    // the mark only spans 7s, so the seek is clamped to its end
    player.shouldHavePosition(1, 6_999)

    player.seekToPrevious()
    player.shouldHavePosition(1, 1_999)

    player.seekToPrevious()
    player.shouldHavePosition(0, 1_998)

    player.seekToPrevious()
    player.shouldHavePosition(0, 0)

    player.seekToPrevious()
    player.shouldHavePosition(0, 0)
  }

  @Test
  fun `forceSeekToNext jumps to chapters`() = scope.runTest {
    setMediaItems(
      listOf(
        chapter(
          ChapterMark(startMs = 0, endMs = 11_999, name = null),
          ChapterMark(startMs = 12_000, endMs = 20_000, name = null),
        ),
        chapter(
          ChapterMark(startMs = 0, endMs = 11_999, name = null),
          ChapterMark(startMs = 12_000, endMs = 20_000, name = null),
        ),
      ),
    )

    player.prepare()
    awaitReady()
    player.shouldHavePosition(0, 0)

    player.forceSeekToNext()
    player.shouldHavePosition(1, 0)

    player.forceSeekToNext()
    player.shouldHavePosition(2, 0)

    player.forceSeekToNext()
    player.shouldHavePosition(3, 0)

    player.forceSeekToNext()
    player.shouldHavePosition(3, 0)
  }

  @Test
  fun `forceSeekToPrevious jumps to chapters`() = scope.runTest {
    setMediaItems(
      listOf(
        chapter(
          ChapterMark(startMs = 0, endMs = 11_999, name = null),
          ChapterMark(startMs = 12_000, endMs = 20_000, name = null),
        ),
        chapter(
          ChapterMark(startMs = 0, endMs = 11_999, name = null),
          ChapterMark(startMs = 12_000, endMs = 20_000, name = null),
        ),
      ),
    )

    player.seekTo(3, 6_000)
    player.prepare()
    awaitReady()
    player.shouldHavePosition(3, 6_000)

    player.forceSeekToPrevious()
    player.shouldHavePosition(3, 0)

    player.forceSeekToPrevious()
    player.shouldHavePosition(2, 0)

    player.forceSeekToPrevious()
    player.shouldHavePosition(1, 0)

    player.forceSeekToPrevious()
    player.shouldHavePosition(0, 0)
  }

  @Test
  fun `forceSeekToPrevious jumps to previous chapter when in the 2s window`() = scope.runTest {
    setMediaItems(
      listOf(
        chapter(
          ChapterMark(startMs = 0, endMs = 11_999, name = null),
          ChapterMark(startMs = 12_000, endMs = 20_000, name = null),
        ),
        chapter(
          ChapterMark(startMs = 0, endMs = 11_999, name = null),
          ChapterMark(startMs = 12_000, endMs = 20_000, name = null),
        ),
      ),
    )

    player.seekTo(2, 1_000)
    player.prepare()
    awaitReady()
    player.shouldHavePosition(2, 1_000)

    player.forceSeekToPrevious()
    player.shouldHavePosition(1, 0)

    player.seekTo(1, 1_000)
    player.forceSeekToPrevious()
    player.shouldHavePosition(0, 0)
  }

  @Test
  fun `setBook resumes inside matching chapter mark`() = scope.runTest {
    val chapter = chapter(
      ChapterMark(startMs = 0, endMs = 11_999, name = null),
      ChapterMark(startMs = 12_000, endMs = 20_000, name = null),
    )
    setMediaItems(
      chapters = listOf(chapter),
      currentChapter = chapter,
      positionInChapter = 15_000,
    )

    player.prepare()
    awaitReady()

    player.shouldHavePosition(1, 3_000)
  }

  @Test
  fun `end of chapter sleep timer pauses at start of next chapter mark`() = scope.runTest {
    setMediaItems(
      listOf(
        chapter(
          ChapterMark(startMs = 0, endMs = 1_000, name = null),
          ChapterMark(startMs = 1_000, endMs = 2_000, name = null),
        ),
      ),
    )

    player.prepare()
    awaitReady()
    sleepTimer.enable(SleepTimerMode.EndOfChapter)

    TestPlayerRunHelper.play(internalPlayer).untilPlayWhenReadyIs(false)

    player.shouldHavePosition(1, 0)
    assertFalse(player.playWhenReady)
    assertEquals(expected = SleepTimerState.Disabled, actual = sleepTimer.state.value)
  }

  @Test
  fun `end of chapter sleep timer pauses at start of normal file chapter`() = scope.runTest {
    setMediaItems(
      listOf(
        chapter(ChapterMark(startMs = 0, endMs = 1_000, name = null)),
        chapter(ChapterMark(startMs = 0, endMs = 1_000, name = null)),
      ),
    )

    player.prepare()
    awaitReady()
    sleepTimer.enable(SleepTimerMode.EndOfChapter)

    TestPlayerRunHelper.play(internalPlayer).untilPlayWhenReadyIs(false)

    player.shouldHavePosition(1, 0)
    assertFalse(player.playWhenReady)
    assertEquals(expected = SleepTimerState.Disabled, actual = sleepTimer.state.value)
  }

  @Test
  fun `end of chapter sleep timer records once where it paused`() = scope.runTest {
    val chapter = chapter(
      ChapterMark(startMs = 0, endMs = 1_000, name = null),
      ChapterMark(startMs = 1_000, endMs = 2_000, name = null),
    )
    setMediaItems(listOf(chapter))

    player.prepare()
    awaitReady()
    sleepTimer.enable(SleepTimerMode.EndOfChapter)

    TestPlayerRunHelper.play(internalPlayer).untilPlayWhenReadyIs(false)
    shadowOf(Looper.getMainLooper()).idle()
    advanceUntilIdle()

    assertEquals(
      expected = listOf(ListeningEvent.Type.SleepTimerEnded to ListeningEvent.Source.SleepTimer),
      actual = historyRepo.events.map { it.type to it.source },
    )
    assertEquals(
      expected = PlaybackPosition(bookId, chapter.id, 1_000),
      actual = historyRepo.events.single().let { PlaybackPosition(it.bookId, it.chapterId, it.time) },
    )
  }

  @Test
  fun `end of chapter sleep timer survives skipping to the next chapter`() = scope.runTest {
    setMediaItems(
      listOf(
        chapter(
          ChapterMark(startMs = 0, endMs = 4_999, name = null),
          ChapterMark(startMs = 5_000, endMs = 9_999, name = null),
          ChapterMark(startMs = 10_000, endMs = 20_000, name = null),
        ),
      ),
    )
    player.prepare()
    awaitReady()
    sleepTimer.enable(SleepTimerMode.EndOfChapter)

    player.forceSeekToNext()
    player.shouldHavePosition(1, 0)
    awaitReady()
    shadowOf(Looper.getMainLooper()).idle()
    scope.advanceUntilIdle()

    assertEquals(expected = SleepTimerState.Enabled.WithEndOfChapter, actual = sleepTimer.state.value)
  }

  @Test
  fun `auto rewind clamps to current chapter start`() = scope.runTest {
    setMediaItems(
      listOf(
        chapter(
          ChapterMark(startMs = 0, endMs = 11_999, name = null),
          ChapterMark(startMs = 12_000, endMs = 20_000, name = null),
        ),
      ),
    )

    autoRewindAmountStore.updateData { 5 }

    player.seekTo(1, 3_000)
    player.prepare()
    awaitReady()
    player.shouldHavePosition(1, 3_000)

    player.pause()

    player.shouldHavePosition(1, 0)
  }

  private fun TestScope.setMediaItems(
    chapters: List<Chapter>,
    currentChapter: Chapter = chapters.first(),
    positionInChapter: Long = 0,
  ) {
    currentBook = book(chapters, bookId).update {
      it.copy(
        currentChapter = currentChapter.id,
        positionInChapter = positionInChapter,
      )
    }
    player.setMediaItem(mediaItemProvider.mediaItem(currentBook))
    runCurrent()
  }

  @Test
  fun `forceSeekToPrevious jumps to chapter start when outside the 2s window`() = scope.runTest {
    setMediaItems(
      listOf(
        chapter(
          ChapterMark(startMs = 0, endMs = 11_999, name = null),
          ChapterMark(startMs = 12_000, endMs = 20_000, name = null),
        ),
        chapter(
          ChapterMark(startMs = 0, endMs = 11_999, name = null),
          ChapterMark(startMs = 12_000, endMs = 20_000, name = null),
        ),
      ),
    )

    player.seekTo(2, 3_000)
    player.prepare()
    awaitReady()
    player.shouldHavePosition(2, 3_000)

    player.forceSeekToPrevious()
    player.shouldHavePosition(2, 0)

    player.seekTo(2, 1_000)
    player.forceSeekToPrevious()
    player.shouldHavePosition(1, 0)
  }

  private fun chapter(vararg marks: ChapterMark): Chapter {
    return Chapter(
      id = ChapterId(Uuid.random().toString()),
      name = "chapter",
      duration = marks.maxOf { it.endMs },
      fileLastModified = Instant.EPOCH,
      markData = marks.map {
        MarkData(it.startMs, it.name ?: "mark ")
      },
      fileSize = 0,
    )
  }

  private fun awaitReady() {
    TestPlayerRunHelper.runUntilPlaybackState(internalPlayer, Player.STATE_READY)
  }

  @IgnorableReturnValue
  private fun Player.shouldHavePosition(
    currentMediaItemIndex: Int,
    currentPosition: Long,
  ): Player {
    scope.advanceUntilIdle()
    assertEquals(expected = currentMediaItemIndex, actual = this.currentMediaItemIndex)
    assertEquals(expected = currentPosition, actual = this.currentPosition)
    return this
  }

  private class FakeSleepTimer : SleepTimer {
    override val state: StateFlow<SleepTimerState>
      get() = stateFlow

    private val stateFlow = MutableStateFlow<SleepTimerState>(SleepTimerState.Disabled)

    override fun enable(mode: SleepTimerMode) {
      stateFlow.value = when (mode) {
        is SleepTimerMode.TimedWithDuration -> SleepTimerState.Enabled.WithDuration(mode.duration)
        SleepTimerMode.TimedWithDefault -> error("TimedWithDefault is not used in these tests")
        SleepTimerMode.EndOfChapter -> SleepTimerState.Enabled.WithEndOfChapter
      }
    }

    override fun disable() {
      stateFlow.value = SleepTimerState.Disabled
    }
  }
}
