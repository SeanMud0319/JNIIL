package top.nontage.jniil.interfaces;

import javassist.CtClass;
import javassist.CtMethod;
import top.nontage.auth.library.annotation.Protect;

@Protect
public interface Injectable {
    /**
     * Returns the source code that should be injected.
     * This method is expected to return a string representation of the code.
     * This method is only for MethodInjector to use.
     *
     * @return the source code to be injected
     */
    default String getInjectSourceCode() {
        return "";
    }

    default String getInjectSourceCode(CtMethod ctMethod) {
        return "";
    }

    default String targetTypeInternalName() {
        return "";
    }

    default Class<?> targetType() {
        return null;
    }

    default String targetMethodName() {
        return "";
    }

    default String[] targetMethodParams() {
        return new String[0];
    }

    default Class<?>[] targetMethodParamTypes() {
        return new Class<?>[0];
    }

    default String targetTypeThreadName() {
        return "";
    }

    default Class<?>[] appendClassLoader() {
        return new Class<?>[0];
    }

    default String[] appendFileLoader() {
        return new String[]{};
    }

    default String[] appendJarLoader() {
        return new String[]{};
    }

    default boolean defaultLoader() {
        return true;
    }
}
