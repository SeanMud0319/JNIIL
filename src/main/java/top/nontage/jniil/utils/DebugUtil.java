package top.nontage.jniil.utils;

public class DebugUtil {

    public static int getJavaVersion() {
        String spec = System.getProperty("java.specification.version");
        if (spec == null || spec.isEmpty()) {
            spec = System.getProperty("java.version");
        }
        if (spec.startsWith("1.")) {
            spec = spec.substring(2);
        }
        int dot = spec.indexOf('.');
        if (dot != -1) {
            spec = spec.substring(0, dot);
        }
        int dash = spec.indexOf('-');
        if (dash != -1) {
            spec = spec.substring(0, dash);
        }
        return Integer.parseInt(spec);
    }
}
