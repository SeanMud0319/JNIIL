package top.nontage.jniil.asm.shadow;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import sun.misc.Unsafe;
import top.nontage.jniil.asm.shadow.metadata.ShadowContextHolder;

import java.lang.invoke.*;
import java.lang.reflect.Field;

public class ShadowBootstrap {

    private static final MethodHandles.Lookup IMPL_LOOKUP;

    static {
        try {
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Unsafe unsafe = (Unsafe) unsafeField.get(null);

            Field implLookupField = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
            IMPL_LOOKUP = (MethodHandles.Lookup)
                    unsafe.getObject(
                            unsafe.staticFieldBase(implLookupField),
                            unsafe.staticFieldOffset(implLookupField)
                    );
        } catch (Exception e) {
            throw new RuntimeException("Failed to get IMPL_LOOKUP", e);
        }
    }

    public static CallSite bootstrap(
            MethodHandles.Lookup lookup,
            String name,
            MethodType callSiteType,
            String shadowOwner,
            String targetOwner,
            String targetName,
            String targetDesc,
            int opcode
    ) throws Throwable {
        ClassLoader loader = lookup.lookupClass().getClassLoader();
        Class<?> targetClass = Class.forName(targetOwner.replace('/', '.'), true, loader);

        MethodHandles.Lookup privilegedLookup = IMPL_LOOKUP.in(targetClass);

        boolean isStatic = (opcode == Opcodes.INVOKESTATIC || opcode == Opcodes.GETSTATIC || opcode == Opcodes.PUTSTATIC);
        boolean isGet = (opcode == Opcodes.GETFIELD || opcode == Opcodes.GETSTATIC);
        boolean isPut = (opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC);
        boolean isMethod = !isGet && !isPut;

        MethodHandle handle;

        if (isMethod) {
            MethodType targetType = MethodType.fromMethodDescriptorString(targetDesc, loader);
            if (isStatic) {
                handle = privilegedLookup.findStatic(targetClass, targetName, targetType);
            } else {
                Object instance = getInstance(shadowOwner);
                MethodHandle virtualHandle = privilegedLookup.findVirtual(targetClass, targetName, targetType);
                MethodHandle boundHandle = virtualHandle.bindTo(instance);
                handle = MethodHandles.dropArguments(boundHandle, 0, callSiteType.parameterType(0));
            }
        } else {
            Class<?> fieldType = getJavaClass(Type.getType(targetDesc), loader);
            if (isStatic) {
                if (isGet) {
                    handle = privilegedLookup.findStaticGetter(targetClass, targetName, fieldType);
                } else {
                    handle = privilegedLookup.findStaticSetter(targetClass, targetName, fieldType);
                }
            } else {
                Object instance = getInstance(shadowOwner);
                MethodHandle fieldHandle;
                if (isGet) {
                    fieldHandle = privilegedLookup.findGetter(targetClass, targetName, fieldType).bindTo(instance);
                } else {
                    fieldHandle = privilegedLookup.findSetter(targetClass, targetName, fieldType).bindTo(instance);
                }
                handle = MethodHandles.dropArguments(fieldHandle, 0, callSiteType.parameterType(0));
            }
        }

        return new ConstantCallSite(handle.asType(callSiteType));
    }

    private static Object getInstance(String shadowOwner) {
        Object instance = ShadowContextHolder.INSTANCE.getBoundInstance(shadowOwner);
        if (instance == null) {
            throw new IllegalStateException("No bound instance for " + shadowOwner);
        }
        return instance;
    }

    private static Class<?> getJavaClass(Type type, ClassLoader classLoader) throws ClassNotFoundException {
        switch (type.getSort()) {
            case Type.BOOLEAN:
                return boolean.class;
            case Type.CHAR:
                return char.class;
            case Type.BYTE:
                return byte.class;
            case Type.SHORT:
                return short.class;
            case Type.INT:
                return int.class;
            case Type.FLOAT:
                return float.class;
            case Type.LONG:
                return long.class;
            case Type.DOUBLE:
                return double.class;
            case Type.ARRAY:
            case Type.OBJECT:
                return Class.forName(type.getClassName(), false, classLoader);
            default:
                throw new IllegalArgumentException("Invalid type: " + type);
        }
    }
}