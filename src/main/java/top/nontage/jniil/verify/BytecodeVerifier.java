package top.nontage.jniil.verify;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.CheckClassAdapter;
import top.nontage.jniil.JNIIL;
import top.nontage.jniil.exception.BytecodeVerifyException;
import top.nontage.jniil.utils.InjectionUtil;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.instrument.ClassDefinition;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public class BytecodeVerifier {

    public static final Set<ClassLoader> VERIFIER_LOADERS =
            Collections.newSetFromMap(new WeakHashMap<>());

    public static class Result {
        private final boolean asmValid;
        private final boolean jvmValid;
        private final String details;

        public Result(boolean asmValid, boolean jvmValid, String details) {
            this.asmValid = asmValid;
            this.jvmValid = jvmValid;
            this.details = details;
        }

        public boolean isAsmValid() {
            return asmValid;
        }

        public boolean isJvmValid() {
            return jvmValid;
        }

        public String getDetails() {
            return details;
        }
    }

    public static boolean asmVerify(ClassLoader classLoader, byte[] classBytes, StringWriter output) {
        PrintWriter pw = new PrintWriter(output);
        ClassReader cr = new ClassReader(classBytes);
        CheckClassAdapter.verify(cr, classLoader, false, pw);
        pw.flush();
        return output.toString().isEmpty();
    }

    public static boolean jvmVerify(String className, byte[] oldBytes, byte[] newBytes) {
        try {
            ClassLoader tempLoader = new ClassLoader() {
            };
            VERIFIER_LOADERS.add(tempLoader);
            Class<?> oldClass = InjectionUtil.unsafeInjectClass(tempLoader, className, oldBytes);
            JNIIL.getInstrumentation().redefineClasses(new ClassDefinition(oldClass, newBytes));
            return true;
        } catch (Throwable e) {
            System.err.println("JVM verification threw unexpected exception: " + e);
            return false;
        }
    }

    public static Result verify(String className, byte[] originalBytecode, byte[] finalBytecode) throws BytecodeVerifyException {
        boolean asmValid = true;
        boolean jvmValid = true;
        StringWriter sw = new StringWriter();
        String jvmError = null;

        if (JNIIL.isAsmVerifyToggle()) {
            try {
                Class<?> clazz = InjectionUtil.findClassAcrossClassLoaders(className);
                asmValid = asmVerify(clazz.getClassLoader(), finalBytecode, sw);
                if (!asmValid) {
                    System.err.println("[BytecodeVerifier] ASM verification failed for " + className);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        if (JNIIL.isJvmVerifyToggle()) {
            jvmValid = jvmVerify(className, originalBytecode, finalBytecode);
            if (!jvmValid) {
                jvmError = "JVM verification failed (see stderr for details)";
            }
        }

        String details = sw.toString();
        if (!asmValid || !jvmValid) {
            details = (asmValid ? "" : "ASM: " + details + "\n") +
                    (jvmValid ? "" : "JVM: " + jvmError);
        }

        Result result = new Result(asmValid, jvmValid, details);

        if (JNIIL.isBytecodeVerifying() && (!asmValid || !jvmValid)) {
            throw new BytecodeVerifyException(result.getDetails());
        }

        return result;
    }

    public static Result verifyAll(String className, byte[] oldClassBytes, byte[] classBytes) {
        return verify(className, oldClassBytes, classBytes);
    }
}