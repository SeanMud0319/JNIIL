package top.nontage.jniil.injector.functional;

public class InvokeRedirectInfo {

    private final MethodInfo info;
    private Object receiver;
    private final Object[] redirectParameters;
    private Object returnValue;
    private boolean returnValueSet;
    private final String returnTypeDesc;

    public InvokeRedirectInfo(MethodInfo info, Object receiver, Object[] redirectParameters, String returnTypeDesc) {
        this.info = info;
        this.receiver = receiver;
        this.redirectParameters = redirectParameters;
        this.returnTypeDesc = returnTypeDesc;
    }

    public MethodInfo getMethodInfo() {
        return info;
    }

    @SuppressWarnings("unchecked")
    public <T> T getReceiver() {
        return (T) receiver;
    }

    public void setReceiver(Object receiver) {
        this.receiver = receiver;
    }

    @SuppressWarnings("unchecked")
    public <T> T getRedirectParameter(int index) {
        return (T) redirectParameters[index];
    }

    public void setRedirectParameter(int index, Object value) {
        if (index < 0 || index >= redirectParameters.length) {
            throw new IllegalArgumentException("Invalid redirect parameter index: " + index
                    + " (length=" + redirectParameters.length + ")");
        }
        redirectParameters[index] = value;
    }

    public Object[] getRedirectParameters() {
        return redirectParameters;
    }

    public int getRedirectParameterCount() {
        return redirectParameters.length;
    }

    public Object getReturnValue() {
        return returnValue;
    }

    public void setReturnValue(Object value) {
        this.returnValue = value;
        this.returnValueSet = true;
    }

    public boolean isReturnValueSet() {
        return returnValueSet;
    }

    public String getReturnTypeDesc() {
        return returnTypeDesc;
    }
}