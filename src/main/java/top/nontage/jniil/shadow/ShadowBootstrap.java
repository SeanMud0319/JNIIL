package top.nontage.jniil.shadow;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import sun.misc.Unsafe;
import top.nontage.jniil.shadow.internal.metadata.ShadowContextHolder;
import top.nontage.jniil.utils.UnsafeUtil;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.function.Supplier;

public class ShadowBootstrap {

    private static final MethodHandles.Lookup IMPL_LOOKUP = UnsafeUtil.IMPL_LOOKUP;
    private static final Unsafe UNSAFE = UnsafeUtil.unsafe;

    public static CallSite bootstrap(
            MethodHandles.Lookup lookup,
            String name,
            MethodType callSiteType,
            String shadowOwner,
            String targetOwner,
            String targetName,
            String targetDesc,
            int opcode,
            int isMutableInt
    ) throws Throwable {
        boolean isMutable = isMutableInt != 0;
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

            if (isPut && isMutable) {
                Field field = targetClass.getDeclaredField(targetName);
                Object staticFieldBase = isStatic ? UNSAFE.staticFieldBase(field) : null;
                long fieldOffset = isStatic ? UNSAFE.staticFieldOffset(field) : UNSAFE.objectFieldOffset(field);
                targetHandle = createUnsafeSetter(fieldType, staticFieldBase, fieldOffset, isStatic);
            } else {
                targetHandle = isStatic
                        ? (isGet ? privilegedLookup.findStaticGetter(targetClass, targetName, fieldType) : privilegedLookup.findStaticSetter(targetClass, targetName, fieldType))
                        : (isGet ? privilegedLookup.findGetter(targetClass, targetName, fieldType) : privilegedLookup.findSetter(targetClass, targetName, fieldType));
            }
        }

        MethodHandle invoker;
        if (isStatic) {
            invoker = targetHandle;
        } else {
            MethodHandle getInstanceHandle = IMPL_LOOKUP.findStatic(
                            ShadowBootstrap.class,
                            "getInstance",
                            MethodType.methodType(Object.class, String.class, String.class)
                    )
                    .bindTo(shadowOwner)
                    .bindTo(targetOwner);

            Class<?> instanceType = targetHandle.type().parameterType(0);
            MethodHandle castedGetInstanceHandle = MethodHandles.explicitCastArguments(getInstanceHandle, MethodType.methodType(instanceType));

            MethodHandle foldedInvoker = MethodHandles.foldArguments(targetHandle, castedGetInstanceHandle);

            Class<?> shadowClass = callSiteType.parameterType(0);
            invoker = MethodHandles.dropArguments(foldedInvoker, 0, shadowClass);
        }

        return new ConstantCallSite(invoker.asType(callSiteType));
    }

    private static MethodHandle createUnsafeSetter(Class<?> fieldType, Object staticFieldBase, long fieldOffset, boolean isStatic) throws NoSuchMethodException, IllegalAccessException {
        String setterName;
        Class<?> unsafeParamType = fieldType;

        if (fieldType.isPrimitive()) {
            String capitalized = capitalize(fieldType.getName());
            setterName = "put" + capitalized;
        } else {
            setterName = "putObject";
            unsafeParamType = Object.class;
        }

        MethodHandle unsafeSetter;
        if (isStatic) {
            // Unsafe static setter: putX(Object base, long offset, ValueType value)
            unsafeSetter = IMPL_LOOKUP.findVirtual(Unsafe.class, setterName, MethodType.methodType(void.class, Object.class, long.class, unsafeParamType))
                    .bindTo(UNSAFE)
                    .bindTo(staticFieldBase)
                    .bindTo(fieldOffset); // (ValueType) -> void
        } else {
            // Unsafe instance setter: putX(Object instance, long offset, ValueType value)
            unsafeSetter = IMPL_LOOKUP.findVirtual(Unsafe.class, setterName, MethodType.methodType(void.class, Object.class, long.class, unsafeParamType))
                    .bindTo(UNSAFE); // (Object instance, long offset, ValueType value) -> void
            // Bind offset and permute to match (Instance, Value)
            unsafeSetter = MethodHandles.insertArguments(unsafeSetter, 1, fieldOffset); // (Object instance, ValueType value) -> void
        }
        return unsafeSetter.asType(MethodType.methodType(void.class, isStatic ? new Class[0] : new Class[]{Object.class}).appendParameterTypes(fieldType));
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    public static Object getInstance(String shadowOwner, String targetOwner) {
        Supplier<Object> supplier = ShadowContextHolder.INSTANCE.getBoundInstanceSupplier(shadowOwner, targetOwner);
        if (supplier == null) {
            throw new IllegalStateException("No bound instance supplier for shadow '" + shadowOwner + "' targeting '" + targetOwner + "'");
        }
        Object instance = supplier.get();
        if (instance == null) {
            throw new IllegalStateException("Instance supplier for shadow '" + shadowOwner + "' targeting '" + targetOwner + "' returned null");
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
            case Type.VOID:
                return void.class;
            case Type.ARRAY:
            case Type.OBJECT:
                return Class.forName(type.getInternalName().replace('/', '.'), false, classLoader);
            default:
                throw new IllegalArgumentException("Invalid type: " + type);
        }
    }
}