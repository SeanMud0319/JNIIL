package top.nontage.jniil.exception;

public class NoSuchLocalVariableException extends RuntimeException {

    private final int index;

    public NoSuchLocalVariableException(int index) {
        super("Local variable at index " + index + " not found");
        this.index = index;
    }

    public int getIndex() {
        return index;
    }
}
