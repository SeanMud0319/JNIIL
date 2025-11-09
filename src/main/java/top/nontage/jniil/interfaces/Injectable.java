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
        return null;
    }

    default String getInjectSourceCode(CtMethod ctMethod) {
        return null;
    }

    default String targetTypeInternalName() {
        throw new UnsupportedOperationException("Must override if @Null is used");
    }

    default String targetMethodName() {
        throw new UnsupportedOperationException("Must override if @Null is used");
    }

    default String[] targetMethodParams() {
        return new String[0];
    }

    default Class<?>[] appendClassLoader() {
        return new Class<?>[0];
    }

    default String targetTypeThreadName() {
        return null;
    }

    default String[] appendFileLoader() {
        return null;
    }

    default String[] appendJarLoader() {
        return null;
    }

    default boolean defaultLoader() {
        return true;
    }
}
