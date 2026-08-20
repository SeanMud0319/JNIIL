package top.nontage.jniil.utils;

import sun.misc.Unsafe;
import sun.reflect.Reflection;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.ProtectionDomain;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class UnsafeUtil {

    public static final Unsafe unsafe;
    public static final MethodHandles.Lookup IMPL_LOOKUP;
    public static final Set<String> ALL_MEMBERS = new HashSet<>();
    private static final MethodHandle FIND_VAR_HANDLE_MH;
    private static final Map<String, Field> FIELD_BYPASS_CACHE = new ConcurrentHashMap<>();
    private static final Object FILTER_MAP_LOCK = new Object();

    static {
        ALL_MEMBERS.add("*");
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafe = (Unsafe) f.get(null);
        } catch (Exception e) {
            throw new RuntimeException("Unable to access Unsafe", e);
        }

        MethodHandles.Lookup lookup;
        try {
            Field field = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
            try {
                field.setAccessible(true);
                lookup = (MethodHandles.Lookup) field.get(null);
            } catch (Throwable t) {
                long offset = unsafe.staticFieldOffset(field);
                Object base = unsafe.staticFieldBase(field);
                lookup = (MethodHandles.Lookup) unsafe.getObject(base, offset);
            }
        } catch (Throwable t) {
            throw new RuntimeException("Failed to acquire IMPL_LOOKUP in this JVM environment", t);
        }
        IMPL_LOOKUP = lookup;

        MethodHandle findVarHandleMH = null;
        try {
            Method findVarHandle = MethodHandles.Lookup.class.getMethod("findVarHandle", Class.class, String.class, Class.class);
            findVarHandleMH = IMPL_LOOKUP.unreflect(findVarHandle);
        } catch (Throwable ignored) {
        }
        FIND_VAR_HANDLE_MH = findVarHandleMH;
    }

    private UnsafeUtil() {
    }

    @SuppressWarnings("unchecked")
    private static <T> T getAny(Class<T> type, Object base, long offset) {
        if (type == boolean.class) return (T) (Boolean) unsafe.getBoolean(base, offset);
        if (type == byte.class) return (T) (Byte) unsafe.getByte(base, offset);
        if (type == short.class) return (T) (Short) unsafe.getShort(base, offset);
        if (type == int.class) return (T) (Integer) unsafe.getInt(base, offset);
        if (type == long.class) return (T) (Long) unsafe.getLong(base, offset);
        if (type == float.class) return (T) (Float) unsafe.getFloat(base, offset);
        if (type == double.class) return (T) (Double) unsafe.getDouble(base, offset);
        if (type == char.class) return (T) (Character) unsafe.getChar(base, offset);
        return (T) unsafe.getObject(base, offset);
    }

    private static void setAny(Class<?> type, Object base, long offset, Object value) {
        if (type == boolean.class) unsafe.putBoolean(base, offset, (boolean) value);
        else if (type == byte.class) unsafe.putByte(base, offset, (byte) value);
        else if (type == short.class) unsafe.putShort(base, offset, (short) value);
        else if (type == int.class) unsafe.putInt(base, offset, (int) value);
        else if (type == long.class) unsafe.putLong(base, offset, (long) value);
        else if (type == float.class) unsafe.putFloat(base, offset, (float) value);
        else if (type == double.class) unsafe.putDouble(base, offset, (double) value);
        else if (type == char.class) unsafe.putChar(base, offset, (char) value);
        else unsafe.putObject(base, offset, value);
    }

    /**
     * Bypasses field filter. Slower than forceGet because it temporarily removes the field from filter.
     */
    @SuppressWarnings("unused")
    public static <T> T forceGetSlow(Class<?> c, String name, Object obj) {
        String cacheKey = c.getName() + "#" + name;
        Field field = FIELD_BYPASS_CACHE.get(cacheKey);

        if (field == null) {
            field = findFieldWithBypass(c, name);
            FIELD_BYPASS_CACHE.put(cacheKey, field);
        }

        return forceGet(field, obj);
    }

    /**
     * Bypasses field filter. Slower than forceSet because it temporarily removes the field from filter.
     */
    @SuppressWarnings("unused")
    public static void forceSetSlow(Class<?> c, String name, Object obj, Object value) {
        String cacheKey = c.getName() + "#" + name;
        Field field = FIELD_BYPASS_CACHE.get(cacheKey);

        if (field == null) {
            field = findFieldWithBypass(c, name);
            FIELD_BYPASS_CACHE.put(cacheKey, field);
        }

        forceSet(field, obj, value);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static Object forceInvokeSlow(Class<?> declaringClass, String methodName, MethodType methodType, Object receiver, Object... args) {
        try {
            MethodHandle mh;
            if (receiver == null || receiver instanceof Class) {
                mh = IMPL_LOOKUP.findStatic(declaringClass, methodName, methodType);
                return mh.invokeWithArguments(args);
            } else {
                mh = IMPL_LOOKUP.findVirtual(declaringClass, methodName, methodType);
                return mh.invokeWithArguments(prepend(receiver, args));
            }
        } catch (Throwable e) {
            throw new RuntimeException("Failed to force invoke method in MethodHandle.", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T forceGet(Field field, Object obj) {
        try {
            Class<?> type = field.getType();
            if (Modifier.isStatic(field.getModifiers())) {
                Object base = unsafe.staticFieldBase(field);
                long offset = unsafe.staticFieldOffset(field);
                return (T) getAny(type, base, offset);
            } else {
                long offset = unsafe.objectFieldOffset(field);
                return (T) getAny(type, obj, offset);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void forceSet(Field field, Object obj, Object value) {
        try {
            if (Modifier.isStatic(field.getModifiers())) {
                Object base = unsafe.staticFieldBase(field);
                long offset = unsafe.staticFieldOffset(field);
                setAny(field.getType(), base, offset, value);
            } else {
                long offset = unsafe.objectFieldOffset(field);
                setAny(field.getType(), obj, offset, value);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unused")
    public static Object forceGetUnsafe(Field field, Object obj) {
        try {
            if (Modifier.isStatic(field.getModifiers())) {
                Object base = unsafe.staticFieldBase(field);
                long offset = unsafe.staticFieldOffset(field);
                return unsafe.getObject(base, offset);
            } else {
                long offset = unsafe.objectFieldOffset(field);
                return unsafe.getObject(obj, offset);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unused")
    public static void forceSetUnsafe(Field field, Object obj, Object value) {
        try {
            if (Modifier.isStatic(field.getModifiers())) {
                Object base = unsafe.staticFieldBase(field);
                long offset = unsafe.staticFieldOffset(field);
                unsafe.putObject(base, offset, value);
            } else {
                long offset = unsafe.objectFieldOffset(field);
                unsafe.putObject(obj, offset, value);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Put the instance if the field is object or put Class if the field is static
    public static void forceSetMH(Object instanceOrClass, String fieldName, Class<?> fieldType, Object value) {
        if (FIND_VAR_HANDLE_MH == null) {
            throw new UnsupportedOperationException("Failed to acquire VarHandle in this JVM environment");
        }
        try {
            Class<?> clazz = (instanceOrClass instanceof Class) ? (Class<?>) instanceOrClass : instanceOrClass.getClass();
            Object receiver = (instanceOrClass instanceof Class) ? null : instanceOrClass;
            Object vh = FIND_VAR_HANDLE_MH.invoke(IMPL_LOOKUP, clazz, fieldName, fieldType);
            Class<?> varHandleClass = Class.forName("java.lang.invoke.VarHandle");
            Class<?> valueType = fieldType.isPrimitive() ? fieldType : Object.class;
            MethodHandle setMH = IMPL_LOOKUP.findStatic(
                    vh.getClass(),
                    "set",
                    MethodType.methodType(void.class, varHandleClass, Object.class, valueType)
            );

            setMH.invoke(vh, receiver, value);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to force set value in MethodHandle.", e);
        }
    }

    // Put the instance if the field is object or put Class if the field is static
    @SuppressWarnings("unused")
    public static Object forceGetMH(Object instanceOrClass, String fieldName, Class<?> fieldType) {
        if (FIND_VAR_HANDLE_MH == null) {
            throw new UnsupportedOperationException("Failed to acquire VarHandle in this JVM environment");
        }
        try {
            Class<?> clazz = (instanceOrClass instanceof Class) ? (Class<?>) instanceOrClass : instanceOrClass.getClass();
            Object receiver = (instanceOrClass instanceof Class) ? null : instanceOrClass;
            Object vh = FIND_VAR_HANDLE_MH.invoke(IMPL_LOOKUP, clazz, fieldName, fieldType);
            MethodHandle getMH = IMPL_LOOKUP.findVirtual(
                    vh.getClass(),
                    "get",
                    MethodType.methodType(Object.class, Object.class)
            );
            if (receiver == null) {
                return getMH.invoke(vh, null);
            } else {
                return getMH.invoke(vh, receiver);
            }
        } catch (Throwable e) {
            throw new RuntimeException("Failed to force get value in MethodHandle.", e);
        }
    }

    public static Object forceInvoke(Method method, Object obj, Object... args) {
        try {
            MethodType type = MethodType.methodType(method.getReturnType(), method.getParameterTypes());
            boolean isStatic = Modifier.isStatic(method.getModifiers());
            if (isStatic) {
                return IMPL_LOOKUP.findStatic(method.getDeclaringClass(), method.getName(), type)
                        .invokeWithArguments(args);
            } else {
                return IMPL_LOOKUP.findVirtual(method.getDeclaringClass(), method.getName(), type)
                        .invokeWithArguments(prepend(obj, args));
            }
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @SuppressWarnings("unused")
    public static void registerFieldsToFilter(Class<?> containingClass, Set<String> fieldNames) {
        registerFilterInternal(containingClass, fieldNames, "registerFieldsToFilter");
    }

    @SuppressWarnings("unused")
    public static void registerMethodsToFilter(Class<?> containingClass, Set<String> methodNames) {
        registerFilterInternal(containingClass, methodNames, "registerMethodsToFilter");
    }

    public static Object forceNewInstance(Class<?> clazz, Class<?>[] parameterTypes, Object... args) {
        try {
            MethodType type = MethodType.methodType(void.class, parameterTypes);
            return IMPL_LOOKUP.findConstructor(clazz, type).invokeWithArguments(args);
        } catch (Throwable t) {
            try {
                Object instance = unsafe.allocateInstance(clazz);
                if (args != null && args.length > 0) {
                    Field[] fields = clazz.getDeclaredFields();
                    for (int i = 0; i < args.length && i < fields.length; i++) {
                        Field field = fields[i];
                        Object arg = args[i];

                        if (arg != null && field.getType().isAssignableFrom(arg.getClass())) {
                            forceSet(field, instance, arg);
                        }
                    }
                }
                return instance;
            } catch (Exception e) {
                throw new RuntimeException("Failed to force call constructor for " + clazz.getName(), t);
            }
        }
    }

    public static Object forceAllocateInstance(Class<?> clazz) {
        try {
            return unsafe.allocateInstance(clazz);
        } catch (InstantiationException e) {
            throw new RuntimeException("Failed to allocate instance of " + clazz.getName(), e);
        }
    }

    public static Class<?> defineClass(String name, ClassLoader loader, byte[] bytes) {
        try {
            Class<?> internalUnsafeClass = Class.forName("jdk.internal.misc.Unsafe");
            Field theUnsafe = internalUnsafeClass.getDeclaredField("theUnsafe");
            Object internalUnsafe = forceGet(theUnsafe, null);
            Method defineClassMethod = internalUnsafeClass.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class, ClassLoader.class, ProtectionDomain.class);
            return (Class<?>) forceInvoke(defineClassMethod, internalUnsafe, name, bytes, 0, bytes.length, loader, null);
        } catch (Throwable throwable) {
            if (throwable.getClass() == ClassNotFoundException.class) {
                return unsafe.defineClass(name, bytes, 0, bytes.length, loader, null);
            }
            throw new RuntimeException(throwable);
        }
    }

    private static Object[] prepend(Object first, Object[] rest) {
        Object[] result = new Object[rest.length + 1];
        result[0] = first;
        System.arraycopy(rest, 0, result, 1, rest.length);
        return result;
    }

    private static void registerFilterInternal(Class<?> containingClass, Set<String> names, String methodName) {
        try {
            Class<?> c = Class.forName("jdk.internal.reflect.Reflection");
            int javaVersion = DebugUtil.getJavaVersion();

            if (javaVersion >= 9) {
                forceInvokeSlow(c, methodName, MethodType.methodType(void.class, Class.class, Set.class), null, containingClass, names);
            } else {
                String[] namesArray = names.toArray(new String[0]);
                if (methodName.contains("Fields")) {
                    Reflection.registerFieldsToFilter(containingClass, namesArray);
                } else {
                    Reflection.registerMethodsToFilter(containingClass, namesArray);
                }
            }
        } catch (Throwable e) {
            throw new UnsupportedOperationException("Failed to register filter (" + methodName + "). Your Java version may not support this feature. Version: " + DebugUtil.getJavaVersion(), e);
        }
    }

    private static Field findFieldWithBypass(Class<?> c, String name) {
        try {
            return c.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            return bypassFieldFilter(c, name, e);
        }
    }

    private static Field bypassFieldFilter(Class<?> c, String name, NoSuchFieldException original) {
        try {
            int javaVersion = DebugUtil.getJavaVersion();
            Class<?> reflectionClass = (javaVersion >= 9)
                    ? Class.forName("jdk.internal.reflect.Reflection")
                    : Reflection.class;

            MethodHandle getter = IMPL_LOOKUP.findStaticGetter(reflectionClass, "fieldFilterMap", Map.class);
            @SuppressWarnings("unchecked")
            Map<Class<?>, Object> filterMap = (Map<Class<?>, Object>) getter.invoke();

            if (filterMap == null) {
                throw new RuntimeException("Field not found: " + name, original);
            }

            Object filterValue = filterMap.get(c);
            if (filterValue == null) {
                throw new RuntimeException("Field not found: " + name, original);
            }

            return modifyFilterAndGetField(c, name, filterMap, filterValue, original);

        } catch (Throwable ex) {
            throw new RuntimeException("Failed to bypass field filter for: " + name, ex);
        }
    }

    private static Field modifyFilterAndGetField(Class<?> c, String name, Map<Class<?>, Object> filterMap,
                                                 Object filterValue, NoSuchFieldException original) {
        synchronized (FILTER_MAP_LOCK) {
            if (filterValue instanceof String[]) {
                String[] filtered = (String[]) filterValue;
                if (!arrayContains(filtered, name)) {
                    throw new RuntimeException("Field not found: " + name, original);
                }
                String[] newFiltered = arrayRemove(filtered, name);
                filterMap.put(c, newFiltered);
                return getFieldAndRestore(c, name, filterMap, filtered);
            }

            if (filterValue instanceof Set) {
                @SuppressWarnings("unchecked")
                Set<String> filteredSet = (Set<String>) filterValue;
                if (!filteredSet.contains(name)) {
                    throw new RuntimeException("Field not found: " + name, original);
                }
                Set<String> newSet = new HashSet<>(filteredSet);
                newSet.remove(name);
                filterMap.put(c, newSet);
                return getFieldAndRestore(c, name, filterMap, filteredSet);
            }

            throw new RuntimeException("Unsupported filter type: " + filterValue.getClass(), original);
        }
    }

    private static Field getFieldAndRestore(Class<?> c, String name, Map<Class<?>, Object> filterMap, Object restoreValue) {
        try {
            clearReflectionData(c);
            return c.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Field not found after bypass: " + name, e);
        } finally {
            filterMap.put(c, restoreValue);
            // clear cache again, (apply restore)
            clearReflectionData(c);
        }
    }

    private static void clearReflectionData(Class<?> c) {
        try {
            Field rdField = Class.class.getDeclaredField("reflectionData");
            long offset = unsafe.objectFieldOffset(rdField);
            unsafe.putObject(c, offset, null);
        } catch (NoSuchFieldException ignored) {

        }
    }

    private static boolean arrayContains(String[] array, String target) {
        for (String s : array) {
            if (s.equals(target)) return true;
        }
        return false;
    }

    private static String[] arrayRemove(String[] array, String target) {
        String[] result = new String[array.length - 1];
        int idx = 0;
        for (String s : array) {
            if (!s.equals(target)) {
                result[idx++] = s;
            }
        }
        return result;
    }
}