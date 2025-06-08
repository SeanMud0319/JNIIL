package top.nontage.interfaces;

public interface Injectable {
    /**
     * Returns the source code that should be injected.
     * This method is expected to return a string representation of the code.
     * This method is only for MethodInjector to use.
     * @return the source code to be injected
     */
    default String getInjectSourceCode() {
        return null;
    };
}
