// Copyright 2026, BiliPai contributors
// SPDX-License-Identifier: Apache-2.0
package top.yukonga.miuix.kmp.nav.core

/** Per-session decision after the platform completes a predictive back gesture. */
interface NavBackCompletionPolicy {
    fun shouldCommit(progress: Float, peakProgress: Float, velocity: Float): Boolean
}
