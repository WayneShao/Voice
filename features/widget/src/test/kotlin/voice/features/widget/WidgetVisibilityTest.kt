package voice.features.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import voice.core.data.Book
import voice.core.data.BookContent
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.playback.playstate.PlayStateManager
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class WidgetVisibilityTest {

  @Test
  fun `updater never hides full widget views for host dimensions or single chapter`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = AppWidgetManager.getInstance(context)
    val shadow = shadowOf(manager)
    val widgetId = shadow.createWidget(AppWidgetProvider::class.java, R.layout.widget)
    val pendingIntent = PendingIntent.getActivity(context, 0, Intent("widget-test"), PendingIntent.FLAG_IMMUTABLE)
    val updater = WidgetUpdater(
      context = context,
      repo = mockk(),
      currentBookStore = mockk(),
      playStateManager = mockk { every { playState } returns PlayStateManager.PlayState.Paused },
      mainActivityIntentProvider = mockk { every { toCurrentBook() } returns pendingIntent },
    )
    val update = WidgetUpdater::class.java.getDeclaredMethod(
      "initWidgetForPresentBook",
      Int::class.javaPrimitiveType,
      Book::class.java,
      Continuation::class.java,
    ).apply { isAccessible = true }
    for (chapterCount in listOf(1, 3)) {
      val content = mockk<BookContent> {
        every { name } returns "Audiobook title"
        every { cover } returns null
        every { chapters } returns List(chapterCount) { ChapterId("chapter-$it") }
      }
      val chapter = mockk<Chapter> { every { name } returns "Current chapter" }
      val book = mockk<Book> {
        every { this@mockk.content } returns content
        every { currentChapter } returns chapter
      }
      for ((width, height) in listOf(0 to 0, 48 to 48, 250 to 48, 250 to 76, 300 to 80, 600 to 300)) {
        manager.updateAppWidgetOptions(
          widgetId,
          Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
          },
        )
        // No cover file: this suspend entry point completes synchronously, without image loading.
        update.invoke(
          updater,
          widgetId,
          book,
          object : Continuation<Unit> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) {
              result.getOrThrow()
            }
          },
        )
        val view = shadow.getViewFor(widgetId)
        for (id in listOf(R.id.title, R.id.summary, R.id.imageView, R.id.rewind, R.id.playPause, R.id.fastForward)) {
          assertEquals(View.VISIBLE, view.findViewById<View>(id).visibility)
        }
        assertEquals("Audiobook title", view.findViewById<TextView>(R.id.title).text.toString())
        assertEquals("Current chapter", view.findViewById<TextView>(R.id.summary).text.toString())
      }
    }
  }

  @Test
  fun `original full widget keeps all information and controls visible at every host size`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    for ((width, height) in listOf(48 to 48, 80 to 200, 250 to 48, 250 to 76, 300 to 80, 600 to 300)) {
      val remote = RemoteViews(context.packageName, R.layout.widget)
      remote.setTextViewText(R.id.title, "Audiobook title")
      remote.setTextViewText(R.id.summary, "Current chapter")
      val view = remote.apply(context, FrameLayout(context))
      view.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
      )
      view.layout(0, 0, width, height)
      for (id in listOf(R.id.title, R.id.summary, R.id.imageView, R.id.rewind, R.id.playPause, R.id.fastForward)) {
        assertEquals(View.VISIBLE, view.findViewById<View>(id).visibility)
      }
      assertEquals("Audiobook title", view.findViewById<TextView>(R.id.title).text.toString())
      assertEquals("Current chapter", view.findViewById<TextView>(R.id.summary).text.toString())
    }
  }

  @Test
  fun `single chapter text is retained in the original full widget`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val remote = RemoteViews(context.packageName, R.layout.widget)
    remote.setTextViewText(R.id.title, "One chapter audiobook")
    remote.setTextViewText(R.id.summary, "Only chapter")
    val view = remote.apply(context, FrameLayout(context))
    assertEquals(View.VISIBLE, view.findViewById<View>(R.id.summary).visibility)
    assertEquals("Only chapter", view.findViewById<TextView>(R.id.summary).text.toString())
  }
}
