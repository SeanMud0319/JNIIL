package top.nontage.jniil.asm.shadow.metadata;

import java.util.Map;
import java.util.HashMap;

public class ShadowContext {
    public final Map<FieldKey, ShadowFieldInfo> shadowFields = new HashMap<>();
    public final Map<MethodKey, ShadowMethodInfo> shadowMethods = new HashMap<>();

    private final Map<String, Object> boundInstances = new HashMap<>();

    public void bindInstance(Class<?> shadowClass, Object instance) {
        bindInstance(shadowClass.getName(), instance);
    }

    public void bindInstance(String className, Object instance) {
        boundInstances.put(className.replace('.','/'), instance);
    }

    public Object getBoundInstance(String shadowClassInternalName) {
        return boundInstances.get(shadowClassInternalName);
    }

    public void reset() {
        shadowFields.clear();
        shadowMethods.clear();
        boundInstances.clear();
    }
}