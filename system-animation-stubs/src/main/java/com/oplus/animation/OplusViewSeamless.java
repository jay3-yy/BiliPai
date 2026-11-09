package com.oplus.animation;

/** Compile-only callback declarations. Runtime classes must come from the system. */
public final class OplusViewSeamless {
    private OplusViewSeamless() {}

    public static class AnimationCallback {
        public AnimationCallback() {
            throw new UnsupportedOperationException("System implementation required");
        }

        public void onAnimationStart(boolean isOpen) {
            throw new UnsupportedOperationException("System implementation required");
        }

        public void onAnimationEnd(boolean isOpen) {
            throw new UnsupportedOperationException("System implementation required");
        }
    }
}
