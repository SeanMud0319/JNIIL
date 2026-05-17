package top.nontage.jniil.accessor;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import top.nontage.jniil.annotations.Accessor;
import top.nontage.jniil.annotations.Invoker;
import top.nontage.jniil.utils.InjectionUtil;
import top.nontage.jniil.utils.UnsafeUtil;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AccessorFactory {

    private static final String MAGIC_ACCESSOR_PATH;
    private static final boolean IS_LEGACY_STRATEGY;
    private static final Map<String, Object> accessorCache = new ConcurrentHashMap<>();
    private static final ClassLoader delegatingClassLoader;

    static {
        if (isClassPresent("sun.reflect.MagicAccessorImpl")) {
            MAGIC_ACCESSOR_PATH = "sun/reflect/MagicAccessorImpl";
            IS_LEGACY_STRATEGY = true;
        } else if (isClassPresent("jdk.internal.reflect.MagicAccessorImpl")) {
            MAGIC_ACCESSOR_PATH = "jdk/internal/reflect/MagicAccessorImpl";
            IS_LEGACY_STRATEGY = true;
        } else {
            MAGIC_ACCESSOR_PATH = null;
            IS_LEGACY_STRATEGY = false;
        }

        if (IS_LEGACY_STRATEGY) {
            Class<?> c;
            try {
                c = Class.forName("sun.reflect.DelegatingClassLoader");
            } catch (ClassNotFoundException e) {
                try {
                    c = Class.forName("jdk.internal.reflect.DelegatingClassLoader");
                } catch (ClassNotFoundException ex) {
                    throw new RuntimeException(ex);
                }
            }
            Object loader = UnsafeUtil.forceNewInstance(c,
                    new Class[]{ClassLoader.class},
                    AccessorFactory.class.getClassLoader()
            );
            delegatingClassLoader = (ClassLoader) loader;
        } else {
            delegatingClassLoader = null;
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T getAccessor(Object instance, Class<T> accessorInterface) {
        Class<?> targetClass = instance.getClass();
        String cacheKey = targetClass.getName() + "#" + accessorInterface.getName();
        return (T) accessorCache.computeIfAbsent(cacheKey, key -> {
            try {
                return registerAccessor(instance, accessorInterface);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }

        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T registerAccessor(Object instance, Class<T> accessorInterface) throws Throwable {
        if (IS_LEGACY_STRATEGY) {
            if (AccessorInitializer.getAccessorRegistry() == null) {
                AccessorInitializer.init();
            }

            Class<?> targetClass = instance.getClass();
            GenerateClassData data = generateBytecodeV8(targetClass, accessorInterface);
            Class<?> accessorImpl = InjectionUtil.unsafeInjectClass(delegatingClassLoader, data.name, data.bytes);
            Class<?> registry = AccessorInitializer.getAccessorRegistry();
            registry.getDeclaredMethod("register", String.class, Class.class).invoke(null, data.name, accessorImpl);
            return (T) UnsafeUtil.forceNewInstance(accessorImpl, new Class[]{Object.class}, instance);
        } else {
            Class<?> targetClass = instance.getClass();
            GenerateClassData data = generateBytecodeV22(targetClass, accessorInterface);
            Class<?> accessorImpl = defineHiddenClassIfAvailable(targetClass, data.bytes, true);
            Object accessorInstance = UnsafeUtil.forceNewInstance(accessorImpl, new Class[]{Object.class}, instance);
            return (T) accessorInstance;
        }
    }

    private static Class<?> defineHiddenClassIfAvailable(Class<?> targetClass, byte[] bytes, boolean initialize) throws Throwable {
        try {
            Method privateLookupIn = MethodHandles.class.getDeclaredMethod("privateLookupIn", Class.class, MethodHandles.Lookup.class);
            privateLookupIn.setAccessible(true);
            MethodHandles.Lookup lookup = (MethodHandles.Lookup) privateLookupIn.invoke(null, targetClass, MethodHandles.lookup());
            Class<?> classOptionClass = Class.forName("java.lang.invoke.MethodHandles$Lookup$ClassOption");
            Object nestmateOption = null;
            for (Object constant : classOptionClass.getEnumConstants()) {
                if (constant.toString().equals("NESTMATE")) {
                    nestmateOption = constant;
                    break;
                }
            }

            Object options = Array.newInstance(classOptionClass, 1);
            Array.set(options, 0, nestmateOption);
            Method defineHiddenClass = null;
            for (Method m : MethodHandles.Lookup.class.getDeclaredMethods()) {
                if (m.getName().equals("defineHiddenClass") && m.getParameterCount() == 3) {
                    defineHiddenClass = m;
                    break;
                }
            }

            defineHiddenClass.setAccessible(true);
            MethodHandles.Lookup hiddenLookup = (MethodHandles.Lookup) defineHiddenClass.invoke(
                    lookup, bytes, initialize, options
            );
            return hiddenLookup.lookupClass();
        } catch (NoSuchMethodException e) {
            throw new UnsupportedOperationException("Hidden class not supported in this JVM", e);
        }
    }

    private static GenerateClassData generateBytecodeV22(Class<?> targetClass, Class<?> accessorInterface) {
        try {
            String targetClassName = accessorInterface.getName() + "$$ImplByJNIIL$$" + Math.abs(accessorInterface.hashCode());
            String targetClassPath = targetClassName.replace('.', '/');
            String targetInternalName = targetClass.getName().replace('.', '/');

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            cw.visit(Opcodes.V22, Opcodes.ACC_PUBLIC, targetClassPath, null,
                    "java/lang/Object",
                    new String[]{accessorInterface.getName().replace('.', '/')});

            cw.visitField(Opcodes.ACC_PRIVATE, "target", "Ljava/lang/Object;", null, null);

            generateConstructorV22(cw, targetClassPath);

            for (Method method : accessorInterface.getDeclaredMethods()) {
                if (method.isDefault()) continue;

                Accessor accessor = method.getAnnotation(Accessor.class);
                Invoker invoker = method.getAnnotation(Invoker.class);

                if (accessor != null) {
                    generateAccessorMethodV22(cw, targetClassPath, targetInternalName, targetClass, method, accessor);
                } else if (invoker != null) {
                    generateInvokerMethodV22(cw, targetClassPath, targetInternalName, method, invoker);
                } else {
                    throw new RuntimeException("Method '" + method.getName() + "' in " + accessorInterface.getSimpleName() + " must be annotated with @Accessor or @Invoker");
                }
            }

            cw.visitEnd();

            GenerateClassData data = new GenerateClassData();
            data.name = targetClassName;
            data.bytes = cw.toByteArray();
            return data;

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate bytecode for: " + accessorInterface.getName(), e);
        }
    }

    private static void generateConstructorV22(ClassWriter cw, String targetClassPath) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/lang/Object;)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitFieldInsn(Opcodes.PUTFIELD, targetClassPath, "target", "Ljava/lang/Object;");
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(2, 2);
        mv.visitEnd();
    }

    private static void generateAccessorMethodV22(ClassWriter cw, String targetClassPath,
                                                  String targetInternalName, Class<?> targetClass,
                                                  Method method, Accessor accessor) {
        String fieldName = accessor.value().isEmpty() ? inferFieldName(method) : accessor.value();
        boolean isSetter = method.getParameterCount() == 1 && method.getReturnType() == void.class;
        boolean isGetter = method.getParameterCount() == 0 && method.getReturnType() != void.class;

        if (!isSetter && !isGetter) {
            throw new RuntimeException("Accessor method must be getter or setter: " + method);
        }

        Field targetField;
        try {
            targetField = targetClass.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Field not found: " + fieldName + " in " + targetClass.getName(), e);
        }

        String fieldDesc = Type.getDescriptor(targetField.getType());
        boolean isStatic = Modifier.isStatic(targetField.getModifiers());

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, method.getName(),
                Type.getMethodDescriptor(method), null, getExceptions(method));
        mv.visitCode();

        if (isStatic) {
            if (isSetter) {
                mv.visitVarInsn(loadOpcode(method.getParameterTypes()[0]), 1);
                mv.visitFieldInsn(Opcodes.PUTSTATIC, targetInternalName, fieldName, fieldDesc);
                mv.visitInsn(Opcodes.RETURN);
            } else {
                mv.visitFieldInsn(Opcodes.GETSTATIC, targetInternalName, fieldName, fieldDesc);
                mv.visitInsn(returnOpcode(method.getReturnType()));
            }
        } else {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitFieldInsn(Opcodes.GETFIELD, targetClassPath, "target", "Ljava/lang/Object;");
            mv.visitTypeInsn(Opcodes.CHECKCAST, targetInternalName);

            if (isSetter) {
                mv.visitVarInsn(loadOpcode(method.getParameterTypes()[0]), 1);
                mv.visitFieldInsn(Opcodes.PUTFIELD, targetInternalName, fieldName, fieldDesc);
                mv.visitInsn(Opcodes.RETURN);
            } else {
                mv.visitFieldInsn(Opcodes.GETFIELD, targetInternalName, fieldName, fieldDesc);
                mv.visitInsn(returnOpcode(method.getReturnType()));
            }
        }

        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void generateInvokerMethodV22(ClassWriter cw, String targetClassPath,
                                                 String targetInternalName, Method method, Invoker invoker) {
        String targetMethodName;

        if (invoker.value().isEmpty()) {
            targetMethodName = inferInvokerMethodName(method);
        } else {
            targetMethodName = invoker.value();
        }

        boolean isStatic = targetMethodName.startsWith("static");
        String actualMethodName = isStatic ? targetMethodName.substring(6) : targetMethodName;

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, method.getName(),
                Type.getMethodDescriptor(method), null, getExceptions(method));
        mv.visitCode();

        if (isStatic) {
            Class<?>[] paramTypes = method.getParameterTypes();
            for (int i = 0; i < paramTypes.length; i++) {
                mv.visitVarInsn(loadOpcode(paramTypes[i]), i + 1);
            }
            String methodDesc = Type.getMethodDescriptor(method);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, targetInternalName, actualMethodName, methodDesc, false);
        } else {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitFieldInsn(Opcodes.GETFIELD, targetClassPath, "target", "Ljava/lang/Object;");
            mv.visitTypeInsn(Opcodes.CHECKCAST, targetInternalName);
            Class<?>[] paramTypes = method.getParameterTypes();
            for (int i = 0; i < paramTypes.length; i++) {
                mv.visitVarInsn(loadOpcode(paramTypes[i]), i + 1);
            }
            String methodDesc = Type.getMethodDescriptor(method);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, targetInternalName, actualMethodName, methodDesc, false);
        }

        if (method.getReturnType() != void.class) {
            mv.visitInsn(returnOpcode(method.getReturnType()));
        } else {
            mv.visitInsn(Opcodes.RETURN);
        }
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static GenerateClassData generateBytecodeV8(Class<?> targetClass, Class<?> accessorInterface) {
        try {
            String targetClassName = accessorInterface.getName() + "$$ImplByJNIIL$$" + Math.abs(accessorInterface.hashCode());
            String targetClassPath = targetClassName.replace('.', '/');
            String targetInternalName = targetClass.getName().replace('.', '/');

            Class<?> magicAccessorClass = Class.forName(MAGIC_ACCESSOR_PATH.replace('/', '.'));

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

            cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, targetClassPath, null,
                    magicAccessorClass.getName().replace('.', '/'),
                    new String[]{accessorInterface.getName().replace('.', '/')});

            cw.visitField(Opcodes.ACC_PRIVATE, "target", "Ljava/lang/Object;", null, null);

            generateConstructorV8(cw, targetClassPath, magicAccessorClass);

            for (Method method : accessorInterface.getDeclaredMethods()) {
                if (method.isDefault()) continue;

                Accessor accessor = method.getAnnotation(Accessor.class);
                Invoker invoker = method.getAnnotation(Invoker.class);

                if (accessor != null) {
                    generateAccessorMethodV8(cw, targetClassPath, targetInternalName, targetClass, method, accessor);
                } else if (invoker != null) {
                    generateInvokerMethodV8(cw, targetClassPath, targetInternalName, method, invoker);
                } else {
                    throw new RuntimeException("Method '" + method.getName() + "' in " + accessorInterface.getSimpleName() + " must be annotated with @Accessor or @Invoker");
                }
            }

            cw.visitEnd();

            GenerateClassData data = new GenerateClassData();
            data.name = targetClassName;
            data.bytes = cw.toByteArray();
            return data;

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate bytecode for: " + accessorInterface.getName(), e);
        }
    }

    private static void generateConstructorV8(ClassWriter cw, String targetClassPath, Class<?> magicAccessorClass) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/lang/Object;)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, magicAccessorClass.getName().replace('.', '/'), "<init>", "()V", false);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitFieldInsn(Opcodes.PUTFIELD, targetClassPath, "target", "Ljava/lang/Object;");
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(2, 2);
        mv.visitEnd();
    }

    private static void generateAccessorMethodV8(ClassWriter cw, String targetClassPath, String targetInternalName, Class<?> targetClass, Method method, Accessor accessor) {
        String fieldName = accessor.value().isEmpty() ? inferFieldName(method) : accessor.value();
        boolean isSetter = method.getParameterCount() == 1 && method.getReturnType() == void.class;
        boolean isGetter = method.getParameterCount() == 0 && method.getReturnType() != void.class;

        if (!isSetter && !isGetter) {
            throw new RuntimeException("Accessor method must be getter or setter: " + method);
        }

        Field targetField;
        try {
            targetField = targetClass.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Field not found: " + fieldName + " in " + targetClass.getName(), e);
        }

        String fieldDesc = Type.getDescriptor(targetField.getType());
        boolean isStatic = Modifier.isStatic(targetField.getModifiers());

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, method.getName(),
                Type.getMethodDescriptor(method), null, getExceptions(method));
        mv.visitCode();

        if (isStatic) {
            if (isSetter) {
                mv.visitVarInsn(loadOpcode(method.getParameterTypes()[0]), 1);
                mv.visitFieldInsn(Opcodes.PUTSTATIC, targetInternalName, fieldName, fieldDesc);
                mv.visitInsn(Opcodes.RETURN);
            } else {
                mv.visitFieldInsn(Opcodes.GETSTATIC, targetInternalName, fieldName, fieldDesc);
                mv.visitInsn(returnOpcode(method.getReturnType()));
            }
        } else {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitFieldInsn(Opcodes.GETFIELD, targetClassPath, "target", "Ljava/lang/Object;");
            mv.visitTypeInsn(Opcodes.CHECKCAST, targetInternalName);

            if (isSetter) {
                mv.visitVarInsn(loadOpcode(method.getParameterTypes()[0]), 1);
                mv.visitFieldInsn(Opcodes.PUTFIELD, targetInternalName, fieldName, fieldDesc);
                mv.visitInsn(Opcodes.RETURN);
            } else {
                mv.visitFieldInsn(Opcodes.GETFIELD, targetInternalName, fieldName, fieldDesc);
                mv.visitInsn(returnOpcode(method.getReturnType()));
            }
        }

        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void generateInvokerMethodV8(ClassWriter cw, String targetClassPath, String targetInternalName, Method method, Invoker invoker) {
        String targetMethodName;

        if (invoker.value().isEmpty()) {
            targetMethodName = inferInvokerMethodName(method);
        } else {
            targetMethodName = invoker.value();
        }

        boolean isStatic = targetMethodName.startsWith("static");
        String actualMethodName = isStatic ? targetMethodName.substring(6) : targetMethodName;

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, method.getName(),
                Type.getMethodDescriptor(method), null, getExceptions(method));
        mv.visitCode();

        if (isStatic) {
            Class<?>[] paramTypes = method.getParameterTypes();
            for (int i = 0; i < paramTypes.length; i++) {
                mv.visitVarInsn(loadOpcode(paramTypes[i]), i + 1);
            }

            String methodDesc = Type.getMethodDescriptor(method);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, targetInternalName, actualMethodName, methodDesc, false);
        } else {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitFieldInsn(Opcodes.GETFIELD, targetClassPath, "target", "Ljava/lang/Object;");
            mv.visitTypeInsn(Opcodes.CHECKCAST, targetInternalName);

            Class<?>[] paramTypes = method.getParameterTypes();
            for (int i = 0; i < paramTypes.length; i++) {
                mv.visitVarInsn(loadOpcode(paramTypes[i]), i + 1);
            }

            String methodDesc = Type.getMethodDescriptor(method);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, targetInternalName, actualMethodName, methodDesc, false);
        }

        if (method.getReturnType() != void.class) {
            mv.visitInsn(returnOpcode(method.getReturnType()));
        } else {
            mv.visitInsn(Opcodes.RETURN);
        }

        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static String inferFieldName(Method method) {
        String methodName = method.getName();
        if (method.getParameterCount() == 1 && method.getReturnType() == void.class) {
            if (methodName.startsWith("set") && methodName.length() > 3) {
                return decapitalize(methodName.substring(3));
            }
        } else if (method.getParameterCount() == 0 && method.getReturnType() != void.class) {
            if (methodName.startsWith("get") && methodName.length() > 3) {
                return decapitalize(methodName.substring(3));
            }
            if (methodName.startsWith("is") && methodName.length() > 2) {
                return decapitalize(methodName.substring(2));
            }
        }
        return decapitalize(methodName);
    }

    private static String inferInvokerMethodName(Method method) {
        String methodName = method.getName();
        if (!methodName.startsWith("call") && !methodName.startsWith("invoke")) {
            return methodName;
        }
        if (methodName.startsWith("callStatic") || methodName.startsWith("invokeStatic")) {
            String rest = methodName.substring(10);
            return "static" + decapitalize(rest);
        }
        String rest = methodName.substring(4);
        return decapitalize(rest);
    }

    private static int loadOpcode(Class<?> type) {
        if (type == int.class || type == byte.class || type == short.class || type == char.class || type == boolean.class) {
            return Opcodes.ILOAD;
        } else if (type == long.class) {
            return Opcodes.LLOAD;
        } else if (type == float.class) {
            return Opcodes.FLOAD;
        } else if (type == double.class) {
            return Opcodes.DLOAD;
        } else {
            return Opcodes.ALOAD;
        }
    }

    private static int returnOpcode(Class<?> type) {
        if (type == int.class || type == byte.class || type == short.class || type == char.class || type == boolean.class) {
            return Opcodes.IRETURN;
        } else if (type == long.class) {
            return Opcodes.LRETURN;
        } else if (type == float.class) {
            return Opcodes.FRETURN;
        } else if (type == double.class) {
            return Opcodes.DRETURN;
        } else if (type == void.class) {
            return Opcodes.RETURN;
        } else {
            return Opcodes.ARETURN;
        }
    }

    private static String[] getExceptions(Method method) {
        Class<?>[] exceptionTypes = method.getExceptionTypes();
        String[] exceptions = new String[exceptionTypes.length];
        for (int i = 0; i < exceptionTypes.length; i++) {
            exceptions[i] = exceptionTypes[i].getName().replace('.', '/');
        }
        return exceptions;
    }

    private static String decapitalize(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        if (name.length() > 1 && Character.isUpperCase(name.charAt(1)) && Character.isUpperCase(name.charAt(0))) {
            return name;
        }
        char[] chars = name.toCharArray();
        chars[0] = Character.toLowerCase(chars[0]);
        return new String(chars);
    }

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, null);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private static class GenerateClassData {
        String name;
        byte[] bytes;
    }
}