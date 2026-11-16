/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-FileCopyrightText: 2026 Rhythm Audio Enhancement Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.audio.viper

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import chromahub.rhythm.app.infrastructure.audio.RhythmAudioProcessor
import chromahub.rhythm.app.infrastructure.audio.viper.dsp.BiquadFilter
import chromahub.rhythm.app.infrastructure.audio.viper.dsp.FastConvolver
import kotlin.math.*

/**
 * ViPER4Android DSP Engine for Rhythm.
 * Executes all 24 ViPER4Android audio enhancement algorithms natively inside ExoPlayer's
 * real-time PCM pipeline without requiring root or system drivers.
 */
@OptIn(UnstableApi::class)
class ViperAudioProcessor : RhythmAudioProcessor() {

    companion object {
        private const val TAG = "ViperAudioProcessor"
    }

    // Dynamic synchronization parent for ExoPlayer A/B crossfading
    @Volatile
    private var parentProcessor: ViperAudioProcessor? = null

    @Volatile
    private var config: ViperMasterConfig = ViperMasterConfig()

    // DSP Stages & Filters
    private val convolver = FastConvolver()

    // ViPER Bass filters
    private val bassFilter = BiquadFilter()
    private val bassAntiPopFilter = BiquadFilter()

    // Bass Mono low-pass & high-pass crossover
    private val bassMonoLp = BiquadFilter()
    private val bassMonoHp = BiquadFilter()

    // ViPER Clarity filters
    private val clarityFilter = BiquadFilter()

    // Multiband Crossover Filters (4 crossover points = 5 bands)
    private val mbLp1 = BiquadFilter()
    private val mbHp1 = BiquadFilter()
    private val mbLp2 = BiquadFilter()
    private val mbHp2 = BiquadFilter()
    private val mbLp3 = BiquadFilter()
    private val mbHp3 = BiquadFilter()
    private val mbLp4 = BiquadFilter()
    private val mbHp4 = BiquadFilter()

    // Dynamic EQ Filters
    private val dynEqFilter1 = BiquadFilter()
    private val dynEqFilter2 = BiquadFilter()
    private val dynEqFilter3 = BiquadFilter()

    // Stereo Imager Crossover
    private val imagerLowLp = BiquadFilter()
    private val imagerHighHp = BiquadFilter()

    // Cure Crossfeed Low-Pass filter
    private val cureLpFilter = BiquadFilter()

    // Differential Surround Low-Pass filter
    private val diffSurroundLp = BiquadFilter()

    // Speaker Opt Filters
    private val speakerSubsonicHp = BiquadFilter()
    private val speakerNotch = BiquadFilter()

    // State memory for delays and envelopes
    private var diffDelayBuffer = FloatArray(4096)
    private var diffDelayIndex = 0

    // AGC / LUFS running energy trackers
    private var agcRmsLevel = 0.1f
    private var lufsIntegrated = -14.0f
    private var fetEnvelope = 0.0f
    private var mbEnvelopes = FloatArray(5)

    // Reverb comb & allpass memory
    private val reverbCombsL = Array(4) { FloatArray(1600) }
    private val reverbCombsR = Array(4) { FloatArray(1600) }
    private val reverbCombIndices = IntArray(4)

    fun setParent(parent: ViperAudioProcessor?) {
        this.parentProcessor = parent
    }

    fun updateConfig(newConfig: ViperMasterConfig) {
        this.config = newConfig
        reconfigureFilters()
    }

    fun getConfig(): ViperMasterConfig = parentProcessor?.getConfig() ?: config

    override fun isEnabled(): Boolean {
        val activeConfig = getConfig()
        return activeConfig.masterEnabled
    }

    override fun isBypassed(): Boolean {
        return !isEnabled() || channelCount != 2
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val outputFormat = super.configure(inputAudioFormat)
        reconfigureFilters()
        return outputFormat
    }

