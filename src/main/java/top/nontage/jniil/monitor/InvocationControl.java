package top.nontage.jniil.monitor;

public final class InvocationControl {
    private boolean cancelled = false;
    private Object overrideReturnValue = null;
    private boolean returnValueSet = false;

    public boolean isCancelled() {
        return cancelled;
    }

    public void cancel() {
        this.cancelled = true;
    }

    public void setReturnValue(Object value) {
        this.overrideReturnValue = value;
        this.returnValueSet = true;
        cancel();
    }

    public Object getOverrideReturnValue() {
        return overrideReturnValue;
    }

    public boolean isReturnValueSet() {
        return returnValueSet;
    }
}
