package dev.tmpfs.jvmplant.impl;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class TheGlobalStaticFieldsGenerator {

    private TheGlobalStaticFieldsGenerator() {
        throw new AssertionError("No instance for you!");
    }

    public static byte[] generateBytecode(String className) {
        // public abstract class JvmPlantGlobalStaticFields {
        //   private JvmPlantGlobalStaticFields () { }
        //   public static java.lang.reflect.Method sEntry; // set later
        //   public static Object[] entry(Object[] args) throws Throwable {
        //     try {
        //       return (Object[]) sEntry.invoke(null, new Object[]{args});
        //     } catch (InvocationTargetException e) {
        //       throw e.getTargetException();
        //     }
        //   }
        // }
        className = className.replace('.', '/');
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(
                Opcodes.V1_7,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
                className,
                null,
                "java/lang/Object",
                null
        );
        cw.visitSource(null, null);
        cw.visitField(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "sEntry",
                "Ljava/lang/reflect/Method;",
                null,
                null
        ).visitEnd();
        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "entry",
                "([Ljava/lang/Object;)[Ljava/lang/Object;",
                null,
                new String[]{"java/lang/Throwable"}
        );
        mv.visitCode();
        //  public static entry([Ljava/lang/Object;)[Ljava/lang/Object; throws java/lang/Throwable
        Label l0 = new Label();
        Label l1 = new Label();
        Label l2 = new Label();
        Label l3 = new Label();
        //    TRYCATCHBLOCK L0 L1 L2 java/lang/reflect/InvocationTargetException
        mv.visitTryCatchBlock(l0, l1, l2, "java/lang/reflect/InvocationTargetException");
        //   L0
        mv.visitLabel(l0);
        mv.visitLineNumber(1, l0);
        //    GETSTATIC dev/tmpfs/jvmplant/test/DTMMain.sEntry : Ljava/lang/reflect/Method;
        mv.visitFieldInsn(Opcodes.GETSTATIC, className, "sEntry", "Ljava/lang/reflect/Method;");
        //    ACONST_NULL
        mv.visitInsn(Opcodes.ACONST_NULL);
        //    ICONST_1
        mv.visitInsn(Opcodes.ICONST_1);
        //    ANEWARRAY java/lang/Object
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
        //    DUP
        mv.visitInsn(Opcodes.DUP);
        //    ICONST_0
        mv.visitInsn(Opcodes.ICONST_0);
        //    ALOAD 0
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        //    AASTORE
        mv.visitInsn(Opcodes.AASTORE);
        //    INVOKEVIRTUAL java/lang/reflect/Method.invoke (Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;
        mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/reflect/Method",
                "invoke",
                "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;",
                false
        );
        //    CHECKCAST [Ljava/lang/Object;
        mv.visitTypeInsn(Opcodes.CHECKCAST, "[Ljava/lang/Object;");
        //   L1
        mv.visitLabel(l1);
        //    ARETURN
        mv.visitInsn(Opcodes.ARETURN);
        //   L2
        mv.visitLabel(l2);
        //    ASTORE 1
        mv.visitVarInsn(Opcodes.ASTORE, 1);
        //   L3
        mv.visitLabel(l3);
        //    ALOAD 1
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        //    INVOKEVIRTUAL java/lang/reflect/InvocationTargetException.getTargetException ()Ljava/lang/Throwable;
        mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/reflect/InvocationTargetException",
                "getTargetException",
                "()Ljava/lang/Throwable;",
                false
        );
        //    ATHROW
        mv.visitInsn(Opcodes.ATHROW);
        //  L4
        //    LOCALVARIABLE e Ljava/lang/reflect/InvocationTargetException; L3 L4 1
        //    LOCALVARIABLE args [Ljava/lang/Object; L0 L4 0
        //    MAXSTACK = 6
        //    MAXLOCALS = 2
        mv.visitMaxs(6, 2);
        mv.visitEnd();
        // private constructor
        MethodVisitor mv2 = cw.visitMethod(
                Opcodes.ACC_PRIVATE,
                "<init>",
                "()V",
                null,
                null
        );
        mv2.visitCode();
        mv2.visitVarInsn(Opcodes.ALOAD, 0);
        mv2.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "java/lang/Object",
                "<init>",
                "()V",
                false
        );
        mv2.visitInsn(Opcodes.RETURN);
        mv2.visitMaxs(1, 1);
        mv2.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

}
