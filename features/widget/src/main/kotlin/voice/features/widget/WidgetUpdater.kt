package voice.features.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import androidx.datastore.core.DataStore
import coil.imageLoader
import coil.request.ImageRequest
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import voice.app.features.widget.BaseWidgetProvider
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.repo.BookRepository
import voice.core.data.store.CurrentBookStore
import voice.core.playback.notification.MainActivityIntentProvider
import voice.core.playback.playstate.PlayStateManager
import voice.core.playback.receiver.WidgetButtonReceiver
import voice.core.ui.dpToPxRounded
import voice.core.ui.R as UiR

@SingleIn(AppScope::class)
@Inject
class WidgetUpdater(
  private val context: Context,
  private val repo: BookRepository,
  @CurrentBookStore
  private val currentBookStore: DataStore<BookId?>,
  private val playStateManager: PlayStateManager,
  private val mainActivityIntentProvider: MainActivityIntentProvider,
) {

  private val appWidgetManager = AppWidgetManager.getInstance(context)

  private val scope = CoroutineScope(Dispatchers.IO)

  fun update() {
    scope.launch {
      val book = currentBookStore.data.first()?.let {
        repo.get(it)
      }
      val componentName = ComponentName(this@WidgetUpdater.context, BaseWidgetProvider::class.java)
      val ids = appWidgetManager.getAppWidgetIds(componentName)

      for (widgetId in ids) {
        updateWidgetForId(book, widgetId)
      }
    }
  }

  private suspend fun updateWidgetForId(
    book: Book?,
    widgetId: Int,
  ) {
    if (book != null) {
      initWidgetForPresentBook(widgetId, book)
    } else {
      initWidgetForAbsentBook(widgetId)
    }
  }

  private suspend fun initWidgetForPresentBook(
    widgetId: Int,
    book: Book,
  ) {
    val opts = appWidgetManager.getAppWidgetOptions(widgetId)
    val useHeight = widgetHeight(opts)

    val remoteViews = RemoteViews(context.packageName, R.layout.widget)
    initElements(remoteViews = remoteViews, book = book, coverSize = useHeight)

    appWidgetManager.updateAppWidget(widgetId, remoteViews)
  }

  private fun widgetHeight(opts: Bundle): Int {
    val key = if (isPortrait) {
      AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
    } else {
      AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
    }
    val dp = opts.getInt(key)
    return context.dpToPxRounded(dp.toFloat())
  }

  private fun initWidgetForAbsentBook(widgetId: Int) {
    val remoteViews = RemoteViews(context.packageName, R.layout.widget)
    val wholeWidgetClickPI = mainActivityIntentProvider.toCurrentBook()
    remoteViews.setImageViewResource(R.id.imageView, UiR.drawable.album_art)
    remoteViews.setOnClickPendingIntent(R.id.wholeWidget, wholeWidgetClickPI)
    appWidgetManager.updateAppWidget(widgetId, remoteViews)
  }

  private val isPortrait: Boolean
    get() {
      val orientation = context.resources.configuration.orientation
      return orientation == Configuration.ORIENTATION_PORTRAIT
    }

  private suspend fun initElements(
    remoteViews: RemoteViews,
    book: Book,
    coverSize: Int,
  ) {
    val playPausePI = WidgetButtonReceiver.pendingIntent(context, WidgetButtonReceiver.Action.PlayPause)
    remoteViews.setOnClickPendingIntent(R.id.playPause, playPausePI)

    val fastForwardPI = WidgetButtonReceiver.pendingIntent(context, WidgetButtonReceiver.Action.FastForward)
    remoteViews.setOnClickPendingIntent(R.id.fastForward, fastForwardPI)

    val rewindPI = WidgetButtonReceiver.pendingIntent(context, WidgetButtonReceiver.Action.Rewind)
    remoteViews.setOnClickPendingIntent(R.id.rewind, rewindPI)

    val playIcon = if (playStateManager.playState == PlayStateManager.PlayState.Playing) {
      UiR.drawable.ic_pause_white_36dp
    } else {
      UiR.drawable.ic_play_white_36dp
    }
    remoteViews.setImageViewResource(R.id.playPause, playIcon)

    // if we have any book, init the views and have a click on the whole widget start BookPlay.
    // if we have no book, simply have a click on the whole widget start BookChoose.
    remoteViews.setTextViewText(R.id.title, book.content.name)
    val name = book.currentChapter.name

    remoteViews.setTextViewText(R.id.summary, name)

    val wholeWidgetClickPI = mainActivityIntentProvider.toCurrentBook()

    val coverFile = book.content.cover
    if (coverFile != null && coverSize > 0) {
      val bitmap = context.imageLoader
        .execute(
          ImageRequest.Builder(context)
            .data(coverFile)
            .size(coverSize, coverSize)
            .fallback(UiR.drawable.album_art)
            .error(UiR.drawable.album_art)
            .allowHardware(false)
            .build(),
        )
        .drawable!!.toBitmap()
      remoteViews.setImageViewBitmap(R.id.imageView, bitmap)
    } else {
      remoteViews.setImageViewResource(R.id.imageView, UiR.drawable.album_art)
    }

    remoteViews.setOnClickPendingIntent(R.id.wholeWidget, wholeWidgetClickPI)
  }
}
