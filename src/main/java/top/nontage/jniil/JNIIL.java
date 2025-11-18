package top.nontage.jniil;

import top.nontage.auth.library.annotation.Protect;

import java.io.File;
import java.lang.instrument.Instrumentation;

@Protect
public class JNIIL {

    private static final InjectionOutputConfig classOutput = new InjectionOutputConfig();
    private static final InjectionOutputConfig methodOutput = new InjectionOutputConfig();
    private static boolean storeOriginalByteCode = false;
    private static boolean bytecodeVerifying = true;
    private static Instrumentation instrumentation;

    public static void setInstrumentation(Instrumentation instrumentation) {
        JNIIL.instrumentation = instrumentation;
    }

    public static Instrumentation getInstrumentation() {
        return instrumentation;
    }

    public static boolean isBytecodeVerifying() {
        return bytecodeVerifying;
    }

    public static void setBytecodeVerifying(boolean bytecodeVerifying) {
        if (!bytecodeVerifying) {
            System.out.println("[JNIIL] Bytecode verifying is disabled. This may lead to runtime errors if the injected bytecode is invalid.");
        }
        JNIIL.bytecodeVerifying = bytecodeVerifying;
    }

    public static void enableClassOutput(File dir) {
        classOutput.enable(dir, "classes");
    }

    public static void enableMethodOutput(File dir) {
        methodOutput.enable(dir, "methods");
    }

    public static boolean isClassOutputEnabled() {
        return classOutput.enabled;
    }

    public static boolean isMethodOutputEnabled() {
        return methodOutput.enabled;
    }

    public static File getClassOutputDir() {
        return classOutput.dir;
    }

    public static File getMethodOutputDir() {
        return methodOutput.dir;
    }

    public static boolean isStoreOriginalByteCode() {
        return storeOriginalByteCode;
    }

    public static void setStoreOriginalByteCode(boolean storeOriginalByteCode) {
        JNIIL.storeOriginalByteCode = storeOriginalByteCode;
    }

    private static class InjectionOutputConfig {
        private boolean enabled = false;
        private File dir = new File(".");

        void enable(File directory, String label) {
            if (!directory.exists() && !directory.mkdirs()) {
                System.err.println("[JNIIL] Failed to create output directory for " + label + ": " + directory.getAbsolutePath());
                return;
            }
            this.dir = directory;
            this.enabled = true;
            System.out.println("[JNIIL] Output for " + label + " enabled: " + directory.getAbsolutePath());
        }
    }
}
