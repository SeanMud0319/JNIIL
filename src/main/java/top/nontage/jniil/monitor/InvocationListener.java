package top.nontage.jniil.monitor;

@FunctionalInterface
public interface InvocationListener {
    void onInvoke(CallerDetail callerDetail, Object target, Object[] args, InvocationControl control);
    default boolean needsCaller() {
        return true;
    }
}
