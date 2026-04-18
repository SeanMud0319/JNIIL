package top.nontage.jniil.injector.functional;

public class MethodInfo {
    private final Object target;
    private final Object[] arguments;
    private Object returnValue;
    private boolean cancelled;

    public MethodInfo(Object target, Object[] arguments) {
        this.target = target;
        this.arguments = arguments;
        this.cancelled = false;
    }

    public Object[] getArguments() {
        return arguments;
    }

    @SuppressWarnings("unchecked")
    public <T> T getArgument(int index) {
        return (T) arguments[index];
    }

    @SuppressWarnings("unchecked")
    public <T> T getTarget() {
        return (T) target;
    }

    public Object getReturnValue() {
        return returnValue;
    }

    public void setReturnValue(Object obj) {
        this.returnValue = obj;
    }

    public void cancel() {
        this.cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }

}
