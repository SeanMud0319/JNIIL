package top.nontage.jniil.verify;

import me.fan87.nativeinstrumentation.NativeInstrumentation;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.CheckClassAdapter;
import top.nontage.auth.library.annotation.Protect;
import top.nontage.jniil.utils.InjectionUtil;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.instrument.ClassDefinition;

@Protect
public class BytecodeVerifier {
    private static final NativeInstrumentation inst = new NativeInstrumentation();

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
        } else {
            System.err.println("ASM found issues:\n" + details);
        }

        if (jvmValid) {
            System.out.println("JVM verification passed");
        } else {
            System.err.println("JVM verification not implemented or failed");
        }

        return new Result(asmValid, jvmValid, details);
    }
}
