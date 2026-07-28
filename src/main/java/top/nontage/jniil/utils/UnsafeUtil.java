package top.nontage.jniil.utils;

import sun.misc.Unsafe;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.ProtectionDomain;

public class UnsafeUtil {

    public static final Unsafe unsafe;
    public static final MethodHandles.Lookup IMPL_LOOKUP;
    public static final MethodHandle FIND_VAR_HANDLE_MH;
    public static final MethodHandle VAR_HANDLE_GET_MH;
    public static final MethodHandle VAR_HANDLE_SET_MH;

    static {
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
        MethodHandle varHandleGetMH = null;
        MethodHandle varHandleSetMH = null;

        try {
            Class<?> varHandleClass = Class.forName("java.lang.invoke.VarHandle");

            Method findVarHandle = MethodHandles.Lookup.class.getMethod("findVarHandle", Class.class, String.class, Class.class);
            findVarHandleMH = IMPL_LOOKUP.unreflect(findVarHandle);

            // VarHandle.get 和 set 是 PolymorphicSignature，需要用 MethodType 匹配
            // 它們的實際簽名是 (Object...) 但會根據參數型別動態調整
            // 使用 MethodType.genericMethodType 來匹配變參數
            MethodType getType = MethodType.genericMethodType(1);
            MethodType setType = MethodType.genericMethodType(2);

            varHandleGetMH = IMPL_LOOKUP.findVirtual(varHandleClass, "get", getType);
            varHandleSetMH = IMPL_LOOKUP.findVirtual(varHandleClass, "set", setType);
        } catch (Throwable ignored) {
        }

        FIND_VAR_HANDLE_MH = findVarHandleMH;
        VAR_HANDLE_GET_MH = varHandleGetMH;
        VAR_HANDLE_SET_MH = varHandleSetMH;
    }

    private UnsafeUtil() {

    }

    private static Object getAny(Class<?> type, Object base, long offset) {
        if (type == boolean.class) return unsafe.getBoolean(base, offset);
        if (type == byte.class) return unsafe.getByte(base, offset);
        if (type == short.class) return unsafe.getShort(base, offset);
        if (type == int.class) return unsafe.getInt(base, offset);
        if (type == long.class) return unsafe.getLong(base, offset);
        if (type == float.class) return unsafe.getFloat(base, offset);
        if (type == double.class) return unsafe.getDouble(base, offset);
        if (type == char.class) return unsafe.getChar(base, offset);
        return unsafe.getObject(base, offset);
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

    public static Object forceGet(Field field, Object obj) {
        try {
            if (Modifier.isStatic(field.getModifiers())) {
                Object base = unsafe.staticFieldBase(field);
                long offset = unsafe.staticFieldOffset(field);
                return getAny(field.getType(), base, offset);
            } else {
                long offset = unsafe.objectFieldOffset(field);
                return getAny(field.getType(), obj, offset);
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

    // Put the instance if the field is object or put Class if the field is static
    public static Object forceGetMH(Object instanceOrClass, String fieldName, Class<?> fieldType) throws Throwable {
        Class<?> clazz;
        Object instance = null;

        if (instanceOrClass instanceof Class) {
            clazz = (Class<?>) instanceOrClass;
        } else {
            clazz = instanceOrClass.getClass();
            instance = instanceOrClass;
        }

        boolean isStatic = instanceOrClass instanceof Class;
        Object receiver = isStatic ? null : instance;

        Object vh = FIND_VAR_HANDLE_MH.invoke(IMPL_LOOKUP, clazz, fieldName, fieldType);
        return VAR_HANDLE_GET_MH.invoke(vh, receiver);
    }

    // Put the instance if the field is object or put Class if the field is static
    public static void forceSetMH(Object instanceOrClass, String fieldName, Class<?> fieldType, Object value) throws Throwable {
        Class<?> clazz;
        Object instance = null;

        if (instanceOrClass instanceof Class) {
            clazz = (Class<?>) instanceOrClass;
        } else {
            clazz = instanceOrClass.getClass();
            instance = instanceOrClass;
        }

        boolean isStatic = instanceOrClass instanceof Class;
        Object receiver = isStatic ? null : instance;

        Object vh = FIND_VAR_HANDLE_MH.invoke(IMPL_LOOKUP, clazz, fieldName, fieldType);
        VAR_HANDLE_SET_MH.invoke(vh, receiver, value);
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

    // Define class into Both ClassLoader include BootstrapClassLoader
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
}