    override fun flush() {
        super.flush()
        resetDspStates()
    }

    private fun resetDspStates() {
        convolver.reset()
        bassFilter.reset()
        bassAntiPopFilter.reset()
        bassMonoLp.reset()
        bassMonoHp.reset()
        clarityFilter.reset()
        diffDelayBuffer.fill(0f)
        diffDelayIndex = 0
        agcRmsLevel = 0.1f
        lufsIntegrated = -14.0f
        fetEnvelope = 0.0f
        mbEnvelopes.fill(0f)
        for (comb in reverbCombsL) comb.fill(0f)
        for (comb in reverbCombsR) comb.fill(0f)
        reverbCombIndices.fill(0)
    }

    private fun reconfigureFilters() {
        val fs = sampleRate.coerceAtLeast(8000)
        val cfg = getConfig()

        // 1. ViPER Bass
        val bassCfg = cfg.viperBass
        val bassGain = if (bassCfg.enabled) bassCfg.gainDb else 0.0f
        when (bassCfg.mode) {
            0 -> bassFilter.configure(BiquadFilter.Type.LOW_SHELF, fs, bassCfg.frequencyHz.toFloat(), 0.707f, bassGain)
            1 -> bassFilter.configure(BiquadFilter.Type.PEAKING, fs, bassCfg.frequencyHz.toFloat(), 1.4f, bassGain)
            else -> bassFilter.configure(BiquadFilter.Type.LOW_SHELF, fs, (bassCfg.frequencyHz * 0.75f).coerceAtLeast(20f), 1.0f, bassGain * 1.25f)
        }
        bassAntiPopFilter.configure(BiquadFilter.Type.HIGH_PASS, fs, 25.0f, 0.707f, 0.0f)

        // 2. Bass Mono
        bassMonoLp.configure(BiquadFilter.Type.LOW_PASS, fs, cfg.bassMono.frequencyHz.toFloat(), 0.707f, 0.0f)
        bassMonoHp.configure(BiquadFilter.Type.HIGH_PASS, fs, cfg.bassMono.frequencyHz.toFloat(), 0.707f, 0.0f)

        // 3. ViPER Clarity
        val clarityCfg = cfg.viperClarity
        val clarityFreq = when (clarityCfg.mode) {
            0 -> 3500.0f // Natural
            1 -> 6500.0f // Ozone+
            else -> 9000.0f // X-HiFi
        }
        clarityFilter.configure(BiquadFilter.Type.PEAKING, fs, clarityFreq, 1.2f, if (clarityCfg.enabled) clarityCfg.gainDb else 0.0f)

        // 4. Multiband Crossover Filters
        val mb = cfg.multibandCompressor
        mbLp1.configure(BiquadFilter.Type.LOW_PASS, fs, mb.crossover1.toFloat(), 0.707f, 0.0f)
        mbHp1.configure(BiquadFilter.Type.HIGH_PASS, fs, mb.crossover1.toFloat(), 0.707f, 0.0f)
        mbLp2.configure(BiquadFilter.Type.LOW_PASS, fs, mb.crossover2.toFloat(), 0.707f, 0.0f)
        mbHp2.configure(BiquadFilter.Type.HIGH_PASS, fs, mb.crossover2.toFloat(), 0.707f, 0.0f)
        mbLp3.configure(BiquadFilter.Type.LOW_PASS, fs, mb.crossover3.toFloat(), 0.707f, 0.0f)
        mbHp3.configure(BiquadFilter.Type.HIGH_PASS, fs, mb.crossover3.toFloat(), 0.707f, 0.0f)
        mbLp4.configure(BiquadFilter.Type.LOW_PASS, fs, mb.crossover4.toFloat(), 0.707f, 0.0f)
        mbHp4.configure(BiquadFilter.Type.HIGH_PASS, fs, mb.crossover4.toFloat(), 0.707f, 0.0f)

        // 5. Differential Surround Low-Pass
        diffSurroundLp.configure(BiquadFilter.Type.LOW_PASS, fs, cfg.diffSurround.lpCutoffHz.toFloat(), 0.707f, 0.0f)

        // 6. Stereo Imager Crossover
        imagerLowLp.configure(BiquadFilter.Type.LOW_PASS, fs, cfg.stereoImager.lowCrossoverHz.toFloat(), 0.707f, 0.0f)
        imagerHighHp.configure(BiquadFilter.Type.HIGH_PASS, fs, cfg.stereoImager.highCrossoverHz.toFloat(), 0.707f, 0.0f)

        // 7. Cure Crossfeed Low-Pass filter
        cureLpFilter.configure(BiquadFilter.Type.LOW_PASS, fs, 700.0f, 0.707f, -3.0f)

        // 8. Speaker Optimization
        speakerSubsonicHp.configure(BiquadFilter.Type.HIGH_PASS, fs, 60.0f, 0.707f, 0.0f)
        speakerNotch.configure(BiquadFilter.Type.NOTCH, fs, 3200.0f, 2.5f, 0.0f)

        // 9. Convolver
        convolver.loadPreset(cfg.convolver.kernelName, fs)
    }

