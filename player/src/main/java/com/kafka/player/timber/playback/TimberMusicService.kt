/*
 * Copyright (c) 2019 Naman Dwivedi.
 *
 * Licensed under the GNU General Public License v3
 *
 * This is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 */
package com.kafka.player.timber.playback

import android.content.Intent
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_MEDIA_ID
import android.support.v4.media.session.PlaybackStateCompat.STATE_NONE
import androidx.annotation.Nullable
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import com.kafka.data.data.db.QueueEntity
import com.kafka.player.R
import com.kafka.player.timber.Utils.EMPTY_ALBUM_ART_URI
import com.kafka.player.timber.constants.Constants
import com.kafka.player.timber.constants.Constants.ACTION_NEXT
import com.kafka.player.timber.constants.Constants.ACTION_PREVIOUS
import com.kafka.player.timber.constants.Constants.APP_PACKAGE_NAME
import com.kafka.player.timber.db.QueueHelper
import com.kafka.player.timber.extensions.isPlayEnabled
import com.kafka.player.timber.extensions.isPlaying
import com.kafka.player.timber.extensions.toIDList
import com.kafka.player.timber.extensions.toRawMediaItems
import com.kafka.player.timber.models.MediaID
import com.kafka.player.timber.models.MediaID.Companion.CALLER_OTHER
import com.kafka.player.timber.models.MediaID.Companion.CALLER_SELF
import com.kafka.player.timber.notifications.Notifications
import com.kafka.player.timber.permissions.PermissionsManager
import com.kafka.player.timber.playback.players.Queue
import com.kafka.player.timber.playback.players.SongPlayer
import com.kafka.player.timber.repository.PlaylistRepository
import com.kafka.player.timber.repository.SongsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import timber.log.Timber.d as log

// TODO pull out media logic to separate class to make this more readable
@AndroidEntryPoint
class TimberMusicService : MediaBrowserServiceCompat(), LifecycleOwner {

    companion object {
        const val MEDIA_ID_ARG = "MEDIA_ID"
        const val MEDIA_TYPE_ARG = "MEDIA_TYPE"
        const val MEDIA_CALLER = "MEDIA_CALLER"
        const val MEDIA_ID_ROOT = -1
        const val TYPE_ALL_ARTISTS = 0
        const val TYPE_ALL_ALBUMS = 1
        const val TYPE_ALL_SONGS = 2
        const val TYPE_ALL_PLAYLISTS = 3
        const val TYPE_SONG = 9
        const val TYPE_ALBUM = 10
        const val TYPE_ARTIST = 11
        const val TYPE_PLAYLIST = 12
        const val TYPE_ALL_FOLDERS = 13
        const val TYPE_ALL_GENRES = 14
        const val TYPE_GENRE = 15

        const val NOTIFICATION_ID = 888
    }

    @Inject
    lateinit var notifications: Notifications

    @Inject
    lateinit var songsRepository: SongsRepository

    @Inject
    lateinit var playlistRepository: PlaylistRepository

    @Inject
    lateinit var queue: Queue

    @Inject
    lateinit var player: SongPlayer

    @Inject
    lateinit var queueHelper: QueueHelper

    @Inject
    lateinit var permissionsManager: PermissionsManager

    private lateinit var becomingNoisyReceiver: BecomingNoisyReceiver
    private val lifecycle = LifecycleRegistry(this)

    override fun getLifecycle() = lifecycle

    override fun onCreate() {
        super.onCreate()
        lifecycle.currentState = Lifecycle.State.RESUMED
        log("onCreate()")

        // We wait until the permission is granted to set the initial queue.
        // This observable will immediately emit if the permission is already granted.

        GlobalScope.launch(IO) {
            permissionsManager.requestStoragePermission(waitForGranted = true)
                .collect {
                    player.setQueue()
                }
        }

        sessionToken = player.getSession().sessionToken
        becomingNoisyReceiver = BecomingNoisyReceiver(this, sessionToken!!)

        player.onPlayingState { isPlaying ->
            if (isPlaying) {
                becomingNoisyReceiver.register()
                startForeground(NOTIFICATION_ID, notifications.buildNotification(getSession()))
            } else {
                becomingNoisyReceiver.unregister()
                stopForeground(false)
                saveCurrentData()
            }
            notifications.updateNotification(player.getSession())
        }

        player.onCompletion {
            notifications.updateNotification(player.getSession())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log("onStartCommand(): ${intent?.action}")
        if (intent == null) {
            return START_STICKY
        }

        val mediaSession = player.getSession()
        val controller = mediaSession.controller

        when (intent.action) {
            Constants.ACTION_PLAY_PAUSE -> {
                controller.playbackState?.let { playbackState ->
                    when {
                        playbackState.isPlaying -> controller.transportControls.pause()
                        playbackState.isPlayEnabled -> controller.transportControls.play()
                    }
                }
            }
            ACTION_NEXT -> {
                controller.transportControls.skipToNext()
            }
            ACTION_PREVIOUS -> {
                controller.transportControls.skipToPrevious()
            }
        }

        MediaButtonReceiver.handleIntent(mediaSession, intent)
        return START_STICKY
    }

    override fun onDestroy() {
        lifecycle.currentState = Lifecycle.State.DESTROYED
        log("onDestroy()")
        saveCurrentData()
        player.release()
        super.onDestroy()
    }

    //media browser
    override fun onLoadChildren(parentId: String, result: Result<List<MediaBrowserCompat.MediaItem>>) {
        result.detach()

        // Wait to load media item children until we have the storage permission, this prevents crashes
        // and allows us to automatically finish loading once the permission is granted by the user.

        GlobalScope.launch(Main) {
            permissionsManager.requestStoragePermission(waitForGranted = true)
            val mediaItems = withContext(IO) {
                loadChildren(parentId)
            }
            result.sendResult(mediaItems)
        }
    }

    @Nullable
    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): MediaBrowserServiceCompat.BrowserRoot? {
        val caller = if (clientPackageName == APP_PACKAGE_NAME) {
            CALLER_SELF
        } else {
            CALLER_OTHER
        }
        return MediaBrowserServiceCompat.BrowserRoot(MediaID(MEDIA_ID_ROOT.toString(), null, caller).asString(), null)
    }

