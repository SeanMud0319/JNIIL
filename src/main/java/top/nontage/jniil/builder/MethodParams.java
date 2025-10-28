package top.nontage.jniil.builder;

import top.nontage.auth.library.annotation.Protect;

@Protect
public class MethodParams {

    private final int[] slots;

    private MethodParams(int[] slots) {
        this.slots = slots;
    }

    public static MethodParams range(int start, int end) {
        int[] s = new int[end - start + 1];
        for (int i = 0; i < s.length; i++) s[i] = start + i;
        return new MethodParams(s);
    }

    public static MethodParams of(int... indices) {
        return new MethodParams(indices);
    }

    public int getSlot(int index) {
        return slots[index];
    }

    public String build() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < slots.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append("$").append(slots[i]);
        }
        return sb.toString();
    }
}
