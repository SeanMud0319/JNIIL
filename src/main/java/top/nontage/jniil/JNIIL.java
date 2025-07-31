package top.nontage.jniil;

import java.io.File;

public class JNIIL {

    private static final InjectionOutputConfig classOutput = new InjectionOutputConfig();
    private static final InjectionOutputConfig methodOutput = new InjectionOutputConfig();
    private static boolean storeOriginalByteCode = false;

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
