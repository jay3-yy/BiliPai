package com.android.purebilibili.build;

import com.android.build.api.instrumentation.AsmClassVisitorFactory;
import com.android.build.api.instrumentation.ClassContext;
import com.android.build.api.instrumentation.ClassData;
import com.android.build.api.instrumentation.InstrumentationParameters;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Parent disposal cascades to child dispatchers before
 * nested/movable Compose content runs its own onDispose or pending enabled-state effect.
 * Make terminal cleanup, enabled-state writes, and deferred Compose BackHandler registration
 * inert once that branch is disposed.
 * Keep direct dispatcher registration, dispatch, and every other live operation strict.
 * Remove once upstream owner cleanup and pending effects tolerate ancestor disposal.
 */
public abstract class NavigationEventDisposalGuard
        implements AsmClassVisitorFactory<InstrumentationParameters.None> {
    private static final String TARGET = "androidx.navigationevent.NavigationEventDispatcher";
    private static final String OWNER = TARGET.replace('.', '/');
    private static final String COMPAT = "androidx.activity.compose.internal.BackHandlerDispatcherCompat";
    private static final String DISPOSED_ACCESSOR = "bilipaiIsDisposed";

    @Override
    public boolean isInstrumentable(ClassData classData) {
        return TARGET.equals(classData.getClassName()) || COMPAT.equals(classData.getClassName());
    }

    @Override
    public ClassVisitor createClassVisitor(ClassContext classContext, ClassVisitor next) {
        if (COMPAT.equals(classContext.getCurrentClassData().getClassName())) {
            return backHandlerVisitor(next);
        }
        return new ClassVisitor(Opcodes.ASM9, next) {
            private boolean disposedAccessorFound;
            private int guardedMethods;
            private int invariantCalls;

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                if ("isDisposed".equals(name) && "()Z".equals(descriptor)
                        && (access & Opcodes.ACC_PRIVATE) != 0) {
                    disposedAccessorFound = true;
                }
                boolean disposal = "dispose".equals(name) && "()V".equals(descriptor);
                boolean enabledWrite = "setEnabled".equals(name) && "(Z)V".equals(descriptor);
                if (!disposal && !enabledWrite) return method;
                if ((access & Opcodes.ACC_STATIC) != 0) {
                    throw new IllegalStateException("NavigationEvent lifecycle methods changed: review disposal patch");
                }
                guardedMethods++;
                return new MethodVisitor(Opcodes.ASM9, method) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        Label live = new Label();
                        super.visitVarInsn(Opcodes.ALOAD, 0);
                        // Use the hierarchical accessor, not the local field: an ancestor may
                        // already be disposing while this descendant waits in its cleanup queue.
                        super.visitMethodInsn(Opcodes.INVOKESPECIAL, OWNER, "isDisposed", "()Z", false);
                        super.visitJumpInsn(Opcodes.IFEQ, live);
                        super.visitInsn(Opcodes.RETURN);
                        super.visitLabel(live);
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name,
                            String descriptor, boolean isInterface) {
                        if (OWNER.equals(owner) && "checkInvariants".equals(name)
                                && "()V".equals(descriptor)) {
                            invariantCalls++;
                        }
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                    }
                };
            }

            @Override
            public void visitEnd() {
                if (!disposedAccessorFound || guardedMethods != 2 || invariantCalls != 2) {
                    throw new IllegalStateException("NavigationEvent internals changed: review lifecycle disposal patch");
                }
                MethodVisitor accessor = super.visitMethod(
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC,
                        DISPOSED_ACCESSOR, "()Z", null, null);
                accessor.visitCode();
                accessor.visitVarInsn(Opcodes.ALOAD, 0);
                accessor.visitMethodInsn(Opcodes.INVOKESPECIAL, OWNER, "isDisposed", "()Z", false);
                accessor.visitInsn(Opcodes.IRETURN);
                accessor.visitMaxs(1, 1);
                accessor.visitEnd();
                super.visitEnd();
            }
        };
    }

    /** A deferred Compose registration into a removed page is terminal, never a live handler. */
    private static ClassVisitor backHandlerVisitor(ClassVisitor next) {
        return new ClassVisitor(Opcodes.ASM9, next) {
            private int guardedMethods;
            private boolean dispatcherFieldFound;

            @Override
            public org.objectweb.asm.FieldVisitor visitField(int access, String name, String descriptor,
                    String signature, Object value) {
                if ("navigationEventDispatcher".equals(name) && ("L" + OWNER + ";").equals(descriptor)) {
                    dispatcherFieldFound = true;
                }
                return super.visitField(access, name, descriptor, signature, value);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!"addHandler".equals(name) ||
                        !"(Landroidx/activity/compose/internal/BackHandlerCompat;)V".equals(descriptor)) {
                    return method;
                }
                guardedMethods++;
                return new MethodVisitor(Opcodes.ASM9, method) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        Label live = new Label();
                        // Keep the legacy dispatcher branch and every live registration intact.
                        super.visitVarInsn(Opcodes.ALOAD, 0);
                        super.visitFieldInsn(Opcodes.GETFIELD, COMPAT.replace('.', '/'),
                                "navigationEventDispatcher", "L" + OWNER + ";");
                        super.visitJumpInsn(Opcodes.IFNULL, live);
                        super.visitVarInsn(Opcodes.ALOAD, 0);
                        super.visitFieldInsn(Opcodes.GETFIELD, COMPAT.replace('.', '/'),
                                "navigationEventDispatcher", "L" + OWNER + ";");
                        super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, OWNER, DISPOSED_ACCESSOR, "()Z", false);
                        super.visitJumpInsn(Opcodes.IFEQ, live);
                        super.visitInsn(Opcodes.RETURN);
                        super.visitLabel(live);
                    }
                };
            }

            @Override
            public void visitEnd() {
                if (!dispatcherFieldFound || guardedMethods != 1) {
                    throw new IllegalStateException("BackHandler compatibility internals changed: review disposal patch");
                }
                super.visitEnd();
            }
        };
    }
}
