package com.android.purebilibili.build;

import com.android.build.api.instrumentation.AsmClassVisitorFactory;
import com.android.build.api.instrumentation.ClassContext;
import com.android.build.api.instrumentation.ClassData;
import com.android.build.api.instrumentation.InstrumentationParameters;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** 剪贴板描述读取被系统拒绝时，不向输入框提供粘贴操作。 */
public abstract class ComposeClipboardReadGuard
        implements AsmClassVisitorFactory<InstrumentationParameters.None> {
    private static final String TARGET = "androidx.compose.ui.platform.AndroidClipboardManager";

    @Override
    public boolean isInstrumentable(ClassData classData) {
        return TARGET.equals(classData.getClassName());
    }

    @Override
    public ClassVisitor createClassVisitor(ClassContext classContext, ClassVisitor next) {
        return guardClipboardQuery(next);
    }

    static ClassVisitor guardClipboardQuery(ClassVisitor next) {
        return new ClassVisitor(Opcodes.ASM9, next) {
            private boolean queryFound;

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!"hasText".equals(name) || !"()Z".equals(descriptor)) return method;
                if ((access & (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
                    throw new IllegalStateException("Clipboard query entry changed: review permission boundary");
                }
                queryFound = true;
                return new MethodVisitor(Opcodes.ASM9, method) {
                    private final Label start = new Label();
                    private final Label end = new Label();
                    private final Label denied = new Label();

                    @Override
                    public void visitCode() {
                        super.visitCode();
                        super.visitTryCatchBlock(start, end, denied, "java/lang/SecurityException");
                        super.visitLabel(start);
                    }

                    @Override
                    public void visitMaxs(int maxStack, int maxLocals) {
                        super.visitLabel(end);
                        super.visitLabel(denied);
                        super.visitInsn(Opcodes.POP);
                        super.visitInsn(Opcodes.ICONST_0);
                        super.visitInsn(Opcodes.IRETURN);
                        super.visitMaxs(Math.max(maxStack, 1), maxLocals);
                    }
                };
            }

            @Override
            public void visitEnd() {
                if (!queryFound) {
                    throw new IllegalStateException("Clipboard query missing: review permission boundary");
                }
                super.visitEnd();
            }
        };
    }
}
