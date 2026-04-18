package top.nontage.jniil.interfaces;

import javassist.CtMethod;

import java.util.Map;

public interface FunctionalInjectable extends Injectable {

    @Override
    default String getInjectSourceCode(CtMethod ctMethod) {
        throw new UnsupportedOperationException(
                "FunctionalInjectable generates redirection code automatically. " +
                        "Direct source code injection via getInjectSourceCode() is not supported."
        );
    }

    @Override
    default String targetTypeInternalName() {
        throw new UnsupportedOperationException("Metadata must be provided via @InjectMethodInfo annotation on the injection method.");
    }

    @Override
    default Class<?> targetType() {
        throw new UnsupportedOperationException("Metadata must be provided via @InjectMethodInfo annotation on the injection method.");
    }

    @Override
    default String targetMethodName() {
        throw new UnsupportedOperationException("Metadata must be provided via @InjectMethodInfo annotation on the injection method.");
    }

    @Override
    default String[] targetMethodParams() {
        throw new UnsupportedOperationException("Metadata must be provided via @InjectMethodInfo annotation on the injection method.");
    }

    @Override
    default Class<?>[] targetMethodParamTypes() {
        throw new UnsupportedOperationException("Metadata must be provided via @InjectMethodInfo annotation on the injection method.");
    }

    @Override
    default String targetTypeThreadName() {
        throw new UnsupportedOperationException("Thread-specific binding must be configured via @InjectMethodInfo.");
    }

    @Override
    default Class<?>[] appendClassLoader() {
        throw new UnsupportedOperationException("ClassLoader appending must be configured via @InjectMethodInfo.appendClassLoader().");
    }

    @Override
    default String[] appendFileLoader() {
        throw new UnsupportedOperationException("File-based ClassPath appending must be configured via @InjectMethodInfo.appendFileLoader().");
    }

    @Override
    default String[] appendJarLoader() {
        throw new UnsupportedOperationException("Jar-based ClassPath appending must be configured via @InjectMethodInfo.appendJarLoader().");
    }

    @Override
    default Map<String, byte[]> appendByteLoader() {
        throw new UnsupportedOperationException("Byte-based ClassPath appending must be configured via @InjectMethodInfo.");
    }

    @Override
    default boolean defaultLoader() {
        throw new UnsupportedOperationException("DefaultLoader toggle must be configured via @InjectMethodInfo.defaultLoader().");
    }
}