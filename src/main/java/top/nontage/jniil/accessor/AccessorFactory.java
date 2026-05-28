package top.nontage.jniil.accessor;

import top.nontage.jniil.accessor.internal.AccessorGenerator;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

public class AccessorFactory {
    private static final Map<Object, Map<Class<?>, Object>> instanceCache = new WeakHashMap<>();

    @SuppressWarnings("unchecked")
    public static <T> T getAccessor(Object instance, Class<T> accessorInterface) {
        synchronized (instanceCache) {
            Map<Class<?>, Object> instanceAccessors = instanceCache.computeIfAbsent(
                    instance, k -> new ConcurrentHashMap<>()
            );
            return (T) instanceAccessors.computeIfAbsent(accessorInterface, key -> {
                try {
                    return AccessorGenerator.registerAccessor(instance, accessorInterface);
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}