/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-FileCopyrightText: 2026 Rhythm Audio Enhancement Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.audio.viper

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository for managing and persisting ViPER4Android DSP settings in Rhythm.
 * Exposes a reactive StateFlow for seamless UI and real-time audio pipeline updates.
 */
class ViperSettingsRepository private constructor(context: Context) {

    companion object {
        private const val PREFS_NAME = "rhythm_viper_audio_settings"

        @Volatile
        private var instance: ViperSettingsRepository? = null

        fun getInstance(context: Context): ViperSettingsRepository {
            return instance ?: synchronized(this) {
                instance ?: ViperSettingsRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _configFlow = MutableStateFlow(loadConfigFromPrefs())
    val configFlow: StateFlow<ViperMasterConfig> = _configFlow.asStateFlow()

    fun updateConfig(updater: (ViperMasterConfig) -> ViperMasterConfig) {
        val newConfig = updater(_configFlow.value)
        _configFlow.value = newConfig
        saveConfigToPrefs(newConfig)
    }

    fun setMasterEnabled(enabled: Boolean) {
        updateConfig { it.copy(masterEnabled = enabled) }
    }

    private fun loadConfigFromPrefs(): ViperMasterConfig {
        return ViperMasterConfig(
            masterEnabled = prefs.getBoolean("master_enabled", false),
            bindToSystemEffect = prefs.getBoolean("bind_system_effect", false),
            masterGate = MasterGateConfig(
                limiterEnabled = prefs.getBoolean("mg_limiter_enabled", true),
                limiterThresholdDb = prefs.getFloat("mg_limiter_thresh", -0.5f),
                outputVolumeDb = prefs.getFloat("mg_out_vol", 0.0f),
                channelPan = prefs.getFloat("mg_pan", 0.0f)
            ),
            playbackGain = PlaybackGainConfig(
                enabled = prefs.getBoolean("pgc_enabled", false),
                strength = prefs.getInt("pgc_strength", 2),
                maxGainDb = prefs.getFloat("pgc_max_gain", 6.0f),
                outputThresholdDb = prefs.getFloat("pgc_thresh", -1.0f)
            ),
            lufs = LufsConfig(
                enabled = prefs.getBoolean("lufs_enabled", false),
                targetLufs = prefs.getFloat("lufs_target", -14.0f),
                maxGainDb = prefs.getFloat("lufs_max_gain", 6.0f),
                speed = prefs.getInt("lufs_speed", 1)
            ),
            fetCompressor = FetCompressorConfig(
                enabled = prefs.getBoolean("fet_enabled", false),
                thresholdDb = prefs.getFloat("fet_thresh", -18.0f),
                ratio = prefs.getFloat("fet_ratio", 2.5f),
                attackMs = prefs.getFloat("fet_attack", 15.0f),
                releaseMs = prefs.getFloat("fet_release", 60.0f),
                makeupGainDb = prefs.getFloat("fet_makeup", 0.0f),
                noClip = prefs.getBoolean("fet_noclip", true)
            ),
            viperBass = ViperBassConfig(
                enabled = prefs.getBoolean("vb_enabled", false),
                mode = prefs.getInt("vb_mode", 0),
                frequencyHz = prefs.getInt("vb_freq", 60),
                gainDb = prefs.getFloat("vb_gain", 4.0f),
                antiPop = prefs.getBoolean("vb_antipop", true)
            ),
            bassMono = BassMonoConfig(
                enabled = prefs.getBoolean("bm_enabled", false),
                frequencyHz = prefs.getInt("bm_freq", 80)
            ),
            psychoacousticBass = PsychoacousticBassConfig(
                enabled = prefs.getBoolean("pab_enabled", false),
                cutoffHz = prefs.getInt("pab_cutoff", 80),
                intensity = prefs.getFloat("pab_intensity", 0.5f),
                harmonicOrder = prefs.getInt("pab_order", 3),
                originalLevel = prefs.getFloat("pab_orig", 0.8f)
            ),
            spectrumExtension = SpectrumExtensionConfig(
                enabled = prefs.getBoolean("spe_enabled", false),
                strength = prefs.getFloat("spe_strength", 0.5f),
                exciter = prefs.getFloat("spe_exciter", 0.3f)
            ),
            viperClarity = ViperClarityConfig(
                enabled = prefs.getBoolean("vc_enabled", false),
                mode = prefs.getInt("vc_mode", 1),
                gainDb = prefs.getFloat("vc_gain", 3.5f)
            ),
            tubeSimulator = TubeSimulatorConfig(
                enabled = prefs.getBoolean("tube_enabled", false),
                model = prefs.getInt("tube_model", 0),
                drive = prefs.getFloat("tube_drive", 0.4f),
                mix = prefs.getFloat("tube_mix", 0.3f)
            ),
            analogX = AnalogXConfig(
                enabled = prefs.getBoolean("ax_enabled", false),
                mode = prefs.getInt("ax_mode", 1)
            ),
            dynamicSystem = DynamicSystemConfig(
                enabled = prefs.getBoolean("ds_enabled", false),
                device = prefs.getInt("ds_device", 1),
                strength = prefs.getFloat("ds_strength", 0.4f)
            ),
            convolver = ConvolverConfig(
                enabled = prefs.getBoolean("conv_enabled", false),
                kernelName = prefs.getString("conv_kernel", "Studio Master") ?: "Studio Master",
                crossChannel = prefs.getFloat("conv_cross", 0.0f)
            ),
            fieldSurround = FieldSurroundConfig(
                enabled = prefs.getBoolean("fs_enabled", false),
                widening = prefs.getFloat("fs_widen", 0.4f),
                midImage = prefs.getFloat("fs_mid", 1.0f),
                depthMs = prefs.getFloat("fs_depth", 15.0f)
            ),
            diffSurround = DiffSurroundConfig(
                enabled = prefs.getBoolean("diff_enabled", false),
                delayMs = prefs.getFloat("diff_delay", 6.0f),
                reversePhase = prefs.getBoolean("diff_reverse", false),
                wetDryMix = prefs.getFloat("diff_mix", 0.5f),
                lpCutoffHz = prefs.getInt("diff_lp", 6000)
            ),
            stereoImager = StereoImagerConfig(
                enabled = prefs.getBoolean("si_enabled", false),
                lowWidth = prefs.getFloat("si_low_w", 0.7f),
                midWidth = prefs.getFloat("si_mid_w", 1.2f),
                highWidth = prefs.getFloat("si_high_w", 1.4f),
                lowCrossoverHz = prefs.getInt("si_low_cross", 250),
                highCrossoverHz = prefs.getInt("si_high_cross", 4000)
            ),
            headphoneSurround = HeadphoneSurroundConfig(
                enabled = prefs.getBoolean("hs_enabled", false),
                quality = prefs.getInt("hs_qual", 1)
            ),
            reverberation = ReverberationConfig(
                enabled = prefs.getBoolean("rev_enabled", false),
                roomSize = prefs.getFloat("rev_room", 0.35f),
                width = prefs.getFloat("rev_width", 0.5f),
                damp = prefs.getFloat("rev_damp", 0.4f),
                wet = prefs.getFloat("rev_wet", 0.15f),
                dry = prefs.getFloat("rev_dry", 0.9f)
            ),
            cureCrossfeed = CureCrossfeedConfig(
                enabled = prefs.getBoolean("cure_enabled", false),
                preset = prefs.getInt("cure_preset", 1)
            ),
            dynamicEq = DynamicEqConfig(
                enabled = prefs.getBoolean("deq_enabled", false),
                band1Freq = prefs.getInt("deq_b1_f", 80),
                band1GainDb = prefs.getFloat("deq_b1_g", 0.0f),
                band2Freq = prefs.getInt("deq_b2_f", 1000),
                band2GainDb = prefs.getFloat("deq_b2_g", 0.0f),
                band3Freq = prefs.getInt("deq_b3_f", 8000),
                band3GainDb = prefs.getFloat("deq_b3_g", 0.0f)
            ),
            speakerOpt = SpeakerOptConfig(
                enabled = prefs.getBoolean("so_enabled", false)
            )
        )
    }

    private fun saveConfigToPrefs(c: ViperMasterConfig) {
        prefs.edit {
            putBoolean("master_enabled", c.masterEnabled)
            putBoolean("bind_system_effect", c.bindToSystemEffect)

            // Master Gate
            putBoolean("mg_limiter_enabled", c.masterGate.limiterEnabled)
            putFloat("mg_limiter_thresh", c.masterGate.limiterThresholdDb)
            putFloat("mg_out_vol", c.masterGate.outputVolumeDb)
            putFloat("mg_pan", c.masterGate.channelPan)

            // Playback Gain
            putBoolean("pgc_enabled", c.playbackGain.enabled)
            putInt("pgc_strength", c.playbackGain.strength)
            putFloat("pgc_max_gain", c.playbackGain.maxGainDb)
            putFloat("pgc_thresh", c.playbackGain.outputThresholdDb)

            // LUFS
            putBoolean("lufs_enabled", c.lufs.enabled)
            putFloat("lufs_target", c.lufs.targetLufs)
            putFloat("lufs_max_gain", c.lufs.maxGainDb)
            putInt("lufs_speed", c.lufs.speed)

            // FET Compressor
            putBoolean("fet_enabled", c.fetCompressor.enabled)
            putFloat("fet_thresh", c.fetCompressor.thresholdDb)
            putFloat("fet_ratio", c.fetCompressor.ratio)
            putFloat("fet_attack", c.fetCompressor.attackMs)
            putFloat("fet_release", c.fetCompressor.releaseMs)
            putFloat("fet_makeup", c.fetCompressor.makeupGainDb)
            putBoolean("fet_noclip", c.fetCompressor.noClip)

            // ViPER Bass
            putBoolean("vb_enabled", c.viperBass.enabled)
            putInt("vb_mode", c.viperBass.mode)
            putInt("vb_freq", c.viperBass.frequencyHz)
            putFloat("vb_gain", c.viperBass.gainDb)
            putBoolean("vb_antipop", c.viperBass.antiPop)

            // Bass Mono
            putBoolean("bm_enabled", c.bassMono.enabled)
            putInt("bm_freq", c.bassMono.frequencyHz)

            // Psychoacoustic Bass
            putBoolean("pab_enabled", c.psychoacousticBass.enabled)
            putInt("pab_cutoff", c.psychoacousticBass.cutoffHz)
            putFloat("pab_intensity", c.psychoacousticBass.intensity)
            putInt("pab_order", c.psychoacousticBass.harmonicOrder)
            putFloat("pab_orig", c.psychoacousticBass.originalLevel)

            // Spectrum Extension
            putBoolean("spe_enabled", c.spectrumExtension.enabled)
            putFloat("spe_strength", c.spectrumExtension.strength)
            putFloat("spe_exciter", c.spectrumExtension.exciter)

            // ViPER Clarity
            putBoolean("vc_enabled", c.viperClarity.enabled)
            putInt("vc_mode", c.viperClarity.mode)
            putFloat("vc_gain", c.viperClarity.gainDb)

            // Tube Simulator
            putBoolean("tube_enabled", c.tubeSimulator.enabled)
            putInt("tube_model", c.tubeSimulator.model)
            putFloat("tube_drive", c.tubeSimulator.drive)
            putFloat("tube_mix", c.tubeSimulator.mix)

            // AnalogX
            putBoolean("ax_enabled", c.analogX.enabled)
            putInt("ax_mode", c.analogX.mode)

            // Dynamic System
            putBoolean("ds_enabled", c.dynamicSystem.enabled)
            putInt("ds_device", c.dynamicSystem.device)
            putFloat("ds_strength", c.dynamicSystem.strength)

            // Convolver
            putBoolean("conv_enabled", c.convolver.enabled)
            putString("conv_kernel", c.convolver.kernelName)
            putFloat("conv_cross", c.convolver.crossChannel)

            // Field Surround
            putBoolean("fs_enabled", c.fieldSurround.enabled)
            putFloat("fs_widen", c.fieldSurround.widening)
            putFloat("fs_mid", c.fieldSurround.midImage)
            putFloat("fs_depth", c.fieldSurround.depthMs)

            // Differential Surround
            putBoolean("diff_enabled", c.diffSurround.enabled)
            putFloat("diff_delay", c.diffSurround.delayMs)
            putBoolean("diff_reverse", c.diffSurround.reversePhase)
            putFloat("diff_mix", c.diffSurround.wetDryMix)
            putInt("diff_lp", c.diffSurround.lpCutoffHz)

            // Stereo Imager
            putBoolean("si_enabled", c.stereoImager.enabled)
            putFloat("si_low_w", c.stereoImager.lowWidth)
            putFloat("si_mid_w", c.stereoImager.midWidth)
            putFloat("si_high_w", c.stereoImager.highWidth)
            putInt("si_low_cross", c.stereoImager.lowCrossoverHz)
            putInt("si_high_cross", c.stereoImager.highCrossoverHz)

            // Headphone Surround
            putBoolean("hs_enabled", c.headphoneSurround.enabled)
            putInt("hs_qual", c.headphoneSurround.quality)

            // Reverberation
            putBoolean("rev_enabled", c.reverberation.enabled)
            putFloat("rev_room", c.reverberation.roomSize)
            putFloat("rev_width", c.reverberation.width)
            putFloat("rev_damp", c.reverberation.damp)
            putFloat("rev_wet", c.reverberation.wet)
            putFloat("rev_dry", c.reverberation.dry)

            // Cure Crossfeed
            putBoolean("cure_enabled", c.cureCrossfeed.enabled)
            putInt("cure_preset", c.cureCrossfeed.preset)

            // Dynamic EQ
            putBoolean("deq_enabled", c.dynamicEq.enabled)
            putInt("deq_b1_f", c.dynamicEq.band1Freq)
            putFloat("deq_b1_g", c.dynamicEq.band1GainDb)
            putInt("deq_b2_f", c.dynamicEq.band2Freq)
            putFloat("deq_b2_g", c.dynamicEq.band2GainDb)
            putInt("deq_b3_f", c.dynamicEq.band3Freq)
            putFloat("deq_b3_g", c.dynamicEq.band3GainDb)

            // Speaker Opt
            putBoolean("so_enabled", c.speakerOpt.enabled)
        }
    }
}
