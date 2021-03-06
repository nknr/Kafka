package com.kafka.player.playback.notification

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.support.v4.media.session.MediaSessionCompat
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import coil.Coil
import coil.request.LoadRequest
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import com.google.android.exoplayer2.util.NotificationUtil.IMPORTANCE_LOW
import com.google.android.exoplayer2.util.NotificationUtil.createNotificationChannel
import com.kafka.data.CustomScope
import com.kafka.data.entities.Song
import com.kafka.player.R
import com.kafka.player.timber.constants.Constants
import com.kafka.player.timber.playback.MusicService
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationManager @Inject constructor(
    private val context: Application
) : CoroutineScope by CustomScope(),
    PlayerNotificationManager.CustomActionReceiver,
    PlayerNotificationManager.MediaDescriptionAdapter {

    private val channelId = "1001"
    private val notificationId = 1001

    private val playerNotificationManager: PlayerNotificationManager by lazy {
        createNotificationChannel(
            context,
            channelId,
            R.string.player_notification_title,
            R.string.player_description_notification_title,
            IMPORTANCE_LOW
        )

        PlayerNotificationManager(
            context,
            channelId,
            notificationId,
            this
        ).apply {
            setColorized(true)
            setColor(Color.parseColor("#AF945C"))
            setUseChronometer(false)
            setFastForwardIncrementMs(10_000L)
            setRewindIncrementMs(10_000L)
            setUseNavigationActionsInCompactView(true)
        }
    }

    init {
        val mediaSession = MediaSessionCompat(context, "ExoPlayer")
        mediaSession.isActive = true
        playerNotificationManager.setMediaSessionToken(mediaSession.getSessionToken())
    }

    fun attachPlayer(player: Player) {
        playerNotificationManager.setPlayer(player)
    }

    fun detachPlayer() {
        playerNotificationManager.setPlayer(null)
    }

    override fun getCustomActions(player: Player): MutableList<String> {
        return mutableListOf()
    }

    override fun createCustomActions(context: Context, instanceId: Int): MutableMap<String, NotificationCompat.Action> {
        return mutableMapOf(
            "play" to getPlayPauseAction(context, R.drawable.ic_pause),
            "next" to getNextAction(context),
            "previous" to getPreviousAction(context)
        )
    }

    override fun onCustomAction(player: Player, action: String, intent: Intent) {
        TODO("not implemented")
    }

    override fun createCurrentContentIntent(player: Player): PendingIntent? {
        return null
    }

    override fun getCurrentContentText(player: Player): CharSequence? {
        return (player.currentTag as? Song)?.subtitle
    }

    override fun getCurrentContentTitle(player: Player): CharSequence {
        return (player.currentTag as? Song)?.title ?: ""
    }

    override fun getCurrentLargeIcon(player: Player, callback: PlayerNotificationManager.BitmapCallback): Bitmap? {
        Coil.imageLoader(context)
            .execute(
                LoadRequest.Builder(context)
                    .data((player.currentTag as? Song)?.coverImage)
                    .target {
                        callback.onBitmap((it as BitmapDrawable).bitmap)
                    }.build()
            )
        return null
    }

    private fun getPreviousAction(context: Context): NotificationCompat.Action {
        val actionIntent = Intent(context, MusicService::class.java).apply {
            action = Constants.ACTION_PREVIOUS
        }
        val pendingIntent = PendingIntent.getService(context, 0, actionIntent, 0)
        return NotificationCompat.Action(R.drawable.ic_step_backward, "", pendingIntent)
    }

    private fun getPlayPauseAction(context: Context, @DrawableRes playButtonResId: Int): NotificationCompat.Action {
        val actionIntent = Intent(context, MusicService::class.java).apply {
            action = Constants.ACTION_PLAY_PAUSE
        }
        val pendingIntent = PendingIntent.getService(context, 0, actionIntent, 0)
        return NotificationCompat.Action(playButtonResId, "", pendingIntent)
    }

    private fun getNextAction(context: Context): NotificationCompat.Action {
        val actionIntent = Intent(context, MusicService::class.java).apply {
            action = Constants.ACTION_NEXT
        }
        val pendingIntent = PendingIntent.getService(context, 0, actionIntent, 0)
        return NotificationCompat.Action(R.drawable.ic_skip_forward, "", pendingIntent)
    }
}
