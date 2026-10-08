package voice.core.data.repo

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import voice.core.data.Book
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.PlaybackSettings
import voice.core.data.store.PlaybackSettingsStore
import voice.core.data.withPlaybackSettings
import voice.core.logging.api.Logger

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
public class BookRepositoryImpl(
  private val chapterRepo: ChapterRepoImpl,
  private val contentRepo: BookContentRepo,
  @PlaybackSettingsStore
  private val playbackSettingsStore: DataStore<PlaybackSettings>,
) : BookRepository {

  private var warmedUp = false
  private val mutex = Mutex()

  private suspend fun warmUp() {
    if (warmedUp) return
    mutex.withLock {
      if (warmedUp) return@withLock
      val chapters = contentRepo.all()
        .filter { it.isActive }
        .flatMap { it.chapters }
      chapterRepo.warmup(chapters)
      warmedUp = true
    }
  }

  override fun flow(): Flow<List<Book>> {
    return combine(contentRepo.flow(), playbackSettingsStore.data) { contents, defaults ->
      contents.filter { it.isActive }
        .mapNotNull { content ->
          content.withPlaybackSettings(defaults).book()
        }
    }.distinctUntilChanged()
  }

  override suspend fun all(): List<Book> {
    val defaults = playbackSettingsStore.data.first()
    return contentRepo.all()
      .filter { it.isActive }
      .mapNotNull { it.withPlaybackSettings(defaults).book() }
  }

  override fun flow(id: BookId): Flow<Book?> {
    return combine(contentRepo.flow(id), playbackSettingsStore.data) { content, defaults ->
      content?.withPlaybackSettings(defaults)?.book()
    }.distinctUntilChanged()
  }

  override suspend fun get(id: BookId): Book? {
    val defaults = playbackSettingsStore.data.first()
    return contentRepo.get(id)?.withPlaybackSettings(defaults)?.book()
  }

  override suspend fun updateBook(
    id: BookId,
    update: (BookContent) -> BookContent,
  ) {
    mutex.withLock {
      val content = contentRepo.get(id) ?: return
      val updated = update(content)
      if (updated != content) {
        contentRepo.put(updated)
      }
    }
  }

  private suspend fun BookContent.book(): Book? {
    warmUp()
    return Book(
      content = this,
      chapters = chapters.map { chapterId ->
        val chapter = chapterRepo.get(chapterId)
        if (chapter == null) {
          Logger.w("Missing chapter with id=$chapterId for $this")
          return null
        }
        chapter
      },
    )
  }
}
