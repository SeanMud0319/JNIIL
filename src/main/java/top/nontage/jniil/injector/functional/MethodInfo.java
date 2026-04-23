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
        T var = (T) capturedLocals.get(name);
        if (var == null) {
            throw new IllegalStateException("Local variable '" + name + "' was not captured. " +
                    "Make sure to include it in @Capture annotation and that it's in scope at injection point.");
        }
        return var;
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

    public void setArgument(int index, Object value) {
        if (index < 0 || index >= arguments.length) {
            throw new IllegalArgumentException("Invalid argument index: " + index);
        }
        arguments[index] = value;
    }

    public <T> void setLocal(String name, T value) {
        capturedLocals.put(name, value);
    }

    public void setArguments(Object... newArgs) {
        if (newArgs.length != arguments.length) {
            throw new IllegalArgumentException("Argument count mismatch: expected " +
                    arguments.length + ", got " + newArgs.length);
        }
        System.arraycopy(newArgs, 0, arguments, 0, arguments.length);
    }

    public void cancel() {
        this.cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }

}
