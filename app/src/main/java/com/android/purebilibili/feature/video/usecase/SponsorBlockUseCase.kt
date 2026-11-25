// File: feature/video/usecase/SponsorBlockUseCase.kt
package com.android.purebilibili.feature.video.usecase

import android.content.Context
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.data.model.response.SponsorSegment
import com.android.purebilibili.data.repository.SponsorBlockRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * SponsorBlock UseCase
 * 
 * Handles SponsorBlock logic:
 * - Load sponsor segments
 * - Check current playback position for segments
 * - Execute auto-skip or show skip button
 * 
 * Requirement Reference: AC1.5 - SponsorBlock handled by independent UseCase
 */
class SponsorBlockUseCase {
    
    companion object {
        private const val TAG = "SponsorBlockUseCase"
    }
    
    // Segment list
    private val _segments = MutableStateFlow<List<SponsorSegment>>(emptyList())
    val segments: StateFlow<List<SponsorSegment>> = _segments.asStateFlow()
    
    // Current segment being displayed
    private val _currentSegment = MutableStateFlow<SponsorSegment?>(null)
    val currentSegment: StateFlow<SponsorSegment?> = _currentSegment.asStateFlow()
    
    // Whether to show skip button
    private val _showSkipButton = MutableStateFlow(false)
    val showSkipButton: StateFlow<Boolean> = _showSkipButton.asStateFlow()
    
    // Already auto-skipped segment UUIDs to avoid duplicate skips
    private val skippedSegmentIds = mutableSetOf<String>()
    
    /**
     * Load sponsor segments for a video
     */
    suspend fun loadSegments(bvid: String) {
        try {
            val loadedSegments = SponsorBlockRepository.getSegments(bvid)
            _segments.value = loadedSegments
            skippedSegmentIds.clear()
            Logger.d(TAG, "Loaded ${loadedSegments.size} segments for $bvid")
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Logger.w(TAG, "Load failed: ${e.message}")
            _segments.value = emptyList()
        }
    }
    
    /**
     * Check current playback position for sponsor segments and decide whether to skip
     */
    suspend fun checkAndSkip(
        context: Context,
        currentPositionMs: Long,
        onSeek: (Long) -> Unit
    ): SkipResult {
        val segmentList = _segments.value
        if (segmentList.isEmpty()) return SkipResult.NO_SEGMENT
        
        val segment = SponsorBlockRepository.findSegmentAtPosition(segmentList, currentPositionMs)
        
        if (segment != null && segment.UUID !in skippedSegmentIds) {
            _currentSegment.value = segment
            
            // Check if auto-skip is enabled
            val autoSkip = SettingsManager.getSponsorBlockAutoSkip(context).first()
            
            if (autoSkip) {
                // Execute auto-skip
                onSeek(segment.endTimeMs)
                skippedSegmentIds.add(segment.UUID)
                _currentSegment.value = null
                _showSkipButton.value = false
                Logger.d(TAG, "Auto-skipped: ${segment.categoryName}")
                return SkipResult.SKIPPED
            } else {
                // Show skip button
                _showSkipButton.value = true
                return SkipResult.SHOW_BUTTON
            }
        } else if (segment == null) {
            _currentSegment.value = null
            _showSkipButton.value = false
        }
        
        return SkipResult.NO_SEGMENT
    }
    
    /**
     * Manually skip current sponsor segment
     */
    fun skipCurrent(onSeek: (Long) -> Unit): SponsorSegment? {
        val segment = _currentSegment.value ?: return null
        
        onSeek(segment.endTimeMs)
        skippedSegmentIds.add(segment.UUID)
        _currentSegment.value = null
        _showSkipButton.value = false
        
        Logger.d(TAG, "Manual skip: ${segment.categoryName}")
        return segment
    }
    
    /**
     * Dismiss current sponsor segment (do not skip)
     */
    fun dismiss() {
        val segment = _currentSegment.value ?: return
        skippedSegmentIds.add(segment.UUID)
        _currentSegment.value = null
        _showSkipButton.value = false
        
        Logger.d(TAG, "Dismissed: ${segment.categoryName}")
    }
    
    /**
     * Reset state (call when switching videos)
     */
    fun reset() {
        _segments.value = emptyList()
        _currentSegment.value = null
        _showSkipButton.value = false
        skippedSegmentIds.clear()
        
        Logger.d(TAG, "Reset")
    }
    
    /**
     * Check if there are active segments (for adjusting check frequency)
     */
    fun hasActiveSegments(): Boolean {
        return _segments.value.isNotEmpty() || _currentSegment.value != null
    }
}

/**
 * Skip result enum
 */
enum class SkipResult {
    /** Auto-skipped */
    SKIPPED,
    /** Show skip button (waiting for user action) */
    SHOW_BUTTON,
    /** No segment at current position */
    NO_SEGMENT
}
