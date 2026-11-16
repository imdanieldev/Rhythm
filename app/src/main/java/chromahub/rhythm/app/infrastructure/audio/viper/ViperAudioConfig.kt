/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-FileCopyrightText: 2026 Rhythm Audio Enhancement Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.audio.viper

/**
 * ViPER4Android DSP audio configurations and parameter states for Rhythm.
 * Covers all 24 audio processing modules with full precision and range limits.
 */

data class MasterGateConfig(
    val limiterEnabled: Boolean = true,
    val limiterThresholdDb: Float = -0.5f, // -6.0dB to 0.0dB
    val outputVolumeDb: Float = 0.0f,     // -20.0dB to +6.0dB
    val channelPan: Float = 0.0f          // -1.0f (full left) to +1.0f (full right)
)

data class PlaybackGainConfig(
    val enabled: Boolean = false,
    val strength: Int = 2,                // 1 (Subtle), 2 (Moderate), 3 (Aggressive)
    val maxGainDb: Float = 6.0f,          // 0.0dB to 18.0dB
    val outputThresholdDb: Float = -1.0f  // -6.0dB to 0.0dB
)

data class LufsConfig(
    val enabled: Boolean = false,
    val targetLufs: Float = -14.0f,       // -24.0 to -8.0 LUFS (EBU R128 standard)
    val maxGainDb: Float = 6.0f,          // 0.0dB to 12.0dB
    val speed: Int = 1                    // 0: Slow, 1: Moderate, 2: Fast
)

data class FetCompressorConfig(
    val enabled: Boolean = false,
    val thresholdDb: Float = -18.0f,      // -40.0dB to 0.0dB
    val ratio: Float = 2.5f,              // 1.0 to 10.0
    val attackMs: Float = 15.0f,          // 0.1ms to 60.0ms
    val releaseMs: Float = 60.0f,         // 10.0ms to 500.0ms
    val makeupGainDb: Float = 0.0f,       // 0.0dB to 15.0dB
    val noClip: Boolean = true
)

data class MultibandCompressorConfig(
    val enabled: Boolean = false,
    val crossover1: Int = 120,            // Hz (Sub-bass / Low)
    val crossover2: Int = 500,            // Hz (Low / Mid)
    val crossover3: Int = 4000,           // Hz (Mid / High)
    val crossover4: Int = 8000,           // Hz (High / Air)
    val bandEnables: List<Boolean> = listOf(true, true, true, true, true),
    val thresholdsDb: List<Float> = listOf(-18f, -18f, -16f, -14f, -12f),
    val ratios: List<Float> = listOf(2.5f, 2.0f, 1.8f, 1.8f, 1.5f),
    val gainsDb: List<Float> = listOf(0f, 0f, 0f, 0f, 0f)
)

data class ViperBassConfig(
    val enabled: Boolean = false,
    val mode: Int = 0,                    // 0: Natural Bass, 1: Pure Bass, 2: Subwoofer
    val frequencyHz: Int = 60,            // 30Hz to 200Hz
    val gainDb: Float = 4.0f,             // 0.0dB to 18.0dB
    val antiPop: Boolean = true
)

data class BassMonoConfig(
    val enabled: Boolean = false,
    val frequencyHz: Int = 80             // 40Hz to 200Hz
)

data class PsychoacousticBassConfig(
    val enabled: Boolean = false,
    val cutoffHz: Int = 80,               // 40Hz to 150Hz
    val intensity: Float = 0.5f,          // 0.0 to 1.0 (50%)
    val harmonicOrder: Int = 3,           // 2: 2nd harmonic, 3: 3rd, 4: 4th
    val originalLevel: Float = 0.8f       // 0.0 to 1.0
)

data class SpectrumExtensionConfig(
    val enabled: Boolean = false,
    val strength: Float = 0.5f,           // 0.0 to 1.0
    val exciter: Float = 0.3f             // 0.0 to 1.0
)

data class ViperClarityConfig(
    val enabled: Boolean = false,
    val mode: Int = 1,                    // 0: Natural, 1: Ozone+, 2: X-HiFi
    val gainDb: Float = 3.5f              // 0.0dB to 12.0dB
)

data class TubeSimulatorConfig(
    val enabled: Boolean = false,
    val model: Int = 0,                   // 0: 6SN7 Triode, 1: 12AX7 High Gain, 2: Warm FET
    val drive: Float = 0.4f,              // 0.0 to 1.0
    val mix: Float = 0.3f                 // 0.0 to 1.0 (30% wet)
)

data class AnalogXConfig(
    val enabled: Boolean = false,
    val mode: Int = 1                     // 0: Light, 1: Moderate, 2: Warm / Vintage
)

data class DynamicSystemConfig(
    val enabled: Boolean = false,
    val device: Int = 1,                  // 0: Common Earphone, 1: High-End Headphone, 2: Apple EarPods, 3: Extreme Bass, 4: Speaker
    val strength: Float = 0.4f            // 0.0 to 1.0
)

