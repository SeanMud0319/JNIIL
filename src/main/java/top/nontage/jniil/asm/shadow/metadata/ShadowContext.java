package top.nontage.jniil.asm.shadow.metadata;

import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class ShadowContext {
    public final Map<FieldKey, ShadowFieldInfo> shadowFields = new HashMap<>();
    public final Map<MethodKey, ShadowMethodInfo> shadowMethods = new HashMap<>();

    private final Map<String, Map<String, Supplier<Object>>> boundInstanceSuppliers = new ConcurrentHashMap<>();

    public void bindInstances(String shadowClassName, Map<String, Supplier<Object>> suppliers) {
        boundInstanceSuppliers.put(shadowClassName.replace('.', '/'), suppliers);
    }

    public void unbindInstances(String shadowClassName) {
        boundInstanceSuppliers.remove(shadowClassName.replace('.', '/'));
    }

    public Supplier<Object> getBoundInstanceSupplier(String shadowClassInternalName, String targetClassInternalName) {
        Map<String, Supplier<Object>> targets = boundInstanceSuppliers.get(shadowClassInternalName);
        if (targets == null) {
            return null;
        }
        return targets.get(targetClassInternalName);
    }

    public void reset() {
        shadowFields.clear();
        shadowMethods.clear();
        boundInstanceSuppliers.clear();
    }
}