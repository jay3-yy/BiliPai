#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

MODE="${1:-}"
if [[ $# -gt 0 ]]; then
  shift
fi

# Each workflow job owns one group; task-local filters keep module boundaries explicit.
case "$MODE" in
  performance)
    GRADLE_TASKS=(
      :ci-lint:test
        --tests 'com.android.purebilibili.lint.PerformanceDetectorTest'
        --tests 'com.android.purebilibili.lint.ModelStabilityDetectorTest'
      :app:lintDebug -Pbili.ciChecks=performance
      :app:testDebugUnitTest
        --tests 'com.android.purebilibili.core.ui.performance.AppDisplayModePreferenceTest'
        --tests 'com.android.purebilibili.core.ui.performance.RuntimeVisualGuardTrackerTest'
      :design-system:testDebugUnitTest
        --tests 'com.android.purebilibili.core.ui.adaptive.RuntimeVisualGuardPolicyTest'
        --tests 'com.android.purebilibili.core.ui.adaptive.RuntimeVisualGuardSignalPolicyTest'
    )
    ;;
  correctness)
    GRADLE_TASKS=(
      :app:testDebugUnitTest
        --tests 'com.android.purebilibili.feature.settings.AppUpdateCheckerTest'
        --tests 'com.android.purebilibili.core.store.SerializedSettingsCacheWriterTest'
        --tests 'com.android.purebilibili.core.store.AppNavigationSettingsMappingPolicyTest.gestureRotationAndTranslationPreferencesAreIndependent'
        --tests 'com.android.purebilibili.core.store.AppNavigationSettingsMappingPolicyTest.populatedPreferences_mapToNavigationSettingsCorrectly'
        --tests 'com.android.purebilibili.core.store.ThemeSelectionMigrationPolicyTest'
    )
    ;;
  style)
    GRADLE_TASKS=(
      :ci-lint:test
        --tests 'com.android.purebilibili.lint.StyleDetectorTest'
      :app:lintDebug -Pbili.ciChecks=style
    )
    ;;
  *)
    echo "Usage: bash scripts/ci_quality.sh {performance|correctness|style} [Gradle options...]" >&2
    exit 2
    ;;
esac

# Keep independent reports without hiding compilation, checker or behavior failures.
bash ./gradlew "${GRADLE_TASKS[@]}" --continue --no-daemon --console=plain "$@"
