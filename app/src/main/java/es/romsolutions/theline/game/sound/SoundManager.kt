package es.romsolutions.theline.game.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import es.romsolutions.theline.R

class SoundManager(private val context: Context) {
    private val soundPool: SoundPool
    private val laserSoundId: Int
    private var mediaPlayer: MediaPlayer? = null

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        
        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(audioAttributes)
            .build()
        
        laserSoundId = soundPool.load(context, R.raw.laser_shot, 1)
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

    fun playBackgroundMusic(level: Int) {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        
        val musicRes = when {
            level <= 5 -> R.raw.sector1_bg
            level <= 10 -> R.raw.sector2_bg
            level <= 15 -> R.raw.sector3_bg
            else -> R.raw.sector4_bg
        }

        mediaPlayer = MediaPlayer.create(context, musicRes).apply {
            isLooping = true
            val vol = if (isMuted) 0f else musicVolume
            setVolume(vol, vol)
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
