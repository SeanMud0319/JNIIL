package top.nontage.jniil.utils;

import sun.misc.Unsafe;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class UnsafeUtil {

    public static final Unsafe unsafe;
    public static final MethodHandles.Lookup IMPL_LOOKUP;

    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafe = (Unsafe) f.get(null);
            Field implLookupField = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
            IMPL_LOOKUP = (MethodHandles.Lookup)
                    unsafe.getObject(
                            unsafe.staticFieldBase(implLookupField),
                            unsafe.staticFieldOffset(implLookupField)
                    );
        } catch (Exception e) {
            throw new RuntimeException("Unable to access Unsafe", e);
        }
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

    private static Object[] prepend(Object first, Object[] rest) {
        Object[] result = new Object[rest.length + 1];
        result[0] = first;
        System.arraycopy(rest, 0, result, 1, rest.length);
        return result;
    }
}
