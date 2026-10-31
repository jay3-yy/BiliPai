package com.android.purebilibili.build;

import com.android.build.api.instrumentation.AsmClassVisitorFactory;
import com.android.build.api.instrumentation.ClassContext;
import com.android.build.api.instrumentation.ClassData;
import com.android.build.api.instrumentation.InstrumentationParameters;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** 弹窗布局回调晚于窗口移除时跳过窗口更新，保留位置参数计算与正常附着路径。 */
public abstract class ComposePopupWindowGuard
        implements AsmClassVisitorFactory<InstrumentationParameters.None> {
    private static final String TARGET = "androidx.compose.ui.window.PopupLayoutHelperImpl";
    private static final String UPDATE = "(Landroid/view/WindowManager;Landroid/view/View;"
            + "Landroid/view/ViewGroup$LayoutParams;)V";

    @Override
    public boolean isInstrumentable(ClassData classData) {
        return TARGET.equals(classData.getClassName());
    }

    @Override
    public ClassVisitor createClassVisitor(ClassContext classContext, ClassVisitor next) {
        return new ClassVisitor(Opcodes.ASM9, next) {
            private boolean updateFound;

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!"updateViewLayout".equals(name) || !UPDATE.equals(descriptor)) return method;
                if ((access & (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT)) != 0) {
                    throw new IllegalStateException("Popup window update entry changed: review attachment guard");
                }
                updateFound = true;
                return new MethodVisitor(Opcodes.ASM9, method) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        Label attached = new Label();
                        // 参数 2 为弹窗 View；未附着时不再交给 WindowManager 更新。
                        super.visitVarInsn(Opcodes.ALOAD, 2);
                        super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/view/View",
                                "isAttachedToWindow", "()Z", false);
                        super.visitJumpInsn(Opcodes.IFNE, attached);
                        super.visitInsn(Opcodes.RETURN);
                        super.visitLabel(attached);
                    }
                };
            }

            @Override
            public void visitEnd() {
                if (!updateFound) {
                    throw new IllegalStateException("Popup window helper changed: review attachment guard");
                }
                super.visitEnd();
            }
        };
    }
}
