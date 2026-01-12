package top.nontage.jniil.asm.shadow.metadata;

import java.util.Map;
import java.util.HashMap;
import java.util.function.Supplier;

public class ShadowContext {
    public final Map<FieldKey, ShadowFieldInfo> shadowFields = new HashMap<>();
    public final Map<MethodKey, ShadowMethodInfo> shadowMethods = new HashMap<>();

    private final Map<String, Supplier<Object>> boundInstances = new HashMap<>();

    public void bindInstance(Class<?> shadowClass, Supplier<Object> instanceSupplier) {
        bindInstance(shadowClass.getName(), instanceSupplier);
    }

    public void bindInstance(String className, Supplier<Object> instanceSupplier) {
        boundInstances.put(className.replace('.','/'), instanceSupplier);
    }

    public void unbindInstance(Class<?> shadowClass) {
        unbindInstance(shadowClass.getName());
    }

    public void unbindInstance(String className) {
        boundInstances.remove(className.replace('.', '/'));
    }

    public Supplier<Object> getBoundInstanceSupplier(String shadowClassInternalName) {
        return boundInstances.get(shadowClassInternalName);
    }

    public void reset() {
        shadowFields.clear();
        shadowMethods.clear();
        boundInstances.clear();
    }
}