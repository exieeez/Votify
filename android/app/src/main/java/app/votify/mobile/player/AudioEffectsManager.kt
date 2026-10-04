package app.votify.mobile.player

import android.media.audiofx.BassBoost
import android.media.audiofx.PresetReverb
import android.util.Log
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages native audio effects (PresetReverb for atmospheric "Slowed + Reverb" soundstage,
 * and BassBoost for deep club/subwoofer bass) synchronized with ExoPlayer's audio session.
 */
@UnstableApi
object AudioEffectsManager {
    private const val TAG = "AudioEffectsManager"

    private var player: ExoPlayer? = null
    private var presetReverb: PresetReverb? = null
    private var bassBoost: BassBoost? = null
    private var currentAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    private val _reverbPreset = MutableStateFlow("none")
    val reverbPreset: StateFlow<String> = _reverbPreset.asStateFlow()

    private val _bassBoostEnabled = MutableStateFlow(false)
    val bassBoostEnabled: StateFlow<Boolean> = _bassBoostEnabled.asStateFlow()

    fun attachPlayer(exoPlayer: ExoPlayer) {
        player = exoPlayer
        currentAudioSessionId = exoPlayer.audioSessionId
        applyEffects()
    }

    fun onAudioSessionIdChanged(audioSessionId: Int) {
        currentAudioSessionId = audioSessionId
        releaseBass()
        applyBassBoost()
    }

    fun detachPlayer() {
        runCatching { player?.clearAuxEffectInfo() }
        player = null
        releaseReverb()
        releaseBass()
    }

    fun setReverb(preset: String) {
        _reverbPreset.value = preset
        applyReverb()
    }

    fun setBassBoost(enabled: Boolean) {
        _bassBoostEnabled.value = enabled
        applyBassBoost()
    }

    private fun applyEffects() {
        applyReverb()
        applyBassBoost()
    }

    private fun applyReverb() {
        val p = player ?: return
        val preset = _reverbPreset.value
        if (preset == "none") {
            runCatching {
                p.clearAuxEffectInfo()
                presetReverb?.enabled = false
            }
            return
        }

        runCatching {
            if (presetReverb == null) {
                // Priority 0, Audio Session 0 for auxiliary effects
                presetReverb = PresetReverb(0, 0)
            }
            presetReverb?.let { reverb ->
                val presetCode = when (preset) {
                    "hall" -> PresetReverb.PRESET_LARGEHALL
                    "room" -> PresetReverb.PRESET_LARGEROOM
                    "plate" -> PresetReverb.PRESET_PLATE
                    else -> PresetReverb.PRESET_MEDIUMHALL
                }
                reverb.preset = presetCode
                reverb.enabled = true
                p.setAuxEffectInfo(AuxEffectInfo(reverb.id, 1.0f))
            }
        }.onFailure { e ->
            Log.w(TAG, "Failed to apply PresetReverb ($preset): ${e.message}")
        }
    }

    private fun applyBassBoost() {
        val sessionId = currentAudioSessionId
        val enabled = _bassBoostEnabled.value
        if (!enabled || sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId <= 0) {
            runCatching {
                bassBoost?.enabled = false
            }
            return
        }

        runCatching {
            if (bassBoost == null) {
                bassBoost = BassBoost(0, sessionId)
            }
            bassBoost?.let { bb ->
                if (bb.strengthSupported) {
                    bb.setStrength(1000.toShort())
                }
                bb.enabled = true
            }
        }.onFailure { e ->
            Log.w(TAG, "Failed to apply BassBoost: ${e.message}")
        }
    }

    private fun releaseReverb() {
        runCatching {
            presetReverb?.enabled = false
            presetReverb?.release()
        }
        presetReverb = null
    }

    private fun releaseBass() {
        runCatching {
            bassBoost?.enabled = false
            bassBoost?.release()
        }
        bassBoost = null
    }
}