    private fun addMediaRoots(mMediaRoot: MutableList<MediaBrowserCompat.MediaItem>, caller: String) {
        mMediaRoot.add(
            MediaBrowserCompat.MediaItem(
                MediaDescriptionCompat.Builder().apply {
                    setMediaId(MediaID(TYPE_ALL_ARTISTS.toString(), null, caller).asString())
                    setTitle(getString(R.string.artists))
                    setIconUri(EMPTY_ALBUM_ART_URI.toUri())
                    setSubtitle(getString(R.string.artists))
                }.build(), FLAG_BROWSABLE
            )
        )

        mMediaRoot.add(
            MediaBrowserCompat.MediaItem(
                MediaDescriptionCompat.Builder().apply {
                    setMediaId(MediaID(TYPE_ALL_ALBUMS.toString(), null, caller).asString())
                    setTitle(getString(R.string.albums))
                    setIconUri(EMPTY_ALBUM_ART_URI.toUri())
                    setSubtitle(getString(R.string.albums))
                }.build(), FLAG_BROWSABLE
            )
        )

        mMediaRoot.add(
            MediaBrowserCompat.MediaItem(
                MediaDescriptionCompat.Builder().apply {
                    setMediaId(MediaID(TYPE_ALL_SONGS.toString(), null, caller).asString())
                    setTitle(getString(R.string.songs))
                    setIconUri(EMPTY_ALBUM_ART_URI.toUri())
                    setSubtitle(getString(R.string.songs))
                }.build(), FLAG_BROWSABLE
            )
        )

        mMediaRoot.add(
            MediaBrowserCompat.MediaItem(
                MediaDescriptionCompat.Builder().apply {
                    setMediaId(MediaID(TYPE_ALL_PLAYLISTS.toString(), null, caller).asString())
                    setTitle(getString(R.string.playlists))
                    setIconUri(EMPTY_ALBUM_ART_URI.toUri())
                    setSubtitle(getString(R.string.playlists))
                }.build(), FLAG_BROWSABLE
            )
        )

        mMediaRoot.add(
            MediaBrowserCompat.MediaItem(
                MediaDescriptionCompat.Builder().apply {
                    setMediaId(MediaID(TYPE_ALL_GENRES.toString(), null, caller).asString())
                    setTitle(getString(R.string.genres))
                    setIconUri(EMPTY_ALBUM_ART_URI.toUri())
                    setSubtitle(getString(R.string.genres))
                }.build(), FLAG_BROWSABLE
            )
        )
    }

    private fun loadChildren(parentId: String): ArrayList<MediaBrowserCompat.MediaItem> {
        val mediaItems = ArrayList<MediaBrowserCompat.MediaItem>()
        val mediaIdParent = MediaID().fromString(parentId)

        val mediaType = mediaIdParent.type
        val mediaId = mediaIdParent.mediaId
        val caller = mediaIdParent.caller

        if (mediaType == MEDIA_ID_ROOT.toString()) {
            addMediaRoots(mediaItems, caller!!)
        } else {
            when (mediaType?.toInt() ?: 0) {

                TYPE_ALL_SONGS -> {
                    mediaItems.addAll(songsRepository.loadSongs(caller))
                }
                TYPE_ALL_PLAYLISTS -> {
                    mediaItems.addAll(playlistRepository.getPlaylists(caller))
                }
                TYPE_PLAYLIST -> {
                    mediaId?.let {
                        mediaItems.addAll(playlistRepository.getSongsInPlaylist(it.toLong(), caller))
                    }
                }
            }
        }

        return if (caller == CALLER_SELF) {
            mediaItems
        } else {
            mediaItems.toRawMediaItems()
        }
    }

    private fun saveCurrentData() {
        GlobalScope.launch(IO) {
            val mediaSession = player.getSession()
            val controller = mediaSession.controller
            if (controller == null ||
                controller.playbackState == null ||
                controller.playbackState.state == STATE_NONE
            ) {
                return@launch
            }

            val queue = controller.queue
            val currentId = controller.metadata?.getString(METADATA_KEY_MEDIA_ID)
            queueHelper.updateQueueSongs(queue?.toIDList(), currentId?.toLong())

            val queueEntity = QueueEntity().apply {
                this.currentId = currentId?.toLong()
                currentSeekPos = controller.playbackState?.position
                repeatMode = controller.repeatMode
                shuffleMode = controller.shuffleMode
                playState = controller.playbackState?.state
                queueTitle = controller.queueTitle?.toString() ?: getString(R.string.all_songs)
            }
            queueHelper.updateQueueData(queueEntity)
        }
    }
}