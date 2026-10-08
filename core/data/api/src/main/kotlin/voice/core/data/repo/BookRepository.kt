package voice.core.data.repo

import kotlinx.coroutines.flow.Flow
import voice.core.data.Book
import voice.core.data.BookContent
import voice.core.data.BookId

/** Reads expose effective playback values, resolving inherited settings against global defaults. */
public interface BookRepository {

  public fun flow(): Flow<List<Book>>

  public suspend fun all(): List<Book>

  public fun flow(id: BookId): Flow<Book?>

  public suspend fun get(id: BookId): Book?

  /** The transform receives raw stored values. Explicit playback edits must also clear the corresponding inheritance flag. */
  public suspend fun updateBook(
    id: BookId,
    update: (BookContent) -> BookContent,
  )
}
