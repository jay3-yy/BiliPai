/** 头像与屏幕挖孔的融合绘制，包含 GPU 与 CPU 合成路径。 */
package com.android.purebilibili.feature.profile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.math.MathUtils;

public class ProfileAvatarFusionView extends FrameLayout {
    private static final float AVATAR_SIZE_DP = 100;
    private static final float BLACK_KING_BAR = 32;

    private final Paint blackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private Impl impl;
    private boolean hardwareEffectsAllowed;
    private final float density;
    private Runnable frameUpdater;
    private int statusBarHeight;
    private float viewportLeft, viewportTop;
    private final int[] windowOrigin = new int[2];
    private boolean rectangularContent;
    /** Rectangular hosts need their full banner in the CPU buffer, rather than an avatar strip. */
    public void setRectangularContent(boolean rectangular) { rectangularContent = rectangular; }
    public float getViewportTop() { return viewportTop; }
    public void setViewportOrigin(float left, float top) {
        if (viewportLeft == left && viewportTop == top) return;
        viewportLeft = left; viewportTop = top;
        refreshCutout();
    }
    private void refreshCutout() {
        notchInfo = ProfileCameraCutout.getInfo(this);
        if (!rectangularContent && ((notchInfo != null && notchInfo.gravity != Gravity.CENTER) ||
                getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)) {
            notchInfo = null;
        }
        if (notchInfo != null) {
            notchInfo.bounds.offset(-viewportLeft, -viewportTop);
            notchInfo.path.offset(-viewportLeft, -viewportTop);
            // A nested/non-edge-to-edge host cannot fuse with the physical cutout.
            if (notchInfo.bounds.bottom < 0 || notchInfo.bounds.centerX() < 0 || notchInfo.bounds.centerX() > getWidth()) notchInfo = null;
        }
    }

    private int dp(float value) { return Math.round(value * density); }
    private static float ilerp(float value, float from, float to) { return (value - from) / (to - from); }
    private static float lerp(float from, float to, float value) { return from + (to - from) * value; }
    private static float lerp(float from, float to, float start, float end, float value) {
        return lerp(from, to, MathUtils.clamp(ilerp(value, start, end), 0f, 1f));
    }

    /** Grow the cutout bridge as the avatar approaches, then retract it before full absorption. */
    public static float resolveGooeyBridgeFactor(float pullProgress) {
        float grow = MathUtils.clamp(ilerp(pullProgress, 0f, 0.5f), 0f, 1f);
        float retract = 1f - MathUtils.clamp(ilerp(pullProgress, 0.55f, 0.85f), 0f, 1f);
        return grow * retract;
    }