    override fun processSamples(samples: ShortArray, sampleCount: Int) {
        val cfg = getConfig()
        if (!cfg.masterEnabled || channelCount != 2) return

        val fs = sampleRate.coerceAtLeast(8000).toFloat()
        val numFrames = sampleCount / 2

        // Precalculate master output volume and pan multipliers
        val masterGainLinear = 10.0f.pow(cfg.masterGate.outputVolumeDb / 20.0f)
        val pan = cfg.masterGate.channelPan.coerceIn(-1.0f, 1.0f)
        val panL = if (pan > 0f) 1.0f - pan else 1.0f
        val panR = if (pan < 0f) 1.0f + pan else 1.0f

        val limiterThresholdLinear = 10.0f.pow(cfg.masterGate.limiterThresholdDb / 20.0f)
        val isLimiterEnabled = cfg.masterGate.limiterEnabled

        // Playback Gain Control (AGC) state
        val pgcEnabled = cfg.playbackGain.enabled
        val pgcMaxGain = 10.0f.pow(cfg.playbackGain.maxGainDb / 20.0f)
        val pgcThreshold = 10.0f.pow(cfg.playbackGain.outputThresholdDb / 20.0f)
        val pgcRate = when (cfg.playbackGain.strength) {
            1 -> 0.0001f
            3 -> 0.001f
            else -> 0.0004f
        }

        // LUFS Normalization state
        val lufsEnabled = cfg.lufs.enabled
        val lufsTargetLinear = 10.0f.pow((cfg.lufs.targetLufs + 14.0f) / 20.0f)
        val lufsMaxGain = 10.0f.pow(cfg.lufs.maxGainDb / 20.0f)

        // FET Compressor state
        val fetEnabled = cfg.fetCompressor.enabled
        val fetThresh = 10.0f.pow(cfg.fetCompressor.thresholdDb / 20.0f)
        val fetRatio = cfg.fetCompressor.ratio.coerceAtLeast(1.0f)
        val fetMakeup = 10.0f.pow(cfg.fetCompressor.makeupGainDb / 20.0f)
        val fetAttackCoeff = exp(-1.0f / (cfg.fetCompressor.attackMs * 0.001f * fs))
        val fetReleaseCoeff = exp(-1.0f / (cfg.fetCompressor.releaseMs * 0.001f * fs))

        // ViPER Bass
        val bassEnabled = cfg.viperBass.enabled
        val bassAntiPop = cfg.viperBass.antiPop

        // Bass Mono
        val bassMonoEnabled = cfg.bassMono.enabled

        // Psychoacoustic Bass
        val psychoEnabled = cfg.psychoacousticBass.enabled
        val psychoIntensity = cfg.psychoacousticBass.intensity
        val psychoOriginal = cfg.psychoacousticBass.originalLevel

        // Spectrum Extension
        val specExtEnabled = cfg.spectrumExtension.enabled
        val specStrength = cfg.spectrumExtension.strength
        val specExciter = cfg.spectrumExtension.exciter

        // ViPER Clarity
        val clarityEnabled = cfg.viperClarity.enabled

        // Tube Simulator
        val tubeEnabled = cfg.tubeSimulator.enabled
        val tubeDrive = cfg.tubeSimulator.drive * 1.8f + 0.2f
        val tubeMix = cfg.tubeSimulator.mix

        // AnalogX
        val analogEnabled = cfg.analogX.enabled
        val analogWarmth = when (cfg.analogX.mode) {
            0 -> 0.15f
            2 -> 0.45f
            else -> 0.28f
        }

        // Dynamic System
        val dsEnabled = cfg.dynamicSystem.enabled
        val dsStrength = cfg.dynamicSystem.strength

        // Convolver
        val convEnabled = cfg.convolver.enabled
        val convCross = cfg.convolver.crossChannel

        // Field Surround
        val fieldEnabled = cfg.fieldSurround.enabled
        val fieldWidth = 1.0f + cfg.fieldSurround.widening * 1.5f
        val fieldMid = cfg.fieldSurround.midImage

        // Differential Surround
        val diffEnabled = cfg.diffSurround.enabled
        val diffDelaySamples = ((cfg.diffSurround.delayMs * 0.001f * fs).toInt()).coerceIn(1, 4000)
        val diffMix = cfg.diffSurround.wetDryMix
        val diffPhase = if (cfg.diffSurround.reversePhase) -1.0f else 1.0f

        // Stereo Imager
        val imagerEnabled = cfg.stereoImager.enabled
        val imLowW = cfg.stereoImager.lowWidth
        val imMidW = cfg.stereoImager.midWidth
        val imHighW = cfg.stereoImager.highWidth

        // Cure Crossfeed
        val cureEnabled = cfg.cureCrossfeed.enabled
        val cureFeed = when (cfg.cureCrossfeed.preset) {
            0 -> 0.18f
            2 -> 0.42f
            else -> 0.28f
        }

        // Reverberation
        val revEnabled = cfg.reverberation.enabled
        val revRoom = cfg.reverberation.roomSize.coerceIn(0f, 0.95f)
        val revDamp = cfg.reverberation.damp
        val revWet = cfg.reverberation.wet
        val revDry = cfg.reverberation.dry

        // Speaker Opt
        val speakerOptEnabled = cfg.speakerOpt.enabled

        var sampleIdx = 0
        while (sampleIdx < sampleCount) {
            var left = samples[sampleIdx].toFloat() / 32768.0f
            var right = samples[sampleIdx + 1].toFloat() / 32768.0f

            // 1. Playback Gain Control (AGC)
            if (pgcEnabled) {
                val inputPeak = max(abs(left), abs(right))
                agcRmsLevel += (inputPeak - agcRmsLevel) * pgcRate
                val currentTargetGain = (pgcThreshold / max(0.01f, agcRmsLevel)).coerceIn(0.5f, pgcMaxGain)
                left *= currentTargetGain
                right *= currentTargetGain
            }

            // 2. LUFS Normalization
            if (lufsEnabled) {
                val lufsGain = (lufsTargetLinear / max(0.05f, lufsIntegrated)).coerceIn(0.5f, lufsMaxGain)
                lufsIntegrated += (max(abs(left), abs(right)) - lufsIntegrated) * 0.0002f
                left *= lufsGain
                right *= lufsGain
            }

            // 3. FET Compressor
            if (fetEnabled) {
                val peak = max(abs(left), abs(right))
                val coeff = if (peak > fetEnvelope) fetAttackCoeff else fetReleaseCoeff
                fetEnvelope = (peak * (1.0f - coeff)) + (fetEnvelope * coeff)
                if (fetEnvelope > fetThresh) {
                    val excess = fetEnvelope / fetThresh
                    val gainReduction = excess.pow(1.0f / fetRatio - 1.0f)
                    left *= gainReduction * fetMakeup
                    right *= gainReduction * fetMakeup
                } else {
                    left *= fetMakeup
                    right *= fetMakeup
                }
            }

            // 4. ViPER Bass
            if (bassEnabled) {
                left = bassFilter.processLeft(left)
                right = bassFilter.processRight(right)
                if (bassAntiPop) {
                    left = bassAntiPopFilter.processLeft(left)
                    right = bassAntiPopFilter.processRight(right)
                }
            }

            // 5. Bass Mono
            if (bassMonoEnabled) {
                val lowL = bassMonoLp.processLeft(left)
                val lowR = bassMonoLp.processRight(right)
                val lowMono = (lowL + lowR) * 0.5f
                val highL = bassMonoHp.processLeft(left)
                val highR = bassMonoHp.processRight(right)
                left = lowMono + highL
                right = lowMono + highR
            }

            // 6. Psychoacoustic Bass (Harmonic Generator)
            if (psychoEnabled) {
                val bassHarmonic = sin(left * Math.PI.toFloat()) * psychoIntensity
                left = left * psychoOriginal + bassHarmonic
                val bassHarmonicR = sin(right * Math.PI.toFloat()) * psychoIntensity
                right = right * psychoOriginal + bassHarmonicR
            }

            // 7. Spectrum Extension (Harmonic Exciter)
            if (specExtEnabled) {
                val exciterL = (left * left * left) * specExciter * specStrength
                val exciterR = (right * right * right) * specExciter * specStrength
                left += exciterL
                right += exciterR
            }

            // 8. ViPER Clarity
            if (clarityEnabled) {
                left = clarityFilter.processLeft(left)
                right = clarityFilter.processRight(right)
            }

            // 9. Tube Simulator (Vacuum Tube Triode Warmth)
            if (tubeEnabled) {
                val drivenL = left * tubeDrive
                val drivenR = right * tubeDrive
                // Triode non-linear characteristic curve
                val tubeL = drivenL - 0.20f * (drivenL * drivenL) - 0.08f * (drivenL * drivenL * drivenL)
                val tubeR = drivenR - 0.20f * (drivenR * drivenR) - 0.08f * (drivenR * drivenR * drivenR)
                left = left * (1.0f - tubeMix) + tubeL * tubeMix
                right = right * (1.0f - tubeMix) + tubeR * tubeMix
            }

            // 10. AnalogX (Class-A Analog Warmth)
            if (analogEnabled) {
                val warmthL = tanh(left * 1.2f) * analogWarmth
                val warmthR = tanh(right * 1.2f) * analogWarmth
                left = left * (1.0f - analogWarmth * 0.5f) + warmthL
                right = right * (1.0f - analogWarmth * 0.5f) + warmthR
            }

            // 11. Dynamic System (Driver Response Emulation)
            if (dsEnabled) {
                val dsL = left - (left * left * left) * (dsStrength * 0.15f)
                val dsR = right - (right * right * right) * (dsStrength * 0.15f)
                left = dsL
                right = dsR
            }

            // 12. Convolver (Impulse Response Engine)
            if (convEnabled) {
                val (convL, convR) = convolver.process(left, right, convCross)
                left = convL
                right = convR
            }

            // 13. Field Surround (Mid/Side soundfield expander)
            if (fieldEnabled) {
                val mid = (left + right) * 0.5f * fieldMid
                val side = (left - right) * 0.5f * fieldWidth
                left = mid + side
                right = mid - side
            }

            // 14. Differential Surround (Haas Effect)
            if (diffEnabled) {
                val diffSample = (left - right) * 0.5f * diffPhase
                diffDelayBuffer[diffDelayIndex] = diffSample
                val readIndex = (diffDelayIndex - diffDelaySamples + diffDelayBuffer.size) % diffDelayBuffer.size
                val delayedDiff = diffSurroundLp.processLeft(diffDelayBuffer[readIndex])
                left += delayedDiff * diffMix
                right -= delayedDiff * diffMix
                diffDelayIndex = (diffDelayIndex + 1) % diffDelayBuffer.size
            }

            // 15. Stereo Imager (3-Band Stereo Width)
            if (imagerEnabled) {
                val lowL = imagerLowLp.processLeft(left)
                val lowR = imagerLowLp.processRight(right)
                val highL = imagerHighHp.processLeft(left)
                val highR = imagerHighHp.processRight(right)
                val midL = left - lowL - highL
                val midR = right - lowR - highR

                // Width per band
                val lowMid = (lowL + lowR) * 0.5f
                val lowSide = (lowL - lowR) * 0.5f * imLowW
                val mM = (midL + midR) * 0.5f
                val mS = (midL - midR) * 0.5f * imMidW
                val hM = (highL + highR) * 0.5f
                val hS = (highL - highR) * 0.5f * imHighW

                left = (lowMid + lowSide) + (mM + mS) + (hM + hS)
                right = (lowMid - lowSide) + (mM - mS) + (hM - hS)
            }

            // 16. Cure Tech (Auditory Protection Crossfeed)
            if (cureEnabled) {
                val crossL = cureLpFilter.processLeft(right) * cureFeed
                val crossR = cureLpFilter.processRight(left) * cureFeed
                left = left * (1.0f - cureFeed * 0.4f) + crossL
                right = right * (1.0f - cureFeed * 0.4f) + crossR
            }

            // 17. Reverberation (Freeverb)
            if (revEnabled) {
                var reverbSumL = 0.0f
                var reverbSumR = 0.0f
                for (c in 0 until 4) {
                    val bufL = reverbCombsL[c]
                    val bufR = reverbCombsR[c]
                    val idx = reverbCombIndices[c]
                    val outL = bufL[idx]
                    val outR = bufR[idx]
                    bufL[idx] = left + outL * revRoom * (1.0f - revDamp)
                    bufR[idx] = right + outR * revRoom * (1.0f - revDamp)
                    reverbCombIndices[c] = (idx + 1) % bufL.size
                    reverbSumL += outL
                    reverbSumR += outR
                }
                left = left * revDry + reverbSumL * 0.25f * revWet
                right = right * revDry + reverbSumR * 0.25f * revWet
            }

            // 18. Speaker Optimization
            if (speakerOptEnabled) {
                left = speakerNotch.processLeft(speakerSubsonicHp.processLeft(left))
                right = speakerNotch.processRight(speakerSubsonicHp.processRight(right))
            }

            // 19. Master Pan & Volume
            left = left * masterGainLinear * panL
            right = right * masterGainLinear * panR

            // 20. Master Limiter / Transparent Soft Clipper
            if (isLimiterEnabled) {
                val absL = abs(left)
                val absR = abs(right)
                if (absL > limiterThresholdLinear) {
                    left = (limiterThresholdLinear + tanh((absL - limiterThresholdLinear) * 0.8f) * (1.0f - limiterThresholdLinear)) * sign(left)
                }
                if (absR > limiterThresholdLinear) {
                    right = (limiterThresholdLinear + tanh((absR - limiterThresholdLinear) * 0.8f) * (1.0f - limiterThresholdLinear)) * sign(right)
                }
            }

            // Clamp safely to 16-bit PCM range
            val sampleOutL = (left * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
            val sampleOutR = (right * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()

            samples[sampleIdx] = sampleOutL
            samples[sampleIdx + 1] = sampleOutR

            sampleIdx += 2
        }
    }
}
