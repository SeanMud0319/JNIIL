package top.nontage.jniil.asm.shadow.metadata;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class MultiBinding {
    private final Class<?> shadowClass;
    private final Map<String, Supplier<Object>> suppliers = new HashMap<>();

    public MultiBinding(Class<?> shadowClass) {
        this.shadowClass = shadowClass;
    }

    public MultiBinding bind(Object instance) {
        if (instance == null) {
            throw new IllegalArgumentException("Binding instance cannot be null.");
        }
        return bindInstance(instance);
    }

    @SuppressWarnings("unchecked")
    private <T> MultiBinding bindInstance(T instance) {
        return bind((Class<T>) instance.getClass(), instance);
    }

    @SuppressWarnings("unchecked")
    public <T> MultiBinding bind(Supplier<T> supplier) {
        if (supplier == null) {
            throw new IllegalArgumentException("Binding supplier cannot be null.");
        }
        T instance = supplier.get();
        if (instance == null) {
            throw new IllegalStateException("Supplier provided a null instance, cannot determine type for binding.");
        }
        return bind((Class<T>) instance.getClass(), supplier);
    }

    public <T> MultiBinding bind(Class<T> targetClass, T instance) {
        return bind(targetClass, (Supplier<T>) () -> instance);
    }

    @SuppressWarnings("unchecked")
    public <T> MultiBinding bind(Class<T> targetClass, Supplier<T> supplier) {
        String targetName = targetClass.getName().replace('.', '/');
        if (suppliers.containsKey(targetName)) {
            throw new IllegalArgumentException("Target class already bound in this MultiBinding: " + targetClass.getName());
        }
        suppliers.put(targetName, (Supplier<Object>) supplier);
        return this;
    }

    public Class<?> getShadowClass() {
        return shadowClass;
    }

    public Map<String, Supplier<Object>> getSuppliers() {
        return suppliers;
    }
}