    /** Fade the avatar's black silhouette out once it sits inside the cutout. */
    public static int resolveGooeyLayerAlpha(float pullProgress, boolean rectangularContent) {
        if (rectangularContent) return 255;
        return MathUtils.clamp((int) ((1f - ilerp(pullProgress, 0.82f, 0.96f)) * 255f), 0, 255);
    }
    public void setFrameUpdater(Runnable updater) { frameUpdater = updater; }
    @Override public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        WindowInsets result = super.onApplyWindowInsets(insets);
        refreshCutout();
        invalidate();
        return result;
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        requestApplyInsets();
    }
    public void prepareFrame() {
        // Lazy-grid placement and parallax can move the host without a Compose layout callback.
        if (isAttachedToWindow()) {
            getLocationInWindow(windowOrigin);
            setViewportOrigin(windowOrigin[0], windowOrigin[1]);
        }
        float previousProgress = pullProgress;
        float previousBlur = blurIntensity;
        boolean previousEnabled = enabled;
        if (frameUpdater != null) frameUpdater.run();
        // Android's cached View display list must be refreshed when Compose scroll changes.
        if (previousProgress != pullProgress || previousBlur != blurIntensity || previousEnabled != enabled) invalidate();
    }
    public void setStatusBarHeight(int height) { statusBarHeight = height; }
    public void updateFrame(float progress, boolean motionEnabled) {
        updateFrame(progress, progress, motionEnabled);
    }
    /** Wide banners blur during the full crop, while cutout fusion uses its later phase. */
    public void updateFrame(float fusionProgress, float blurProgress, boolean motionEnabled) {
        setPullProgress(fusionProgress);
        setBlurIntensity(motionEnabled ? Math.min((MathUtils.clamp(blurProgress, 0.2f, 0.7f) - 0.2f) / 0.5f, 0.75f) : 0f);
        setGooeyEnabled(motionEnabled && fusionProgress < 1f && (fusionProgress > 0f || blurIntensity > 0f));
    }
    public void setHardwareEffectsEnabled(boolean allowed) {
        if (hardwareEffectsAllowed == allowed) return;
        hardwareEffectsAllowed = allowed;
        invalidate();
    }
    private void selectBackend(boolean hardwareCanvas) {
        boolean useGpu = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && hardwareEffectsAllowed && hardwareCanvas;
        if (useGpu == (impl instanceof GPUImpl)) return;
        impl.release();
        impl = useGpu ? new GPUImpl(1.5f) : new CPUImpl();
        impl.setIntensity(intensity);
        impl.setBlurIntensity(blurIntensity);
        if (getWidth() > 0 && getHeight() > 0) impl.onSizeChanged(getWidth(), getHeight());
    }
    public void release() { impl.release(); frameUpdater = null; }

    private float intensity;
    private float pullProgress;
    private float blurIntensity;
    private boolean enabled;

    @Nullable
    public ProfileCameraCutout.Info notchInfo;

    public ProfileAvatarFusionView(Context context, boolean hardwareEffects) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        hardwareEffectsAllowed = hardwareEffects;
        setClipChildren(false);
        setClipToPadding(false);

        blackPaint.setColor(Color.BLACK);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && hardwareEffects) {
            impl = new GPUImpl(1.5f);
        } else {
            impl = new CPUImpl();
        }
        setIntensity(15f);
        setBlurIntensity(0f);
        setWillNotDraw(false);
    }

    public float getEndOffset(boolean occupyStatusBar, float avatarScale) {
        if (notchInfo != null) {
            return -(dp(16) + (notchInfo.isLikelyCircle ? notchInfo.bounds.width() + notchInfo.bounds.width() * getAvatarEndScale() : notchInfo.bounds.height() - notchInfo.bounds.top));
        }

        return -((occupyStatusBar ? statusBarHeight : 0) + dp(16) + dp(AVATAR_SIZE_DP));
    }

    public float getAvatarEndScale() {
        if (notchInfo != null) {
            float f;
            if (notchInfo.isLikelyCircle) {
                f = (notchInfo.bounds.width() - dp(2)) / dp(AVATAR_SIZE_DP);
            } else {
                f = Math.min(notchInfo.bounds.width(), notchInfo.bounds.height()) / dp(AVATAR_SIZE_DP);
            }
            return Math.min(0.8f, f);
        }

        return 0.8f;
    }

    public boolean hasNotchInfo() {
        return notchInfo != null;
    }

    public void setIntensity(float intensity) {
        this.intensity = intensity;
        impl.setIntensity(intensity);

    }

    public void setPullProgress(float pullProgress) {
        this.pullProgress = pullProgress;

    }

    public void setBlurIntensity(float intensity) {
        this.blurIntensity = intensity;
        impl.setBlurIntensity(intensity);

    }

    public void setGooeyEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;

    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        refreshCutout();

        if (w > 0 && h > 0) impl.onSizeChanged(w, h);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        prepareFrame();
        if (pullProgress >= 1f) return;
        if (!enabled) {
            super.draw(canvas);
            return;
        }
        // RenderNode cannot draw onto a software canvas. Recreate the matching backend
        // after transient visual guards or canvas changes, retaining the current frame.
        selectBackend(canvas.isHardwareAccelerated());
        impl.draw(c -> {
            c.save();
            c.translate(0, dp(BLACK_KING_BAR));
            super.draw(c);
            c.restore();
        }, canvas);
    }

    private final class CPUImpl implements Impl {
        private Bitmap bitmap;
        private Canvas bitmapCanvas;
        private final Paint bitmapPaint = new Paint();
        private final Paint bitmapPaint2 = new Paint();

        private int optimizedH;
        private int optimizedW;

        private int bitmapOrigW, bitmapOrigH;
        private final float scaleConst = 6f;

        {
            bitmapPaint.setFlags(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
            bitmapPaint.setFilterBitmap(true);
            bitmapPaint2.setFlags(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
            bitmapPaint2.setFilterBitmap(true);
            bitmapPaint2.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
            bitmapPaint.setColorFilter(new ColorMatrixColorFilter(new float[] {
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 60, -7500
            }));
        }

        @Override
        public void onSizeChanged(int w, int h) {
            if (bitmap != null) {
                bitmap.recycle();
                bitmap = null;
            }

            optimizedW = rectangularContent ? w : Math.min(dp(120), w);
            optimizedH = rectangularContent ? h : Math.min(dp(220), h);

            bitmapOrigW = optimizedW;
            bitmapOrigH = optimizedH + dp(BLACK_KING_BAR);
            bitmap = Bitmap.createBitmap(Math.max(1, (int) (bitmapOrigW / scaleConst)), Math.max(1, (int) (bitmapOrigH / scaleConst)), Bitmap.Config.ARGB_8888);

            bitmapCanvas = new Canvas(bitmap);
        }

        @Override public void release() {
            if (bitmap != null) bitmap.recycle();
            bitmap = null;
            bitmapCanvas = null;
        }
        @Override
        public void draw(Drawer drawer, Canvas canvas) {
            if (bitmap == null || bitmap.isRecycled()) return;

            final float v = (MathUtils.clamp(blurIntensity, 0.2f, 0.3f) - 0.2f) / (0.3f - 0.2f);
            final int alpha = (int) ((1f - v) * 0xFF);
            final float optimizedOffsetX = (getWidth() - optimizedW) / 2f;

            // Offset everything for black bar
            canvas.save();
            canvas.translate(0, -dp(BLACK_KING_BAR));

            final int gooeyAlpha = resolveGooeyLayerAlpha(pullProgress, rectangularContent);
            if (alpha != 255 && gooeyAlpha > 0) {
                bitmap.eraseColor(0);

                bitmapCanvas.save();
                bitmapCanvas.scale((float) bitmap.getWidth() / bitmapOrigW, (float) bitmap.getHeight() / bitmapOrigH);
                bitmapCanvas.translate(-optimizedOffsetX, 0);
                drawer.draw(bitmapCanvas);
                bitmapCanvas.restore();

                bitmapCanvas.save();
                bitmapCanvas.scale((float) bitmap.getWidth() / bitmapOrigW, (float) bitmap.getHeight() / bitmapOrigH);
                if (notchInfo != null) {
                    bitmapCanvas.save();
                    bitmapCanvas.translate(-optimizedOffsetX, dp(BLACK_KING_BAR));
                    if (notchInfo.isLikelyCircle) {
                        float rad = Math.min(notchInfo.bounds.width(), notchInfo.bounds.height()) / 2f;
                        bitmapCanvas.drawCircle(notchInfo.bounds.centerX(), notchInfo.bounds.bottom - notchInfo.bounds.width() / 2f, rad, blackPaint);
                    } else if (notchInfo.isAccurate) {
                        bitmapCanvas.drawPath(notchInfo.path, blackPaint);
                    } else {
                        float rad = Math.max(notchInfo.bounds.width(), notchInfo.bounds.height()) / 2f;
                        bitmapCanvas.drawRoundRect(notchInfo.bounds, rad, rad, blackPaint);
                    }
                    bitmapCanvas.restore();
                } else {
                    bitmapCanvas.drawRect(0, 0, optimizedW, dp(BLACK_KING_BAR), blackPaint);
                }
                bitmapCanvas.restore();

                // Blur buffer
                ProfileAvatarBitmapBlur.blur(bitmap, (int) (intensity * 2 / scaleConst));

                // Filter alpha + fade, then draw
                canvas.save();
                canvas.translate(optimizedOffsetX, 0);
                if (gooeyAlpha != 255) {
                    canvas.saveLayerAlpha(0, 0, bitmapOrigW, bitmapOrigH, gooeyAlpha);
                }
                canvas.saveLayer(0, 0, bitmapOrigW, bitmapOrigH, null);
                canvas.scale((float) bitmapOrigW / bitmap.getWidth(), (float) bitmapOrigH / bitmap.getHeight());
                canvas.drawBitmap(bitmap, 0, 0, bitmapPaint);
                canvas.drawBitmap(bitmap, 0, 0, bitmapPaint2);
                canvas.restore();
                if (gooeyAlpha != 255) {
                    canvas.restore();
                }
                canvas.restore();
            }

            // Fade, draw blurred
            if (alpha != 0) {
                if (alpha != 255) {
                    canvas.saveLayerAlpha(optimizedOffsetX, 0, optimizedOffsetX + optimizedW, optimizedH, alpha);
                }
                drawer.draw(canvas);
                if (alpha != 255) {
                    canvas.restore();
                }
            }

            canvas.restore();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private final class GPUImpl implements Impl {
        private final Paint filter = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RenderNode node = new RenderNode("render");
        private final RenderNode effectNotchNode = new RenderNode("effectNotch");
        private final RenderNode effectNode = new RenderNode("effect");
        private final RenderNode blurNode = new RenderNode("blur");
        private final float factorMult;

        private final RectF whole = new RectF();
        private final RectF temp = new RectF();

        private final Paint blackNodePaint = new Paint();

        private float installedBlur = -1f;

        private GPUImpl(float factorMult) {
            this.factorMult = factorMult;

            blackNodePaint.setColor(Color.BLACK);
            blackNodePaint.setBlendMode(BlendMode.SRC_IN);
        }

        @Override
        public void setIntensity(float intensity) {
            effectNode.setRenderEffect(RenderEffect.createBlurEffect(intensity, intensity, Shader.TileMode.CLAMP));
            effectNotchNode.setRenderEffect(RenderEffect.createBlurEffect(intensity, intensity, Shader.TileMode.CLAMP));
            filter.setColorFilter(new ColorMatrixColorFilter(new float[] {
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 51, 51 * -125
            }));
        }

        @Override
        public void setBlurIntensity(float blurIntensity) {
            if (installedBlur == blurIntensity) return;
            installedBlur = blurIntensity;
            if (blurIntensity == 0) {
                blurNode.setRenderEffect(null);
                return;
            }
            blurNode.setRenderEffect(RenderEffect.createBlurEffect(blurIntensity * intensity / factorMult, blurIntensity * intensity / factorMult, Shader.TileMode.DECAL));
        }

        @Override public void release() {
            node.discardDisplayList();
            effectNode.discardDisplayList();
            effectNotchNode.discardDisplayList();
            blurNode.discardDisplayList();
        }
        private final RectF wholeOptimized = new RectF();

        @Override
        public void draw(Drawer drawer, @NonNull Canvas canvas) {
            if (!canvas.isHardwareAccelerated()) {
                return;
            }
            Canvas c;

            whole.set(0, 0, getWidth(), getHeight());
            if (getChildCount() > 0) {
                final View child = getChildAt(0);
                final float w = child.getWidth() * child.getScaleX();
                final float h = child.getHeight() * child.getScaleY();
                final float l = child.getX();
                final float t = child.getY();

                wholeOptimized.set(l, t, l + w, t + h);
                if (notchInfo != null) {
                    wholeOptimized.union(notchInfo.bounds);
                }
                wholeOptimized.inset(-dp(20), -dp(20));
                wholeOptimized.intersect(whole);
                wholeOptimized.top = 0;
            } else {
                wholeOptimized.set(whole);
            }
            wholeOptimized.bottom += dp(BLACK_KING_BAR);

            final int width = (int) Math.ceil(wholeOptimized.width());
            final int height = (int) Math.ceil(wholeOptimized.height());
            final float left = wholeOptimized.left;
            final float top = wholeOptimized.top;

            node.setPosition(0, 0, width, height);
            blurNode.setPosition(0, 0, width, height);
            effectNode.setPosition(0, 0, width, height);
            effectNotchNode.setPosition(0, 0, width, height);
            wholeOptimized.set(0, 0, width, height);

            // Record everything into buffer
            c = node.beginRecording();
            c.translate(-left, -top);
            final int imageAlphaNoClamp = (int) ((1f - ilerp(pullProgress, rectangularContent ? 0.85f : 0.5f, 1.0f)) * 255);
            // Photos retain their colour as they contract; the avatar's black absorption
            // silhouette looks like a large grey block when applied to a wide banner.
            final int imageAlpha = MathUtils.clamp(imageAlphaNoClamp, 0, 255);
            drawer.draw(c);
            node.endRecording();

            // Blur only buffer
            float blurScaleFactor = factorMult / 4f + 1f + blurIntensity * 0.5f * factorMult + (factorMult - 1f) * 2f;
            c = blurNode.beginRecording();
            c.scale(1f / blurScaleFactor, 1f / blurScaleFactor, 0, 0);
            c.drawRenderNode(node);
            blurNode.endRecording();

            // Blur + filter buffer
            float gooScaleFactor = 2f + factorMult;
            final int gooeyAlpha = resolveGooeyLayerAlpha(pullProgress, rectangularContent);
            if (gooeyAlpha > 0) {
                c = effectNode.beginRecording();
                c.scale(1f / gooScaleFactor, 1f / gooScaleFactor, 0, 0);
                if (imageAlpha < 255) {
                    c.saveLayer(wholeOptimized, null);
                    c.drawRenderNode(node);
                    c.drawRect(wholeOptimized, blackNodePaint);
                    c.restore();
                }
                final float h = dp(7) * gooScaleFactor * resolveGooeyBridgeFactor(pullProgress);
                if (getChildCount() > 0) {
                    final View child = getChildAt(0);
                    final float cx = child.getX() + child.getWidth() * child.getScaleX() / 2.0f - left;
                    final float cy = child.getY() + child.getHeight() * child.getScaleY() / 2.0f + dp(BLACK_KING_BAR) - top;
                    final float r = rectangularContent
                            ? Math.min(child.getWidth() * child.getScaleX(), child.getHeight() * child.getScaleY()) / 2f
                            : child.getWidth() / 2.0f * child.getScaleX();

                    path.rewind();
                    path.moveTo(cx - r, cy - (float) Math.cos(Math.PI / 4) * r);
                    path.lineTo(cx, cy - r - h * 0.25f);
                    path.lineTo(cx + r, cy - (float) Math.cos(Math.PI / 4) * r);
                    path.close();
                    c.drawPath(path, blackPaint);
                }
                if (imageAlpha > 0) {
                    if (imageAlpha != 255) {
                        c.saveLayerAlpha(wholeOptimized, imageAlpha);
                    }
                    c.drawRenderNode(node);
                    if (imageAlpha != 255) {
                        c.restore();
                    }
                }
                effectNode.endRecording();

                c = effectNotchNode.beginRecording();
                c.scale(1f / gooScaleFactor, 1f / gooScaleFactor, 0, 0);
                if (notchInfo != null) {
                    c.translate(-left, -top);
                    c.translate(0, dp(BLACK_KING_BAR));
                    if (notchInfo.isLikelyCircle) {
                        float rad = Math.min(notchInfo.bounds.width(), notchInfo.bounds.height()) / 2f;
                        final float cy = notchInfo.bounds.bottom - notchInfo.bounds.width() / 2f;
                        c.drawCircle(notchInfo.bounds.centerX(), cy, rad, blackPaint);

                        if (h > 0f) {
                            path.rewind();
                            path.moveTo(notchInfo.bounds.centerX() - h / 2f, cy);
                            path.lineTo(notchInfo.bounds.centerX(), cy + rad + h);
                            path.lineTo(notchInfo.bounds.centerX() + h / 2f, cy);
                            path.close();
                            c.drawPath(path, blackPaint);
                        }
                    } else if (notchInfo.isAccurate) {
                        c.drawPath(notchInfo.path, blackPaint);
                    } else {
                        float rad = Math.max(notchInfo.bounds.width(), notchInfo.bounds.height()) / 2f;
                        temp.set(notchInfo.bounds);
                        c.drawRoundRect(temp, rad, rad, blackPaint);

                        if (h > 0f) {
                            path.rewind();
                            path.moveTo(temp.centerX() - h / 2f, temp.bottom);
                            path.lineTo(temp.centerX(), temp.bottom + h);
                            path.lineTo(temp.centerX() + h / 2f, temp.bottom);
                            path.close();
                            c.drawPath(path, blackPaint);
                        }
                    }
                } else {
                    c.drawRect(0, 0, width, dp(BLACK_KING_BAR), blackPaint);

                    if (h > 0f) {
                        path.rewind();
                        path.moveTo((width - h) / 2f, dp(BLACK_KING_BAR));
                        path.lineTo((width) / 2f, dp(BLACK_KING_BAR) + h);
                        path.lineTo((width + h) / 2f, dp(BLACK_KING_BAR));
                        path.close();
                        c.drawPath(path, blackPaint);
                    }
                }
                effectNotchNode.endRecording();
            }

            // Offset everything for black bar
            canvas.save();
            canvas.translate(left, top - dp(BLACK_KING_BAR));

            if (notchInfo != null) {
                canvas.clipRect(0, notchInfo.bounds.top - top + dp(BLACK_KING_BAR), width, height);
            }

            // Filter alpha + fade, then draw
            if (gooeyAlpha > 0) {
                if (gooeyAlpha != 255) {
                    canvas.saveLayerAlpha(wholeOptimized, gooeyAlpha);
                }
                canvas.saveLayer(wholeOptimized, filter);
                canvas.scale(gooScaleFactor, gooScaleFactor);
                canvas.drawRenderNode(effectNotchNode);
                canvas.drawRenderNode(effectNode);
                canvas.restore();
                if (gooeyAlpha != 255) {
                    canvas.restore();
                }
            }

            // Fade, draw blurred
            final int blurImageAlpha = MathUtils.clamp(rectangularContent ? imageAlphaNoClamp : imageAlphaNoClamp * 3 / 4, 0, 255);
            if (blurImageAlpha < 255) {
                canvas.saveLayer(wholeOptimized, null);
                if (blurIntensity != 0) {
                    canvas.saveLayer(wholeOptimized, filter);
                    canvas.scale(blurScaleFactor, blurScaleFactor);
                    canvas.drawRenderNode(blurNode);
                    canvas.restore();
                } else {
                    canvas.drawRenderNode(node);
                }
                canvas.drawRect(wholeOptimized, blackNodePaint);
                canvas.restore();
            }

            if (blurImageAlpha > 0) {
                if (blurImageAlpha != 255) {
                    canvas.saveLayerAlpha(wholeOptimized, blurImageAlpha);
                }
                if (blurIntensity != 0) {
                    canvas.saveLayer(wholeOptimized, filter);
                    canvas.scale(blurScaleFactor, blurScaleFactor);
                    canvas.drawRenderNode(blurNode);
                    canvas.restore();
                } else {
                    canvas.drawRenderNode(node);
                }
                if (blurImageAlpha != 255) {
                    canvas.restore();
                }
            }

            canvas.restore();
        }
    }

    private interface Impl {
        default void setIntensity(float intensity) {}
        default void setBlurIntensity(float intensity) {}
        default void onSizeChanged(int w, int h) {}
        default void release() {}
        void draw(Drawer drawer, Canvas canvas);
    }

    private interface Drawer {
        void draw(Canvas canvas);
    }
}
