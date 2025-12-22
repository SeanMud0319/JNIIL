package top.nontage.jniil.verify;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.CheckClassAdapter;
import top.nontage.auth.library.annotation.Protect;
import top.nontage.jniil.JNIIL;
import top.nontage.jniil.utils.InjectionUtil;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

@Protect
public class BytecodeVerifier {
    private static final Instrumentation inst = JNIIL.getInstrumentation();

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

    public static boolean asmVerify(byte[] classBytes, StringWriter output) {
        PrintWriter pw = new PrintWriter(output);
        ClassReader cr = new ClassReader(classBytes);
        CheckClassAdapter.verify(cr, false, pw);
        pw.flush();
        return output.toString().isEmpty();
    }

    public static boolean jvmVerify(String className, byte[] oldBytes, byte[] newBytes) {
        try {
            ClassLoader tempLoader = new ClassLoader() {
            };
            VERIFIER_LOADERS.add(tempLoader);
            Class<?> oldClass = InjectionUtil.unsafeInjectClass(tempLoader, className, oldBytes);
            inst.redefineClasses(new ClassDefinition(oldClass, newBytes));
            return true;
        } catch (Throwable e) {
            System.err.println("JVM verification threw unexpected exception: " + e);
            return false;
        }
    }

    public static Result verifyAll(String className, byte[] oldClassBytes, byte[] classBytes) {
        StringWriter sw = new StringWriter();
        boolean asmValid = asmVerify(classBytes, sw);
        boolean jvmValid = jvmVerify(className, oldClassBytes, classBytes);
        String details = sw.toString();
        if (asmValid) {
            System.out.println("ASM structure check passed");
        }

        if (jvmValid) {
            System.out.println("JVM verification passed");
        }
        return new Result(asmValid, jvmValid, details);
    }
}
