package top.nontage.jniil.builder;

public class ExprUtil {
    private ExprUtil() {
    }

    public static String trimEndingSemicolon(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == ';') {
            end--;
        }
        return s.substring(0, end) + ";";
    }
}