data class ConvolverConfig(
    val enabled: Boolean = false,
    val kernelName: String = "Studio Master", // Built-in or custom IRS name
    val crossChannel: Float = 0.0f        // 0.0 to 1.0
)

data class FieldSurroundConfig(
    val enabled: Boolean = false,
    val widening: Float = 0.4f,           // 0.0 to 1.0
    val midImage: Float = 1.0f,           // 0.5 to 2.0
    val depthMs: Float = 15.0f            // 0.0 to 40.0 ms
)

data class DiffSurroundConfig(
    val enabled: Boolean = false,
    val delayMs: Float = 6.0f,            // 1.0ms to 25.0ms
    val reversePhase: Boolean = false,
    val wetDryMix: Float = 0.5f,          // 0.0 to 1.0
    val lpCutoffHz: Int = 6000            // 1000Hz to 12000Hz
)

data class StereoImagerConfig(
    val enabled: Boolean = false,
    val lowWidth: Float = 0.7f,           // 0.0 to 2.0 (Tight low end)
    val midWidth: Float = 1.2f,           // 0.0 to 2.0 (Open mids)
    val highWidth: Float = 1.4f,          // 0.0 to 2.0 (Spacious highs)
    val lowCrossoverHz: Int = 250,        // 100Hz to 500Hz
    val highCrossoverHz: Int = 4000       // 2000Hz to 8000Hz
)

data class HeadphoneSurroundConfig(
    val enabled: Boolean = false,
    val quality: Int = 1                  // 0: Level 1 (Light), 1: Level 2 (Full VHS+)
)

data class ReverberationConfig(
    val enabled: Boolean = false,
    val roomSize: Float = 0.35f,          // 0.0 to 1.0
    val width: Float = 0.5f,              // 0.0 to 1.0
    val damp: Float = 0.4f,               // 0.0 to 1.0
    val wet: Float = 0.15f,               // 0.0 to 1.0
    val dry: Float = 0.9f                 // 0.0 to 1.0
)

data class CureCrossfeedConfig(
    val enabled: Boolean = false,
    val preset: Int = 1                   // 0: Slight, 1: Moderate (Recommended), 2: Strong
)

data class DynamicEqConfig(
    val enabled: Boolean = false,
    val band1Freq: Int = 80,              // Hz
    val band1GainDb: Float = 0.0f,
    val band1ThresholdDb: Float = -20.0f,
    val band2Freq: Int = 1000,            // Hz
    val band2GainDb: Float = 0.0f,
    val band2ThresholdDb: Float = -18.0f,
    val band3Freq: Int = 8000,            // Hz
    val band3GainDb: Float = 0.0f,
    val band3ThresholdDb: Float = -16.0f
)

data class SpeakerOptConfig(
    val enabled: Boolean = false
)

/**
 * Root configuration containing all ViPER4Android DSP states
 */
data class ViperMasterConfig(
    val masterEnabled: Boolean = false,
    val bindToSystemEffect: Boolean = false,
    val masterGate: MasterGateConfig = MasterGateConfig(),
    val playbackGain: PlaybackGainConfig = PlaybackGainConfig(),
    val lufs: LufsConfig = LufsConfig(),
    val fetCompressor: FetCompressorConfig = FetCompressorConfig(),
    val multibandCompressor: MultibandCompressorConfig = MultibandCompressorConfig(),
    val viperBass: ViperBassConfig = ViperBassConfig(),
    val bassMono: BassMonoConfig = BassMonoConfig(),
    val psychoacousticBass: PsychoacousticBassConfig = PsychoacousticBassConfig(),
    val spectrumExtension: SpectrumExtensionConfig = SpectrumExtensionConfig(),
    val viperClarity: ViperClarityConfig = ViperClarityConfig(),
    val tubeSimulator: TubeSimulatorConfig = TubeSimulatorConfig(),
    val analogX: AnalogXConfig = AnalogXConfig(),
    val dynamicSystem: DynamicSystemConfig = DynamicSystemConfig(),
    val convolver: ConvolverConfig = ConvolverConfig(),
    val fieldSurround: FieldSurroundConfig = FieldSurroundConfig(),
    val diffSurround: DiffSurroundConfig = DiffSurroundConfig(),
    val stereoImager: StereoImagerConfig = StereoImagerConfig(),
    val headphoneSurround: HeadphoneSurroundConfig = HeadphoneSurroundConfig(),
    val reverberation: ReverberationConfig = ReverberationConfig(),
    val cureCrossfeed: CureCrossfeedConfig = CureCrossfeedConfig(),
    val dynamicEq: DynamicEqConfig = DynamicEqConfig(),
    val speakerOpt: SpeakerOptConfig = SpeakerOptConfig()
)
