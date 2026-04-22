package top.nontage.jniil.injector.functional;

import java.util.HashMap;
import java.util.Map;

public class MethodInfo {
    private final Object target;
    private final Object[] arguments;
    private Object returnValue;
    private boolean cancelled;
    private final Map<String, Object> capturedLocals = new HashMap<>();

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

    @SuppressWarnings("unchecked")
    public <T> T getLocal(String name) {
        return (T) capturedLocals.get(name);
    }

    public Object getReturnValue() {
        return returnValue;
    }

    public void setReturnValue(Object obj) {
        this.returnValue = obj;
    }

    public void captureLocal(String name, Object value) {
        capturedLocals.put(name, value);
    }

    public void cancel() {
        this.cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }

}
