package top.nontage.jniil.shadow.metadata;

import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class ShadowContext {
    public final Map<FieldKey, ShadowFieldInfo> shadowFields = new HashMap<>();
    public final Map<MethodKey, ShadowMethodInfo> shadowMethods = new HashMap<>();

    private final Map<String, Supplier<Object>> boundInstanceSuppliers = new ConcurrentHashMap<>();

    private static final String SEPARATOR = "#";

    private String makeKey(String shadowClassInternalName, String targetClassInternalName) {
        return shadowClassInternalName + SEPARATOR + targetClassInternalName;
    }

    public void bindInstances(String shadowClassName, Map<String, Supplier<Object>> suppliers) {
        String shadowInternal = shadowClassName.replace('.', '/');
        for (Map.Entry<String, Supplier<Object>> entry : suppliers.entrySet()) {
            String key = makeKey(shadowInternal, entry.getKey());
            boundInstanceSuppliers.put(key, entry.getValue());
        }
    }

    public void unbindInstances(String shadowClassName) {
        String shadowInternal = shadowClassName.replace('.', '/');
        boundInstanceSuppliers.keySet().removeIf(key -> key.startsWith(shadowInternal + SEPARATOR));
    }

    public Supplier<Object> getBoundInstanceSupplier(String shadowClassInternalName, String targetClassInternalName) {
        String key = makeKey(shadowClassInternalName, targetClassInternalName);
        return boundInstanceSuppliers.get(key);
    }

    public void reset() {
        shadowFields.clear();
        shadowMethods.clear();
        boundInstanceSuppliers.clear();
    }

    public int getBindingCount() {
        return boundInstanceSuppliers.size();
    }

    public boolean isBound(String shadowClassInternalName, String targetClassInternalName) {
        String key = makeKey(shadowClassInternalName, targetClassInternalName);
        return boundInstanceSuppliers.containsKey(key);
    }
}