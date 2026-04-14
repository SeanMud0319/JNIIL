package top.nontage.jniil.monitor;

public class CallerDetail {
    private final Class<?> callerClass;
    private final String callerMethodName;

    public CallerDetail() {
        this.callerClass = null;
        this.callerMethodName = null;
    }

    public CallerDetail(Class<?> callerClass, String callerMethodName) {
        this.callerClass = callerClass;
        this.callerMethodName = callerMethodName;
    }

    public Class<?> getCallerClass() {
        return callerClass;
    }

    public String getCallerMethodName() {
        return callerMethodName;
    }
}
