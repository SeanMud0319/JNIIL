package top.nontage.jniil.monitor;

import java.lang.reflect.Executable;

@FunctionalInterface
public interface InvocationListener {
    void onInvoke(CallerDetail callerDetail, Object target, Executable targetMethod, Object[] args, InvocationControl control);

    default boolean needsCaller() {
        return true;
    }
}
