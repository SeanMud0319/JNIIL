package top.nontage.jniil.injector.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InjectionCache {

    private static final Map<String, byte[]> CACHE = new ConcurrentHashMap<>();

    public static void put(Class<?> clazz, byte[] bytecode) {
        CACHE.put(clazz.getName(), bytecode);
    }

    public static byte[] get(Class<?> clazz) {
        return CACHE.get(clazz.getName());
    }

    public static boolean contains(Class<?> clazz) {
        return CACHE.containsKey(clazz.getName());
    }

    public static void clear() {
        CACHE.clear();
    }
}
