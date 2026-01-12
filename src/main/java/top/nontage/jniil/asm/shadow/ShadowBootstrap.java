package top.nontage.jniil.asm.shadow;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import sun.misc.Unsafe;
import top.nontage.jniil.asm.shadow.metadata.ShadowContextHolder;

import java.lang.invoke.*;
import java.lang.reflect.Field;
import java.util.function.Supplier;

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

        MethodHandle targetHandle;
        if (isMethod) {
            MethodType targetType = MethodType.fromMethodDescriptorString(targetDesc, loader);
            targetHandle = isStatic
                    ? privilegedLookup.findStatic(targetClass, targetName, targetType)
                    : privilegedLookup.findVirtual(targetClass, targetName, targetType);
        } else { // Field access
            Class<?> fieldType = getJavaClass(Type.getType(targetDesc), loader);
            targetHandle = isStatic
                    ? (isGet ? privilegedLookup.findStaticGetter(targetClass, targetName, fieldType) : privilegedLookup.findStaticSetter(targetClass, targetName, fieldType))
                    : (isGet ? privilegedLookup.findGetter(targetClass, targetName, fieldType) : privilegedLookup.findSetter(targetClass, targetName, fieldType));
        }

        MethodHandle invoker;
        if (isStatic) {
            invoker = targetHandle;
        } else {
            MethodHandle getInstanceHandle = IMPL_LOOKUP.findStatic(ShadowBootstrap.class, "getInstance", MethodType.methodType(Object.class, String.class))
                    .bindTo(shadowOwner);

            Class<?> instanceType = targetHandle.type().parameterType(0);
            MethodHandle castedGetInstanceHandle = MethodHandles.explicitCastArguments(getInstanceHandle, MethodType.methodType(instanceType));

            MethodHandle foldedInvoker = MethodHandles.foldArguments(targetHandle, castedGetInstanceHandle);

            Class<?> shadowClass = callSiteType.parameterType(0);
            invoker = MethodHandles.dropArguments(foldedInvoker, 0, shadowClass);
        }

        return new ConstantCallSite(invoker.asType(callSiteType));
    }

    public static Object getInstance(String shadowOwner) {
        Supplier<Object> supplier = ShadowContextHolder.INSTANCE.getBoundInstanceSupplier(shadowOwner);
        if (supplier == null) {
            throw new IllegalStateException("No bound instance supplier for " + shadowOwner);
        }
        Object instance = supplier.get();
        if (instance == null) {
            throw new IllegalStateException("Instance supplier for " + shadowOwner + " returned null");
        }
        return instance;
    }

    private static Class<?> getJavaClass(Type type, ClassLoader classLoader) throws ClassNotFoundException {
        switch (type.getSort()) {
            case Type.BOOLEAN: return boolean.class;
            case Type.CHAR: return char.class;
            case Type.BYTE: return byte.class;
            case Type.SHORT: return short.class;
            case Type.INT: return int.class;
            case Type.FLOAT: return float.class;
            case Type.LONG: return long.class;
            case Type.DOUBLE: return double.class;
            case Type.ARRAY:
            case Type.OBJECT: return Class.forName(type.getClassName(), false, classLoader);
            default: throw new IllegalArgumentException("Invalid type: " + type);
        }
    }
}