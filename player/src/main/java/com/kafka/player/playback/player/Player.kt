package com.kafka.player.playback.player

import com.google.android.exoplayer2.Player
import com.kafka.data.entities.Song

interface Player {
    val player: Player
    fun play()
    fun play(song: Song)
    fun pause()
    fun togglePlayPause()
    fun next()
    fun previous()
    fun seekTo(position: Long)
    suspend fun setQueue(queue: List<Song>)
    fun start()
    fun stop()
    fun release()
}

