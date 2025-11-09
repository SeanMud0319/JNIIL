package top.nontage.jniil.error;

public class NoSuchLocalVariableError extends Error {

    private final int index;

    public NoSuchLocalVariableError(int index) {
        super("Local variable at index " + index + " not found");
        this.index = index;
    }

    public int getIndex() {
        return index;
    }
}
