package com.android.purebilibili.feature.video.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * 基于 ML Kit（GMS 按需下发中文/拉丁模型）的端侧画面文字识别引擎。
 * 识别全程在本地完成，不上传截图也不依赖外部 API Key。
 */
internal object OnDeviceFrameOcrEngine {

    suspend fun recognizeFromUri(
        context: Context,
        imageUri: Uri,
    ): VideoFrameOcrResult = withContext(Dispatchers.Default) {
        val appContext = context.applicationContext ?: context
        val inputImage = try {
            InputImage.fromFilePath(appContext, imageUri)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return@withContext VideoFrameOcrResult.Failure(resolveOcrFailureMessage(error.message))
        }

        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        try {
            suspendCancellableCoroutine<VideoFrameOcrResult> { continuation ->
                continuation.invokeOnCancellation {
                    runCatching { recognizer.close() }
                }
                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        val blocks = normalizeOcrBlocks(visionText.textBlocks.map { it.text })
                        if (continuation.isActive) {
                            continuation.resume(VideoFrameOcrResult.Success(blocks))
                        }
                    }
                    .addOnFailureListener { error ->
                        if (continuation.isActive) {
                            continuation.resume(
                                VideoFrameOcrResult.Failure(resolveOcrFailureMessage(error.message))
                            )
                        }
                    }
            }
        } finally {
            runCatching { recognizer.close() }
        }
    }
}
