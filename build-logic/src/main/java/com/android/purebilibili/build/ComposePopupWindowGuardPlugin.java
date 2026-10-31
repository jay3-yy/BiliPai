package com.android.purebilibili.build;

import com.android.build.api.instrumentation.FramesComputationMode;
import com.android.build.api.instrumentation.InstrumentationScope;
import com.android.build.api.variant.AndroidComponentsExtension;
import com.android.build.api.variant.Variant;
import kotlin.Unit;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

/** 为打包的弹窗窗口更新入口加入附着检查。 */
public class ComposePopupWindowGuardPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPlugins().withId("com.android.application", ignored -> registerGuard(project));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerGuard(Project project) {
        AndroidComponentsExtension components =
                project.getExtensions().getByType(AndroidComponentsExtension.class);
        components.onVariants(components.selector().all(), (Action<Variant>) variant -> {
            variant.getInstrumentation().transformClassesWith(
                    ComposePopupWindowGuard.class,
                    InstrumentationScope.ALL,
                    parameters -> Unit.INSTANCE);
            variant.getInstrumentation().setAsmFramesComputationMode(
                    FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS);
        });
    }
}
