package top.nontage.jniil.monitor;

@FunctionalInterface
public interface InvocationListener {
    void onInvoke(Class<?> callerClass, Object target, Object[] args, InvocationControl control);
}
