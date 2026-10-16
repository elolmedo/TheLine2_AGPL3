package es.romsolutions.theline.game.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import es.romsolutions.theline.R

class SoundManager(private val context: Context) {
    private val soundPool: SoundPool
    private val laserSoundId: Int
    private val explosion1SoundId: Int
    private val explosion2SoundId: Int
    private val itemSoundId: Int
    
    private var mediaPlayer: MediaPlayer? = null
    private val playlist = listOf(R.raw.audio1, R.raw.audio2)
    private var currentTrackIndex = 0

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        
        soundPool = SoundPool.Builder()
            .setMaxStreams(8)
            .setAudioAttributes(audioAttributes)
            .build()
        
        laserSoundId = soundPool.load(context, R.raw.laser_shot_theline, 1)
        explosion1SoundId = soundPool.load(context, R.raw.explosion1, 1)
        explosion2SoundId = soundPool.load(context, R.raw.explosion2, 1)
        itemSoundId = soundPool.load(context, R.raw.item, 1)
    }

    private var musicVolume = 0.4f
    private var sfxVolume = 1.0f
    private var isMuted = false

    fun setMusicVolume(volume: Float) {
        musicVolume = volume
        if (!isMuted) mediaPlayer?.setVolume(volume, volume)
    }

    fun setSfxVolume(volume: Float) {
        sfxVolume = volume
    }

    fun setMute(mute: Boolean) {
        isMuted = mute
        val vol = if (mute) 0f else musicVolume
        mediaPlayer?.setVolume(vol, vol)
    }

    fun playLaser() {
        if (!isMuted) soundPool.play(laserSoundId, sfxVolume, sfxVolume, 0, 0, 1.0f)
    }

    fun playExplosion() {
        if (!isMuted) {
            val expId = if (kotlin.random.Random.nextBoolean()) explosion1SoundId else explosion2SoundId
            soundPool.play(expId, sfxVolume, sfxVolume, 0, 0, 1.0f)
        }
    }

    fun playItem() {
        if (!isMuted) soundPool.play(itemSoundId, sfxVolume, sfxVolume, 0, 0, 1.0f)
    }

    fun playBackgroundMusic(level: Int = 1) {
        if (mediaPlayer?.isPlaying == true) {
            return
        }
        playTrack(currentTrackIndex)
    }

    private fun playTrack(index: Int) {
        mediaPlayer?.stop()
        mediaPlayer?.release()

        val trackRes = playlist[index % playlist.size]
        mediaPlayer = MediaPlayer.create(context, trackRes).apply {
            isLooping = false
            val vol = if (isMuted) 0f else musicVolume
            setVolume(vol, vol)
            setOnCompletionListener {
                currentTrackIndex = (index + 1) % playlist.size
                playTrack(currentTrackIndex)
            }
            start()
        }
    }

    fun stopBackgroundMusic() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    fun release() {
        soundPool.release()
        stopBackgroundMusic()
    }
}
