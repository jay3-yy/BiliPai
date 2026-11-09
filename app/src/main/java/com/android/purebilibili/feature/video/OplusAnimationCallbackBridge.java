package com.android.purebilibili.feature.video;

import com.oplus.animation.OplusViewSeamless;
import java.util.function.BiConsumer;

/** Loaded by name only after the system callback type is confirmed present. */
public final class OplusAnimationCallbackBridge extends OplusViewSeamless.AnimationCallback {
    private final BiConsumer<String, Boolean> listener;

    private OplusAnimationCallbackBridge(BiConsumer<String, Boolean> listener) {
        this.listener = listener;
    }

    public static Object create(BiConsumer<String, Boolean> listener) {
        return new OplusAnimationCallbackBridge(listener);
    }

    @Override
    public void onAnimationStart(boolean isOpen) {
        listener.accept("start", isOpen);
    }

    @Override
    public void onAnimationEnd(boolean isOpen) {
        listener.accept("end", isOpen);
    }
}
