package dev.tmpfs.jvmplant.impl;


import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.PrintWriter;
import java.io.StringWriter;

public class ClassFileVerifier {

    private ClassFileVerifier() {
    }

    public static void verifyClassFile(byte[] bytecode) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        CheckClassAdapter.verify(new ClassReader(bytecode), false, printWriter);
        String verificationResult = stringWriter.toString();
        if (!verificationResult.isEmpty()) {
            throw new IllegalArgumentException("Class file verification failed: " + verificationResult);
        }
    }

}
