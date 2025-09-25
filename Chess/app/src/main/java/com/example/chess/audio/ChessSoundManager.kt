package com.example.chess.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import androidx.compose.runtime.*
import com.example.chess.R

/**
 * Quản lý âm thanh cho game cờ vua sử dụng SoundPool
 * - Move.ogg: Di chuyển quân cờ thường
 * - Capture.ogg: Ăn quân
 * - Check.ogg: Chiếu vua
 * - Tournament1st.ogg: Kết thúc game (chiến thắng/hòa)
 */
class ChessSoundManager(private val context: Context) {
    private var soundPool: SoundPool? = null
    private var soundIds = mutableMapOf<ChessSound, Int>()
    private var isEnabled = true
    
    enum class ChessSound(val resourceId: Int) {
        MOVE(R.raw.move),
        CAPTURE(R.raw.capture),
        CHECK(R.raw.check),
        GAME_END(R.raw.tournament)
    }
    
    init {
        initSoundPool()
    }
    
    private fun initSoundPool() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
            
        soundPool = SoundPool.Builder()
            .setMaxStreams(4) // Tối đa 4 âm thanh đồng thời
            .setAudioAttributes(audioAttributes)
            .build()
            
        // Load tất cả sound effects
        loadSounds()
    }
    
    private fun loadSounds() {
        soundPool?.let { pool ->
            ChessSound.values().forEach { sound ->
                val soundId = pool.load(context, sound.resourceId, 1)
                soundIds[sound] = soundId
            }
        }
    }
    
    /**
     * Phát âm thanh theo loại
     */
    fun playSound(sound: ChessSound) {
        if (!isEnabled) return
        
        soundPool?.let { pool ->
            soundIds[sound]?.let { soundId ->
                // Volume: 1.0 = max, Priority: 1 = normal, Loop: 0 = no loop, Rate: 1.0 = normal speed
                pool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)
            }
        }
    }
    
    /**
     * Bật/tắt âm thanh
     */
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }
    
    /**
     * Kiểm tra trạng thái âm thanh
     */
    fun isEnabled(): Boolean = isEnabled
    
    /**
     * Giải phóng tài nguyên
     */
    fun release() {
        soundPool?.release()
        soundPool = null
        soundIds.clear()
    }
}

/**
 * Composable helper để tạo và quản lý ChessSoundManager
 */
@Composable
fun rememberChessSoundManager(context: Context): ChessSoundManager {
    val soundManager = remember { ChessSoundManager(context) }
    
    DisposableEffect(soundManager) {
        onDispose {
            soundManager.release()
        }
    }
    
    return soundManager
}

/**
 * Phát âm thanh dựa trên loại nước đi và trạng thái game
 */
fun ChessSoundManager.playMoveSound(
    isCapture: Boolean = false,
    isCheck: Boolean = false,
    isGameEnd: Boolean = false
) {
    when {
        isGameEnd -> playSound(ChessSoundManager.ChessSound.GAME_END)
        isCheck -> playSound(ChessSoundManager.ChessSound.CHECK)
        isCapture -> playSound(ChessSoundManager.ChessSound.CAPTURE)
        else -> playSound(ChessSoundManager.ChessSound.MOVE)
    }
}
