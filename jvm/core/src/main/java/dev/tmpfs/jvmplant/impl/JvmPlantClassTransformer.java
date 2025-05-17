package dev.tmpfs.jvmplant.impl;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.*;

public class JvmPlantClassTransformer {

    private JvmPlantClassTransformer() {
    }

    private static class HookRewriterClassVisitor extends ClassVisitor {

        private HookTargetInfo mHookTargetInfo;

        protected HookRewriterClassVisitor(int api, ClassVisitor classVisitor, HookTargetInfo hookTargetInfo) {
            super(api, classVisitor);
            mHookTargetInfo = hookTargetInfo;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);
            mHookTargetInfo.targetClassType = name;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            if (name.equals(mHookTargetInfo.methodName) && descriptor.equals(mHookTargetInfo.methodDescriptor)) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                mHookTargetInfo.isStatic = ((access & Opcodes.ACC_STATIC) != 0);
                mHookTargetInfo.targetMethodParamTypes = Type.getArgumentTypes(descriptor);
                mHookTargetInfo.targetMethodParamTypeShorties = new char[mHookTargetInfo.targetMethodParamTypes.length];
                for (int i = 0; i < mHookTargetInfo.targetMethodParamTypes.length; i++) {
                    mHookTargetInfo.targetMethodParamTypeShorties[i] = mHookTargetInfo.targetMethodParamTypes[i].getDescriptor().charAt(0);
                    if (mHookTargetInfo.targetMethodParamTypeShorties[i] == '[') {
                        mHookTargetInfo.targetMethodParamTypeShorties[i] = 'L';
                    }
                }
                mHookTargetInfo.targetMethodReturnType = Type.getReturnType(descriptor);
                mHookTargetInfo.targetMethodReturnTypeShorty = mHookTargetInfo.targetMethodReturnType.getDescriptor().charAt(0);
                if (mHookTargetInfo.targetMethodReturnTypeShorty == '[') {
                    mHookTargetInfo.targetMethodReturnTypeShorty = 'L';
                }
                return new HookRewriterMethodVisitor(api, mv, mHookTargetInfo);
            } else {
                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }
        }
    }

    private static class HookRewriterMethodVisitor extends MethodVisitor {

        private final HookTargetInfo mHookTargetInfo;

        protected HookRewriterMethodVisitor(int api, MethodVisitor methodVisitor, HookTargetInfo hookTargetInfo) {
            super(api, methodVisitor);
            mHookTargetInfo = hookTargetInfo;
        }

        @Override
        public void visitParameter(String name, int access) {
            super.visitParameter(name, access);
        }

        @Override
        public void visitCode() {
            super.visitCode();
            visitHookedMethodPrologue(mv, mHookTargetInfo);
        }

    }

    private static String primitiveShortyToWrapperType(char shorty) {
        switch (shorty) {
            case 'Z':
                return "java/lang/Boolean";
            case 'B':
                return "java/lang/Byte";
            case 'C':
                return "java/lang/Character";
            case 'S':
                return "java/lang/Short";
            case 'I':
                return "java/lang/Integer";
            case 'J':
                return "java/lang/Long";
            case 'F':
                return "java/lang/Float";
            case 'D':
                return "java/lang/Double";
            default:
                throw new IllegalArgumentException("Invalid primitive shorty: " + shorty);
        }
    }

    private static int primitiveLoadOpcode(char shorty) {
        switch (shorty) {
            case 'Z':
                return Opcodes.ILOAD;
            case 'B':
                return Opcodes.ILOAD;
            case 'C':
                return Opcodes.ILOAD;
            case 'S':
                return Opcodes.ILOAD;
            case 'I':
                return Opcodes.ILOAD;
            case 'J':
                return Opcodes.LLOAD;
            case 'F':
                return Opcodes.FLOAD;
            case 'D':
                return Opcodes.DLOAD;
            default:
                throw new IllegalArgumentException("Invalid primitive shorty: " + shorty);
        }
    }

    private static int primitiveReturnOpcode(char shorty) {
        switch (shorty) {
            case 'Z':
                return Opcodes.IRETURN;
            case 'B':
                return Opcodes.IRETURN;
            case 'C':
                return Opcodes.IRETURN;
            case 'S':
                return Opcodes.IRETURN;
            case 'I':
                return Opcodes.IRETURN;
            case 'J':
                return Opcodes.LRETURN;
            case 'F':
                return Opcodes.FRETURN;
            case 'D':
                return Opcodes.DRETURN;
            case 'V':
                return Opcodes.RETURN;
            default:
                throw new IllegalArgumentException("Invalid primitive shorty: " + shorty);
        }
    }

    private static String primitiveUnboxMethodName(char shorty) {
        switch (shorty) {
            case 'Z':
                return "booleanValue";
            case 'B':
                return "byteValue";
            case 'C':
                return "charValue";
            case 'S':
                return "shortValue";
            case 'I':
                return "intValue";
            case 'J':
                return "longValue";
            case 'F':
                return "floatValue";
            case 'D':
                return "doubleValue";
            default:
                throw new IllegalArgumentException("Invalid primitive shorty: " + shorty);
        }
    }

    private static void copyArgvToObjectArray(
            @NotNull MethodVisitor mv,
            int localIndex,
            int arrayIndex,
            char localShorty
    ) {
        // assume the target array is already on the top of the stack
        boolean isPrimitive = localShorty != 'L' && localShorty != '[';
        // dup
        mv.visitInsn(Opcodes.DUP);
        // iconst_{arrayIndex}
        mv.visitIntInsn(Opcodes.BIPUSH, arrayIndex);
        if (isPrimitive) {
            // primitive type, need to box
            // [x]laod_{localIndex}
            // invokestatic java.lang.[Wrapper].valueOf([X])
            String wrapperType = primitiveShortyToWrapperType(localShorty);
            int loadOpcode = primitiveLoadOpcode(localShorty);
            mv.visitVarInsn(loadOpcode, localIndex);
            mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    wrapperType,
                    "valueOf",
                    "(" + localShorty + ")L" + wrapperType + ";",
                    false
            );
        } else {
            // aload_{localIndex}
            mv.visitVarInsn(Opcodes.ALOAD, localIndex);
        }
        // aastore
        mv.visitInsn(Opcodes.AASTORE);
    }

    private static String descriptorToTypeName(String descriptor) {
        if (descriptor.startsWith("L") && descriptor.endsWith(";")) {
            return descriptor.substring(1, descriptor.length() - 1);
        } else {
            return descriptor;
        }
    }

    private static void visitHookedMethodPrologue(
            @NotNull MethodVisitor mv,
            @NotNull HookTargetInfo hookTargetInfo
    ) {
        // Object[] args = isStatic ? new Object[] {hookId, var1, var2, ...} : new Object[] {hookId, this, var1, var2, ...};
        // Object[] result = (Object[]) JvmPlantGlobalStaticFields.sEntry.invoke(null, new Object[] {args});
        // if (result != null) {
        //     return result[0];
        // }
        boolean isStatic = hookTargetInfo.isStatic;
        int arrayLength = hookTargetInfo.targetMethodParamTypes.length + (isStatic ? 1 : 2);
        Label prologueLabel = new Label();
        mv.visitLabel(prologueLabel);
        mv.visitLineNumber(1, prologueLabel);
        // create the args array
        mv.visitIntInsn(Opcodes.BIPUSH, arrayLength);
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
        // args[0] = hookId
        // dup
        mv.visitInsn(Opcodes.DUP);
        // iconst_0
        mv.visitIntInsn(Opcodes.BIPUSH, 0);
        // ldc hookId
        mv.visitLdcInsn(hookTargetInfo.hookId);
        // box the long
        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "java/lang/Long",
                "valueOf",
                "(J)Ljava/lang/Long;",
                false
        );
        // aastore
        mv.visitInsn(Opcodes.AASTORE);
        // then, copy the args to the array, including "this" if not static
        if (!isStatic) {
            copyArgvToObjectArray(mv, 0, 1, 'L');
        }
        int localVarStart;
        {
            int localIndex = isStatic ? 0 : 1;
            // copy the args
            for (int i = 0; i < hookTargetInfo.targetMethodParamTypes.length; i++) {
                char shorty = hookTargetInfo.targetMethodParamTypeShorties[i];
                copyArgvToObjectArray(mv, localIndex, i + (isStatic ? 1 : 2), shorty);
                localIndex += (shorty == 'J' || shorty == 'D') ? 2 : 1;
            }
            localVarStart = localIndex;
        }
        // astore {localVarStart}
        mv.visitVarInsn(Opcodes.ASTORE, localVarStart);
        // aload {localVarStart}
        mv.visitVarInsn(Opcodes.ALOAD, localVarStart);
        // INVOKESTATIC GlobalStaticFields.entry ([Ljava/lang/Object;)[Ljava/lang/Object;
        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                hookTargetInfo.globalStaticFieldsClassType,
                "entry",
                "([Ljava/lang/Object;)[Ljava/lang/Object;",
                false
        );
        // astore {localVarStart+1}
        mv.visitVarInsn(Opcodes.ASTORE, localVarStart + 1);
        // aload {localVarStart+1}
        mv.visitVarInsn(Opcodes.ALOAD, localVarStart + 1);
        Label originCode = new Label();
        // if (result == null) goto origin;
        mv.visitJumpInsn(Opcodes.IFNULL, originCode);
        char returnTypeShorty = hookTargetInfo.targetMethodReturnTypeShorty;
        boolean isReturnVoid = returnTypeShorty == 'V';
        boolean isReturnPrimitive = returnTypeShorty != 'L' && returnTypeShorty != '[';
        if (isReturnVoid) {
            // return;
            mv.visitInsn(Opcodes.RETURN);
        } else {
            // aload {localVarStart+1}
            mv.visitVarInsn(Opcodes.ALOAD, localVarStart + 1);
            // iconst_0
            mv.visitIntInsn(Opcodes.BIPUSH, 0);
            // aaload
            mv.visitInsn(Opcodes.AALOAD);
            if (isReturnPrimitive) {
                // checkcast box{returnType}
                String wrapperType = primitiveShortyToWrapperType(returnTypeShorty);
                mv.visitTypeInsn(Opcodes.CHECKCAST, wrapperType);
                // invokevirtual box{returnType}.unbox()
                mv.visitMethodInsn(
                        Opcodes.INVOKEVIRTUAL,
                        wrapperType,
                        primitiveUnboxMethodName(returnTypeShorty),
                        "()" + returnTypeShorty,
                        false
                );
                // return {returnType}
                mv.visitInsn(primitiveReturnOpcode(returnTypeShorty));
            } else {
                // checkcast {returnType}
                mv.visitTypeInsn(Opcodes.CHECKCAST,
                        hookTargetInfo.targetMethodReturnType.getInternalName());
                // areturn {returnType}
                mv.visitInsn(Opcodes.ARETURN);
            }
        }
        // origins:
        mv.visitLabel(originCode);
    }

    public static class HookTargetInfo {
        public String methodName;
        public String methodDescriptor;
        public long hookId;
        public String globalStaticFieldsClassType;
        private boolean isStatic;
        private String targetClassType;
        private Type[] targetMethodParamTypes;
        // not including the return type, e.g. "I" for int
        private char[] targetMethodParamTypeShorties;
        private Type targetMethodReturnType;
        private char targetMethodReturnTypeShorty;
    }

    public static byte @NotNull [] installHookPrologueToClass(
            byte @NotNull [] originalClassFile,
            @NotNull HookTargetInfo hookTargetInfo
    ) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        ClassVisitor classVisitor = new HookRewriterClassVisitor(
                Opcodes.ASM9,
                cw,
                hookTargetInfo
        );
        ClassReader classReader = new ClassReader(originalClassFile);
        classReader.accept(classVisitor, 0);
        return cw.toByteArray();
    }

}
