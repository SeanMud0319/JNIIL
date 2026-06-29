package top.nontage.jniil;

import java.io.File;
import java.lang.instrument.Instrumentation;

public class JNIIL {

    private static Instrumentation instrumentation;
    private static final InjectionOutputConfig classOutput = new InjectionOutputConfig();
    private static final InjectionOutputConfig methodOutput = new InjectionOutputConfig();

    private static boolean storeOriginalByteCode = false;
    private static boolean bytecodeVerifyWarning = true;
    private static boolean jvmVerifyToggle = false;
    private static boolean asmVerifyToggle = true;

    public static void setInstrumentation(Instrumentation inst) {
        instrumentation = inst;
    }

    public static Instrumentation getInstrumentation() {
        if (instrumentation != null) return instrumentation;
        throw new IllegalStateException("Instrumentation not initialized. Call JNIILBootstrap.install() first.");
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

    public static void setBytecodeVerifying(boolean flag) {
        if (!flag && bytecodeVerifyWarning) {
            System.out.println("[JNIIL] Bytecode verifying disabled. Invalid bytecode may cause runtime errors.");
        }
        jvmVerifyToggle = flag;
        asmVerifyToggle = flag;
    }

    public static boolean isBytecodeVerifying() {
        return isAsmVerifyToggle() || isJvmVerifyToggle();
    }

    public static void setBytecodeVerifyWarning(boolean enabled) {
        bytecodeVerifyWarning = enabled;
    }

    public static boolean isBytecodeVerifyWarning() {
        return bytecodeVerifyWarning;
    }

    public static void setJvmVerifyToggle(boolean enabled) {
        jvmVerifyToggle = enabled;
    }

    public static boolean isJvmVerifyToggle() {
        return jvmVerifyToggle;
    }

    public static void setAsmVerifyToggle(boolean enabled) {
        asmVerifyToggle = enabled;
    }

    public static boolean isAsmVerifyToggle() {
        return asmVerifyToggle;
    }

    public static void setStoreOriginalByteCode(boolean enabled) {
        storeOriginalByteCode = enabled;
    }

    public static boolean isStoreOriginalByteCode() {
        return storeOriginalByteCode;
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