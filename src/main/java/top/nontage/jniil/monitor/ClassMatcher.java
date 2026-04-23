package top.nontage.jniil.monitor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class ClassMatcher {

    private final String pattern;
    private Predicate<Method> methodFilter = m -> true;
    private Predicate<Constructor<?>> constructorFilter = c -> false;
    private final List<InvocationListener> listeners = new ArrayList<>();

    public ClassMatcher(String pattern) {
        this.pattern = pattern;
    }

    public boolean matches(String className) {
        String internalName = className.replace('.', '/');
        String normalizedPattern = pattern.replace('.', '/');

        if (normalizedPattern.equals("*")) return true;
        if (normalizedPattern.endsWith("/**")) {
            String prefix = normalizedPattern.substring(0, normalizedPattern.length() - 2);
            return internalName.startsWith(prefix);
        }
        if (normalizedPattern.endsWith("/*")) {
            String prefix = normalizedPattern.substring(0, normalizedPattern.length() - 1);
            String packagePart = internalName.substring(0, internalName.lastIndexOf('/') + 1);
            return packagePart.equals(prefix);
        }
        return internalName.equals(normalizedPattern);
    }

    public boolean matchesMethod(Method method) {
        return methodFilter.test(method);
    }

    public boolean matchesConstructor(Constructor<?> constructor) {
        return constructorFilter.test(constructor);
    }

    public ClassMatcher methodName(String name) {
        this.methodFilter = this.methodFilter.and(m -> m.getName().equals(name));
        return this;
    }

    public ClassMatcher methodNameStartsWith(String prefix) {
        this.methodFilter = this.methodFilter.and(m -> m.getName().startsWith(prefix));
        return this;
    }

    public ClassMatcher methodNameEndsWith(String suffix) {
        this.methodFilter = this.methodFilter.and(m -> m.getName().endsWith(suffix));
        return this;
    }

    public ClassMatcher methodNameContains(String infix) {
        this.methodFilter = this.methodFilter.and(m -> m.getName().contains(infix));
        return this;
    }

    public ClassMatcher returnType(Class<?> returnType) {
        this.methodFilter = this.methodFilter.and(m -> m.getReturnType().equals(returnType));
        return this;
    }

    public ClassMatcher returnTypeVoid() {
        this.methodFilter = this.methodFilter.and(m -> m.getReturnType() == void.class);
        return this;
    }

    public ClassMatcher methodParameterTypes(Class<?>... paramTypes) {
        this.methodFilter = this.methodFilter.and(m -> {
            Class<?>[] actualParams = m.getParameterTypes();
            if (actualParams.length != paramTypes.length) return false;
            for (int i = 0; i < paramTypes.length; i++) {
                if (!actualParams[i].equals(paramTypes[i])) return false;
            }
            return true;
        });
        return this;
    }

    public ClassMatcher methodParameterCount(int count) {
        this.methodFilter = this.methodFilter.and(m -> m.getParameterCount() == count);
        return this;
    }

    public ClassMatcher methodContainsParameterType(Class<?> targetType) {
        this.methodFilter = this.methodFilter.and(m -> {
            for (Class<?> param : m.getParameterTypes()) {
                if (param.equals(targetType)) {
                    return true;
                }
            }
            return false;
        });
        return this;
    }

    public ClassMatcher methodContainsAnyParameterType(Class<?>... targetTypes) {
        this.methodFilter = this.methodFilter.and(m -> {
            for (Class<?> param : m.getParameterTypes()) {
                for (Class<?> target : targetTypes) {
                    if (param.equals(target)) {
                        return true;
                    }
                }
            }
            return false;
        });
        return this;
    }

    public ClassMatcher annotatedWith(Class<? extends Annotation> annotation) {
        this.methodFilter = this.methodFilter.and(m -> m.isAnnotationPresent(annotation));
        return this;
    }

    public ClassMatcher allMethods() {
        this.methodFilter = m -> true;
        return this;
    }

    public ClassMatcher skipMethodName(String name) {
        this.methodFilter = this.methodFilter.and(m -> !m.getName().equals(name));
        return this;
    }

    public ClassMatcher skipMethodNameStartsWith(String prefix) {
        this.methodFilter = this.methodFilter.and(m -> !m.getName().startsWith(prefix));
        return this;
    }

    public ClassMatcher skipMethodNameEndsWith(String suffix) {
        this.methodFilter = this.methodFilter.and(m -> !m.getName().endsWith(suffix));
        return this;
    }

    public ClassMatcher skipMethodNameContains(String infix) {
        this.methodFilter = this.methodFilter.and(m -> !m.getName().contains(infix));
        return this;
    }

    public ClassMatcher skipReturnType(Class<?> returnType) {
        this.methodFilter = this.methodFilter.and(m -> !m.getReturnType().equals(returnType));
        return this;
    }

    public ClassMatcher skipMethodParameterTypes(Class<?>... paramTypes) {
        this.methodFilter = this.methodFilter.and(m -> {
            Class<?>[] actualParams = m.getParameterTypes();
            if (actualParams.length != paramTypes.length) return true;
            for (int i = 0; i < paramTypes.length; i++) {
                if (!actualParams[i].equals(paramTypes[i])) return false;
            }
            return false;
        });
        return this;
    }

    public ClassMatcher skipAnnotatedWith(Class<? extends Annotation> annotation) {
        this.methodFilter = this.methodFilter.and(m -> !m.isAnnotationPresent(annotation));
        return this;
    }

    public ClassMatcher constructorParameterTypes(Class<?>... paramTypes) {
        this.constructorFilter = this.constructorFilter.and(c -> {
            Class<?>[] actualParams = c.getParameterTypes();
            if (actualParams.length != paramTypes.length) return false;
            for (int i = 0; i < paramTypes.length; i++) {
                if (!actualParams[i].equals(paramTypes[i])) return false;
            }
            return true;
        });
        return this;
    }

    public ClassMatcher constructorParameterCount(int count) {
        this.constructorFilter = this.constructorFilter.and(c -> c.getParameterCount() == count);
        return this;
    }

    public ClassMatcher constructorContainsParameterType(Class<?> targetType) {
        this.constructorFilter = this.constructorFilter.and(c -> {
            for (Class<?> param : c.getParameterTypes()) {
                if (param.equals(targetType)) {
                    return true;
                }
            }
            return false;
        });
        return this;
    }

    public ClassMatcher allConstructors() {
        this.constructorFilter = c -> true;
        return this;
    }

    public ClassMatcher skipConstructors() {
        this.constructorFilter = c -> false;
        return this;
    }

    public ClassMatcher skipConstructorParameterTypes(Class<?>... paramTypes) {
        this.constructorFilter = this.constructorFilter.and(c -> {
            Class<?>[] actualParams = c.getParameterTypes();
            if (actualParams.length != paramTypes.length) return true;
            for (int i = 0; i < paramTypes.length; i++) {
                if (!actualParams[i].equals(paramTypes[i])) return false;
            }
            return false;
        });
        return this;
    }

    public ClassMatcher withListener(InvocationListener listener) {
        this.listeners.add(listener);
        return this;
    }

    public List<InvocationListener> getListeners() {
        return listeners;
    }
}