package com.android.purebilibili.feature.video.playback.audio

import com.android.purebilibili.data.model.response.Dash
import com.android.purebilibili.data.model.response.DashAudio
import com.android.purebilibili.data.model.response.Dolby
import com.android.purebilibili.data.model.response.Flac
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AudioStreamSelectionPolicyTest {

    private val standard192 = DashAudio(
        id = 30280,
        baseUrl = "https://example.com/audio-192.m4s",
        bandwidth = 192_000
    )
    private val standard64 = DashAudio(
        id = 30216,
        baseUrl = "https://example.com/audio-64.m4s",
        bandwidth = 64_000
    )
    private val dolby = DashAudio(
        id = AUDIO_QUALITY_DOLBY,
        baseUrl = "https://example.com/audio-dolby.m4s",
        bandwidth = 448_000,
        mimeType = "audio/mp4",
        codecs = "ec-3"
    )
    private val hiRes = DashAudio(
        id = AUDIO_QUALITY_HI_RES,
        baseUrl = "https://example.com/audio-hires.m4s",
        bandwidth = 1_800_000,
        mimeType = "audio/mp4",
        codecs = "fLaC"
    )

    @Test
    fun `collect candidates includes standard dolby and hi res streams`() {
        val candidates = collectAudioStreamCandidates(fullDash())

        assertEquals(
            listOf(AudioStreamKind.HI_RES, AudioStreamKind.DOLBY, AudioStreamKind.STANDARD, AudioStreamKind.STANDARD),
            candidates.map { it.kind }
        )
        assertEquals(
            listOf(AUDIO_QUALITY_HI_RES, AUDIO_QUALITY_DOLBY, 30280, 30216),
            candidates.map { it.preferenceId }
        )
    }

    @Test
    fun `auto selects best standard audio without enabling premium audio`() {
        val decision = resolveAudioStreamSelection(
            dash = fullDash(),
            requestedAudioQuality = AUDIO_QUALITY_AUTO
        )

        assertEquals(30280, decision.selectedPreferenceId)
        assertEquals(AudioStreamKind.STANDARD, decision.selected?.kind)
        assertEquals(30280, decision.selected?.track?.id)
        assertNull(decision.fallbackReason)
    }

    @Test
    fun `explicit hi res selection returns flac candidate`() {
        val decision = resolveAudioStreamSelection(
            dash = fullDash(),
            requestedAudioQuality = AUDIO_QUALITY_HI_RES
        )

        assertEquals(AUDIO_QUALITY_HI_RES, decision.selectedPreferenceId)
        assertEquals("https://example.com/audio-hires.m4s", decision.selected?.track?.getValidUrl())
        assertNull(decision.fallbackReason)
    }

    @Test
    fun `missing hi res falls back to best standard without changing request`() {
        val decision = resolveAudioStreamSelection(
            dash = Dash(audio = listOf(standard64, standard192)),
            requestedAudioQuality = AUDIO_QUALITY_HI_RES
        )

        assertEquals(AUDIO_QUALITY_HI_RES, decision.requestedPreferenceId)
        assertEquals(30280, decision.selectedPreferenceId)
        assertEquals(AudioFallbackReason.REQUESTED_UNAVAILABLE, decision.fallbackReason)
    }

    @Test
    fun `high speed temporarily falls back from hi res to standard`() {
        val decision = resolveAudioStreamSelection(
            dash = fullDash(),
            requestedAudioQuality = AUDIO_QUALITY_HI_RES,
            playbackSpeed = 2.0f
        )

        assertEquals(AUDIO_QUALITY_HI_RES, decision.requestedPreferenceId)
        assertEquals(AUDIO_QUALITY_AUTO, decision.effectivePreferenceId)
        assertEquals(30280, decision.selectedPreferenceId)
        assertEquals(AudioFallbackReason.SPEED_INCOMPATIBLE, decision.fallbackReason)
        val presentation = resolveAudioQualityControlPresentation(decision.availableOptions, decision.selectedPreferenceId)
        assertEquals("192K", presentation.currentLabel)
    }

    @Test
    fun `concrete AAC default remains an exact bitrate preference`() {
        assertEquals(
            30280,
            resolveRequestedAudioQuality(
                defaultAudioQuality = 30280,
                rememberedAudioQuality = AUDIO_QUALITY_HI_RES
            )
        )
    }

    @Test
    fun `follow last default uses remembered manual selection`() {
        assertEquals(
            AUDIO_QUALITY_HI_RES,
            resolveRequestedAudioQuality(
                defaultAudioQuality = AUDIO_QUALITY_FOLLOW_LAST_SELECTED,
                rememberedAudioQuality = AUDIO_QUALITY_HI_RES
            )
        )
    }

    @Test
    fun `available options only expose tracks returned by current response`() {
        val options = buildAvailableAudioQualityOptions(
            collectAudioStreamCandidates(
                Dash(audio = listOf(standard192))
            )
        )

        assertEquals(listOf(AUDIO_QUALITY_AUTO, 30280), options.map { it.preferenceId })
        assertNull(options.first().kind)
        assertEquals("192K", options.last().label)
        assertTrue(options.none { it.isHiRes })
        assertTrue(options.none { it.isDolby })
    }

    @Test
    fun `dolby container does not promote AAC track to dolby`() {
        val aacInDolbyContainer = standard192.copy(
            baseUrl = "https://example.com/audio-aac-in-dolby-container.m4s",
            codecs = "mp4a.40.2"
        )

        val candidates = collectAudioStreamCandidates(
            Dash(
                audio = listOf(standard192),
                dolby = Dolby(type = 1, audio = listOf(aacInDolbyContainer))
            )
        )

        assertTrue(candidates.none { it.kind == AudioStreamKind.DOLBY })
    }

    @Test
    fun `alternate dolby IDs are selected by actual codec rather than one hardcoded ID`() {
        val alternateDolby = dolby.copy(id = 30255, baseUrl = "https://example.com/dolby-30255.m4s")
        val decision = resolveAudioStreamSelection(
            Dash(audio = listOf(standard192), dolby = Dolby(type = 1, audio = listOf(dolby, alternateDolby))),
            requestedAudioQuality = 30255
        )

        assertEquals(30255, decision.selectedPreferenceId)
        assertSame(alternateDolby, decision.selected?.track)
        assertEquals("https://example.com/dolby-30255.m4s", decision.selected?.track?.getValidUrl())
        assertEquals(AudioStreamKind.DOLBY, decision.selected?.kind)
        assertNull(decision.fallbackReason)
    }

    @Test
    fun `disabled dolby container does not expose dolby track`() {
        val candidates = collectAudioStreamCandidates(
            Dash(
                dolby = Dolby(type = 0, audio = listOf(dolby))
            )
        )

        assertTrue(candidates.none { it.kind == AudioStreamKind.DOLBY })
    }

    @Test
    fun `unsupported dolby decoder hides dolby and falls back to AAC`() {
        val decision = resolveAudioStreamSelection(
            dash = fullDash(),
            requestedAudioQuality = AUDIO_QUALITY_DOLBY,
            isDolbyAudioSupported = false
        )

        assertEquals(30280, decision.selectedPreferenceId)
        assertEquals(AudioStreamKind.STANDARD, decision.selected?.kind)
        assertEquals(AudioFallbackReason.REQUESTED_UNAVAILABLE, decision.fallbackReason)
        assertTrue(decision.availableOptions.none { it.isDolby })
    }

    @Test
    fun `premium options expose matching audio badges`() {
        val options = buildAvailableAudioQualityOptions(
            collectAudioStreamCandidates(fullDash())
        )

        val hiResOption = options.first { it.preferenceId == AUDIO_QUALITY_HI_RES }
        val dolbyOption = options.first { it.preferenceId == AUDIO_QUALITY_DOLBY }
        val aacOption = options.first { it.preferenceId == 30280 }

        assertEquals(
            listOf(AUDIO_QUALITY_AUTO, AUDIO_QUALITY_HI_RES, AUDIO_QUALITY_DOLBY, 30280, 30216),
            options.map { it.preferenceId }
        )
        assertTrue(hiResOption.isHiRes)
        assertTrue(!hiResOption.isDolby)
        assertTrue(dolbyOption.isDolby)
        assertTrue(!dolbyOption.isHiRes)
        assertEquals(AudioStreamKind.STANDARD, aacOption.kind)
    }

    @Test
    fun `software decoded dolby uses dolby audio label`() {
        val decision = resolveAudioStreamSelection(
            dash = fullDash(),
            requestedAudioQuality = AUDIO_QUALITY_DOLBY,
            isDolbyAudioSupported = true,
            isDolbyAudioSoftwareDecoded = true
        )

        val dolbyOption = decision.availableOptions.first { it.preferenceId == AUDIO_QUALITY_DOLBY }
        val presentation = resolveAudioQualityControlPresentation(
            options = decision.availableOptions,
            selectedAudioQuality = AUDIO_QUALITY_DOLBY
        )

        assertEquals("杜比音频", dolbyOption.label)
        assertTrue(dolbyOption.isSoftwareDecoded)
        assertEquals("杜比音频", presentation.label)
        assertTrue(presentation.showDolbyBadge)
    }

    @Test
    fun `platform decoded dolby keeps atmos label`() {
        val decision = resolveAudioStreamSelection(
            dash = fullDash(),
            requestedAudioQuality = AUDIO_QUALITY_DOLBY,
            isDolbyAudioSupported = true,
            isDolbyAudioSoftwareDecoded = false
        )

        val dolbyOption = decision.availableOptions.first { it.preferenceId == AUDIO_QUALITY_DOLBY }
        assertEquals("杜比全景声", dolbyOption.label)
        assertTrue(!dolbyOption.isSoftwareDecoded)
    }

    @Test
    fun `audio quality control keeps a visible fallback when options are empty`() {
        val presentation = resolveAudioQualityControlPresentation(
            options = emptyList(),
            selectedAudioQuality = AUDIO_QUALITY_AUTO
        )

        assertEquals("音质", presentation.label)
        assertNull(presentation.currentLabel)
        assertTrue(!presentation.showHiResBadge)
        assertTrue(!presentation.showDolbyBadge)
    }

    @Test
    fun `automatic audio preference does not report a current track before selection`() {
        val options = buildAvailableAudioQualityOptions(collectAudioStreamCandidates(fullDash()))
        val presentation = resolveAudioQualityControlPresentation(options, AUDIO_QUALITY_AUTO)

        assertEquals("自动", presentation.label)
        assertNull(presentation.currentLabel)
    }

    @Test
    fun `single AAC option remains visible in audio quality control`() {
        val options = buildAvailableAudioQualityOptions(
            collectAudioStreamCandidates(Dash(audio = listOf(standard192)))
        )

        val presentation = resolveAudioQualityControlPresentation(
            options = options,
            selectedAudioQuality = 30280
        )

        assertEquals("192K", presentation.label)
    }

    @Test
    fun `Hi Res control shows explicit text next to badge`() {
        val options = buildAvailableAudioQualityOptions(
            collectAudioStreamCandidates(fullDash())
        )

        val presentation = resolveAudioQualityControlPresentation(
            options = options,
            selectedAudioQuality = AUDIO_QUALITY_HI_RES
        )

        assertEquals("Hi-Res", presentation.label)
        assertTrue(presentation.showHiResBadge)
    }

    @Test
    fun `each returned ordinary bitrate selects its own source and reports its actual ID`() {
        val standard132 = standard192.copy(
            id = 30232,
            bandwidth = 132_000,
            baseUrl = "https://example.com/audio-132.m4s"
        )
        val tracks = listOf(standard64, standard132, standard192)
        for (track in tracks) {
            val decision = resolveAudioStreamSelection(Dash(audio = tracks), track.id)

            assertEquals(track.id, decision.requestedPreferenceId)
            assertEquals(track.id, decision.effectivePreferenceId)
            assertEquals(track.id, decision.selectedPreferenceId)
            assertSame(track, decision.selected?.track)
            assertEquals(track.getValidUrl(), decision.selected?.track?.getValidUrl())
            assertNull(decision.fallbackReason)
        }
    }

    @Test
    fun `unavailable raw request falls back to best ordinary source without rewriting preference`() {
        val decision = resolveAudioStreamSelection(fullDash(), requestedAudioQuality = 30232)

        assertEquals(30232, decision.requestedPreferenceId)
        assertEquals(30232, decision.effectivePreferenceId)
        assertEquals(30280, decision.selectedPreferenceId)
        assertSame(standard192, decision.selected?.track)
        assertEquals(standard192.getValidUrl(), decision.selected?.track?.getValidUrl())
        assertEquals(AudioFallbackReason.REQUESTED_UNAVAILABLE, decision.fallbackReason)
    }

    @Test
    fun `raw IDs unknown to built in labels retain identity and raw codec metadata`() {
        val tracks = listOf(100008, 100009, 100010, 77777).mapIndexed { index, id ->
            DashAudio(id, baseUrl = "https://example.com/$id.m4s", bandwidth = index + 1, codecs = "mp4a.40.2")
        }
        for (track in tracks) {
            val decision = resolveAudioStreamSelection(Dash(audio = tracks), track.id)
            val option = decision.availableOptions.single { it.preferenceId == track.id }

            assertSame(track, decision.selected?.track)
            assertEquals(track.id, decision.selectedPreferenceId)
            assertEquals(track.id.toString(), option.label)
            assertEquals(track.codecs, option.codecDescription)
            assertEquals(AudioStreamKind.STANDARD, option.kind)
        }
    }

    @Test
    fun `raw special IDs from base list honor software decoding and speed boundaries`() {
        val alternateDolby = dolby.copy(id = 30255, baseUrl = "https://example.com/dolby-alt.m4s")
        val dash = Dash(audio = listOf(standard64, standard192, alternateDolby))
        val normal = resolveAudioStreamSelection(
            dash, 30255, playbackSpeed = 1.5f, isDolbyAudioSoftwareDecoded = true
        )
        val fast = resolveAudioStreamSelection(
            dash, 30255, playbackSpeed = 1.51f, isDolbyAudioSoftwareDecoded = true
        )
        val restored = resolveAudioStreamSelection(dash, fast.requestedPreferenceId, playbackSpeed = 1f)

        assertSame(alternateDolby, normal.selected?.track)
        assertTrue(normal.selected?.isSoftwareDecoded == true)
        assertEquals("ec-3", normal.availableOptions.single { it.preferenceId == 30255 }.codecDescription)
        assertEquals(30255, fast.requestedPreferenceId)
        assertEquals(AUDIO_QUALITY_AUTO, fast.effectivePreferenceId)
        assertEquals(30280, fast.selectedPreferenceId)
        assertSame(standard192, fast.selected?.track)
        assertEquals(AudioFallbackReason.SPEED_INCOMPATIBLE, fast.fallbackReason)
        assertSame(alternateDolby, restored.selected?.track)
        assertNull(restored.fallbackReason)
    }

    @Test
    fun `invalid empty and duplicate rows expose only actual playable identities`() {
        val alternate = standard192.copy(baseUrl = "https://example.com/audio-192-better.m4s", bandwidth = 200_000)
        val decision = resolveAudioStreamSelection(
            Dash(audio = listOf(standard192, alternate, standard64.copy(baseUrl = ""), standard64.copy(id = 0))),
            requestedAudioQuality = 30280
        )

        assertSame(alternate, decision.selected?.track)
        assertEquals(listOf(30280), decision.availableOptions.filter { it.kind != null }.map { it.preferenceId })
        assertEquals(listOf(AUDIO_QUALITY_AUTO), decision.availableOptions.filter { it.kind == null }.map { it.preferenceId })
    }

    @Test
    fun `follow last preserves raw preference and rejects only nontrack sentinels`() {
        for (id in listOf(30216, 30232, 30280, 30250, 30255, 30251, 100008, 100009, 100010)) {
            assertEquals(id, resolveRequestedAudioQuality(AUDIO_QUALITY_FOLLOW_LAST_SELECTED, id))
            assertEquals(id, normalizeAudioQualityPreference(id))
        }
        assertEquals(AUDIO_QUALITY_AUTO, normalizeAudioQualityPreference(0))
        assertEquals(AUDIO_QUALITY_AUTO, normalizeAudioQualityPreference(-10))
    }

    @Test
    fun `flac container preserves noncanonical raw identity through speed fallback and restoration`() {
        val rawFlac = hiRes.copy(id = 100010, baseUrl = "https://example.com/raw-flac.m4s")
        val dash = Dash(audio = listOf(standard192), flac = Flac(audio = rawFlac))
        val normal = resolveAudioStreamSelection(dash, 100010)
        val fast = resolveAudioStreamSelection(dash, 100010, playbackSpeed = 2f)
        val restored = resolveAudioStreamSelection(dash, fast.requestedPreferenceId, playbackSpeed = 1f)

        assertEquals(100010, normal.selectedPreferenceId)
        assertSame(rawFlac, normal.selected?.track)
        assertEquals(AudioStreamKind.HI_RES, normal.availableOptions.single { it.preferenceId == 100010 }.kind)
        assertEquals("fLaC", normal.availableOptions.single { it.preferenceId == 100010 }.codecDescription)
        assertEquals(100010, fast.requestedPreferenceId)
        assertSame(standard192, fast.selected?.track)
        assertEquals(AudioFallbackReason.SPEED_INCOMPATIBLE, fast.fallbackReason)
        assertSame(rawFlac, restored.selected?.track)
        assertEquals(100010, restored.selectedPreferenceId)
    }

    @Test
    fun `no playable source retains request without reporting a fabricated track`() {
        val decision = resolveAudioStreamSelection(Dash(audio = emptyList()), 30232)

        assertEquals(30232, decision.requestedPreferenceId)
        assertNull(decision.selected)
        assertEquals(AUDIO_QUALITY_AUTO, decision.selectedPreferenceId)
        assertTrue(decision.availableOptions.isEmpty())
        assertEquals(AudioFallbackReason.NO_PLAYABLE_AUDIO, decision.fallbackReason)
    }

    private fun fullDash(): Dash {
        return Dash(
            audio = listOf(standard192, standard64),
            dolby = Dolby(type = 1, audio = listOf(dolby)),
            flac = Flac(display = true, audio = hiRes)
        )
    }
}
