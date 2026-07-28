package top.nontage.jniil.accessor.internal;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import top.nontage.jniil.accessor.AccessorFactory;
import top.nontage.jniil.annotations.Accessor;
import top.nontage.jniil.annotations.Invoker;
import top.nontage.jniil.utils.InjectionUtil;
import top.nontage.jniil.utils.UnsafeUtil;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class AccessorGenerator {
    private static final String MAGIC_ACCESSOR_PATH;
    private static final boolean IS_LEGACY_STRATEGY;
    private static final ClassLoader delegatingClassLoader;
    private static final AtomicLong classCounter = new AtomicLong(0);

    static {
        ClassLoader tempLoader;
        int javaVersion = getJavaVersion();
        if (javaVersion >= 22) {
            MAGIC_ACCESSOR_PATH = null;
            IS_LEGACY_STRATEGY = false;
        } else if (isClassPresent("sun.reflect.MagicAccessorImpl")) {
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
            tempLoader = (ClassLoader) loader;
        } else {
            tempLoader = null;
        }
        delegatingClassLoader = tempLoader;
    }

    @SuppressWarnings("unchecked")
    public static <T> T registerAccessor(Object instance, Class<T> accessorInterface) throws Throwable {
        if (IS_LEGACY_STRATEGY) {
            if (AccessorInitializer.getAccessorRegistry() == null) {
                AccessorInitializer.init();
            }

            Class<?> targetClass = instance.getClass();
            GenerateClassData data = generateBytecodeV8(targetClass, accessorInterface);
            Class<?> accessorImpl = InjectionUtil.unsafeInjectClass(delegatingClassLoader, data.name, data.bytes);
            Class<?> registry = AccessorInitializer.getAccessorRegistry();
            registry.getDeclaredMethod("register", String.class, Class.class).invoke(null, data.name, accessorImpl);
            return (T) UnsafeUtil.forceNewInstance(accessorImpl, new Class[]{targetClass}, instance);
        } else {
            Class<?> targetClass = instance.getClass();
            GenerateClassData data = generateBytecodeV22(targetClass, accessorInterface);
            Class<?> accessorImpl = defineHiddenClassIfAvailable(targetClass, data.bytes);
            Object accessorInstance = UnsafeUtil.forceNewInstance(accessorImpl, new Class[]{targetClass}, instance);
            return (T) accessorInstance;
        }
    }

    private static Class<?> defineHiddenClassIfAvailable(Class<?> targetClass, byte[] bytes) throws Throwable {
        try {
            MethodHandles.Lookup lookup = (MethodHandles.Lookup) UnsafeUtil.forceAllocateInstance(MethodHandles.Lookup.class);
            UnsafeUtil.forceSetMH(lookup, "lookupClass", Class.class, targetClass);
            UnsafeUtil.forceSetMH(lookup, "allowedModes", int.class, -1);
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
                    lookup, bytes, true, options
            );
            return hiddenLookup.lookupClass();
        } catch (NoSuchMethodException e) {
            throw new UnsupportedOperationException("Hidden class not supported in this JVM", e);
        } catch (IllegalAccessException e) {
            throw new UnsupportedOperationException(
                    "Cannot access " + targetClass.getName() +
                            " for hidden class definition. Add 'opens' directive if using modules.", e);
        }
    }

    private static GenerateClassData generateBytecodeV22(Class<?> targetClass, Class<?> accessorInterface) {
        try {
            String targetPackage = targetClass.getPackage().getName();
            String targetClassName = targetPackage + "." + accessorInterface.getSimpleName() + "$$ImplByJNIIL$$" + classCounter.incrementAndGet();
            String targetClassPath = targetClassName.replace('.', '/');
            String targetInternalName = targetClass.getName().replace('.', '/');

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            cw.visit(Opcodes.V22, Opcodes.ACC_PUBLIC, targetClassPath, null,
                    "java/lang/Object",
                    new String[]{accessorInterface.getName().replace('.', '/')});

            cw.visitField(Opcodes.ACC_PRIVATE, "target", "L" + targetInternalName + ";", null, null);

            generateConstructorV22(cw, targetClassPath, targetInternalName);

            for (Method method : accessorInterface.getDeclaredMethods()) {
                if (method.isDefault()) continue;

                Accessor accessor = method.getAnnotation(Accessor.class);
                Invoker invoker = method.getAnnotation(Invoker.class);

                if (accessor != null) {
                    generateAccessorMethodV22(cw, targetClassPath, targetInternalName, targetClass, method, accessor);
                } else if (invoker != null) {
                    generateInvokerMethodV22(cw, targetClassPath, targetInternalName, targetClass, method, invoker);
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

    private static void generateConstructorV22(ClassWriter cw, String targetClassPath, String targetInternalName) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(L" + targetInternalName + ";)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitFieldInsn(Opcodes.PUTFIELD, targetClassPath, "target", "L" + targetInternalName + ";");
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(2, 2);
        mv.visitEnd();
    }

    private static void generateAccessorMethodV22(ClassWriter cw, String targetClassPath, String targetInternalName, Class<?> targetClass, Method method, Accessor accessor) {
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
        boolean targetIsStatic = Modifier.isStatic(targetField.getModifiers());

        if (accessor.isStatic() != targetIsStatic) {
            String expected = accessor.isStatic() ? "static" : "instance";
            String actual = targetIsStatic ? "static" : "instance";
            throw new RuntimeException("Accessor mismatch: annotation declares " + expected +
                    " but field '" + fieldName + "' is " + actual);
        }

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, method.getName(),
                Type.getMethodDescriptor(method), null, getExceptions(method));
        mv.visitCode();

        if (targetIsStatic) {
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
            mv.visitFieldInsn(Opcodes.GETFIELD, targetClassPath, "target", "L" + targetInternalName + ";");

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

    private static void generateInvokerMethodV22(ClassWriter cw, String targetClassPath, String targetInternalName, Class<?> targetClass, Method method, Invoker invoker) throws NoSuchMethodException {
        String targetMethodName = invoker.value().isEmpty() ? inferInvokerMethodName(method) : invoker.value();
        boolean targetIsStatic = invoker.isStatic();

        Class<?>[] interfaceParams = method.getParameterTypes();
        String[] hints = invoker.hints();
        Class<?>[] resolvedParams = resolveParameterTypes(interfaceParams, hints, targetClass.getClassLoader());

        Method targetMethod = findTargetMethod(targetClass, targetMethodName, resolvedParams, targetIsStatic);
        Class<?>[] targetParamTypes = targetMethod.getParameterTypes();

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, method.getName(), Type.getMethodDescriptor(method), null, getExceptions(method));
        mv.visitCode();

        if (targetIsStatic) {
            for (int i = 0; i < interfaceParams.length; i++) {
                mv.visitVarInsn(loadOpcode(interfaceParams[i]), i + 1);
                if (interfaceParams[i] == Object.class && targetParamTypes[i] != Object.class) {
                    if (targetParamTypes[i].isPrimitive()) {
                        unboxPrimitive(mv, targetParamTypes[i]);
                    } else {
                        mv.visitTypeInsn(Opcodes.CHECKCAST, targetParamTypes[i].getName().replace('.', '/'));
                    }
                }
            }
            String methodDesc = Type.getMethodDescriptor(targetMethod);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, targetInternalName, targetMethodName, methodDesc, false);
        } else {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitFieldInsn(Opcodes.GETFIELD, targetClassPath, "target", "L" + targetInternalName + ";");
            for (int i = 0; i < interfaceParams.length; i++) {
                mv.visitVarInsn(loadOpcode(interfaceParams[i]), i + 1);
                if (interfaceParams[i] == Object.class && targetParamTypes[i] != Object.class) {
                    if (targetParamTypes[i].isPrimitive()) {
                        unboxPrimitive(mv, targetParamTypes[i]);
                    } else {
                        mv.visitTypeInsn(Opcodes.CHECKCAST, targetParamTypes[i].getName().replace('.', '/'));
                    }
                }
            }
            String methodDesc = Type.getMethodDescriptor(targetMethod);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, targetInternalName, targetMethodName, methodDesc, false);
        }

        if (method.getReturnType() != void.class) {
            mv.visitInsn(returnOpcode(method.getReturnType()));
        } else {
            mv.visitInsn(Opcodes.RETURN);
        }
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void unboxPrimitive(MethodVisitor mv, Class<?> primitiveType) {
        if (primitiveType == int.class) {
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
        } else if (primitiveType == long.class) {
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
        } else if (primitiveType == double.class) {
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
        } else if (primitiveType == float.class) {
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false);
        } else if (primitiveType == boolean.class) {
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
        } else if (primitiveType == byte.class) {
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Byte", "byteValue", "()B", false);
        } else if (primitiveType == char.class) {
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Character", "charValue", "()C", false);
        } else if (primitiveType == short.class) {
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Short", "shortValue", "()S", false);
        }
    }

    private static GenerateClassData generateBytecodeV8(Class<?> targetClass, Class<?> accessorInterface) {
        try {
            String targetClassName = accessorInterface.getName() + "$$ImplByJNIIL$$" + classCounter.incrementAndGet();
            String targetClassPath = targetClassName.replace('.', '/');
            String targetInternalName = targetClass.getName().replace('.', '/');

            Class<?> magicAccessorClass = Class.forName(MAGIC_ACCESSOR_PATH.replace('/', '.'));

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

            cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, targetClassPath, null,
                    magicAccessorClass.getName().replace('.', '/'),
                    new String[]{accessorInterface.getName().replace('.', '/')});

            cw.visitField(Opcodes.ACC_PRIVATE, "target", "L" + targetInternalName + ";", null, null);

            generateConstructorV8(cw, targetClassPath, targetInternalName, magicAccessorClass);

            for (Method method : accessorInterface.getDeclaredMethods()) {
                if (method.isDefault()) continue;

                Accessor accessor = method.getAnnotation(Accessor.class);
                Invoker invoker = method.getAnnotation(Invoker.class);

                if (accessor != null) {
                    generateAccessorMethodV8(cw, targetClassPath, targetInternalName, targetClass, method, accessor);
                } else if (invoker != null) {
                    generateInvokerMethodV8(cw, targetClassPath, targetInternalName, targetClass, method, invoker);
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

    private static void generateConstructorV8(ClassWriter cw, String targetClassPath, String targetInternalName, Class<?> magicAccessorClass) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(L" + targetInternalName + ";)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, magicAccessorClass.getName().replace('.', '/'), "<init>", "()V", false);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitFieldInsn(Opcodes.PUTFIELD, targetClassPath, "target", "L" + targetInternalName + ";");
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
        boolean targetIsStatic = Modifier.isStatic(targetField.getModifiers());

        if (accessor.isStatic() != targetIsStatic) {
            String expected = accessor.isStatic() ? "static" : "instance";
            String actual = targetIsStatic ? "static" : "instance";
            throw new RuntimeException("Accessor mismatch: annotation declares " + expected +
                    " but field '" + fieldName + "' is " + actual);
        }

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, method.getName(),
                Type.getMethodDescriptor(method), null, getExceptions(method));
        mv.visitCode();

        if (targetIsStatic) {
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
            mv.visitFieldInsn(Opcodes.GETFIELD, targetClassPath, "target", "L" + targetInternalName + ";");

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

    private static void generateInvokerMethodV8(ClassWriter cw, String targetClassPath, String targetInternalName, Class<?> targetClass, Method method, Invoker invoker) throws NoSuchMethodException {
        String targetMethodName = invoker.value().isEmpty() ? inferInvokerMethodName(method) : invoker.value();
        boolean targetIsStatic = invoker.isStatic();

        Class<?>[] interfaceParams = method.getParameterTypes();
        String[] hints = invoker.hints();
        Class<?>[] resolvedParams = resolveParameterTypes(interfaceParams, hints, targetClass.getClassLoader());

        Method targetMethod = findTargetMethod(targetClass, targetMethodName, resolvedParams, targetIsStatic);
        Class<?>[] targetParamTypes = targetMethod.getParameterTypes();

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, method.getName(),
                Type.getMethodDescriptor(method), null, getExceptions(method));
        mv.visitCode();

        if (targetIsStatic) {
            for (int i = 0; i < interfaceParams.length; i++) {
                mv.visitVarInsn(loadOpcode(interfaceParams[i]), i + 1);
                if (interfaceParams[i] == Object.class && targetParamTypes[i] != Object.class) {
                    if (targetParamTypes[i].isPrimitive()) {
                        unboxPrimitive(mv, targetParamTypes[i]);
                    } else {
                        mv.visitTypeInsn(Opcodes.CHECKCAST, targetParamTypes[i].getName().replace('.', '/'));
                    }
                }
            }
            String methodDesc = Type.getMethodDescriptor(targetMethod);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, targetInternalName, targetMethodName, methodDesc, false);
        } else {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitFieldInsn(Opcodes.GETFIELD, targetClassPath, "target", "L" + targetInternalName + ";");
            for (int i = 0; i < interfaceParams.length; i++) {
                mv.visitVarInsn(loadOpcode(interfaceParams[i]), i + 1);
                if (interfaceParams[i] == Object.class && targetParamTypes[i] != Object.class) {
                    if (targetParamTypes[i].isPrimitive()) {
                        unboxPrimitive(mv, targetParamTypes[i]);
                    } else {
                        mv.visitTypeInsn(Opcodes.CHECKCAST, targetParamTypes[i].getName().replace('.', '/'));
                    }
                }
            }
            String methodDesc = Type.getMethodDescriptor(targetMethod);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, targetInternalName, targetMethodName, methodDesc, false);
        }

        if (method.getReturnType() != void.class) {
            mv.visitInsn(returnOpcode(method.getReturnType()));
        } else {
            mv.visitInsn(Opcodes.RETURN);
        }
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static Class<?>[] resolveParameterTypes(Class<?>[] interfaceParams, String[] hints, ClassLoader classLoader) {
        int objectParamCount = 0;
        for (Class<?> param : interfaceParams) {
            if (param == Object.class) {
                objectParamCount++;
            }
        }
        if (hints.length > objectParamCount) {
            throw new IllegalArgumentException(
                    "Too many hints provided: expected at most " + objectParamCount +
                            " but got " + hints.length + " for @Invoker"
            );
        }
        Class<?>[] resolved = interfaceParams.clone();
        int hintIndex = 0;
        for (int i = 0; i < resolved.length; i++) {
            if (resolved[i] != Object.class) {
                continue;
            }
            if (hintIndex < hints.length) {
                String hint = hints[hintIndex];
                if (!hint.isEmpty()) {
                    resolved[i] = loadClass(hint, classLoader);
                }
                hintIndex++;
            }
        }
        return resolved;
    }

    private static Class<?> loadClass(String className, ClassLoader classLoader) {
        switch (className) {
            case "int":
                return int.class;
            case "long":
                return long.class;
            case "double":
                return double.class;
            case "float":
                return float.class;
            case "boolean":
                return boolean.class;
            case "byte":
                return byte.class;
            case "char":
                return char.class;
            case "short":
                return short.class;
            case "void":
                return void.class;
            default:
                try {
                    return Class.forName(className, false, classLoader);
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException("Cannot load class: " + className, e);
                }
        }
    }

    private static Method findTargetMethod(Class<?> targetClass, String methodName, Class<?>[] paramTypes, boolean requireStatic) throws NoSuchMethodException {
        Method[] methods = targetClass.getDeclaredMethods();

        List<Method> candidates = new ArrayList<>();
        for (Method m : methods) {
            if (Modifier.isStatic(m.getModifiers()) != requireStatic) {
                continue;
            }

            if (m.getName().equals(methodName)) {
                candidates.add(m);
            }
        }

        if (candidates.isEmpty()) {
            throw new NoSuchMethodException(
                    "Method '" + methodName + "' not found in class " + targetClass.getName() +
                            "\n  Available methods with name '" + methodName + "': none"
            );
        }

        List<Method> matchingCount = new ArrayList<>();
        for (Method m : candidates) {
            if (m.getParameterTypes().length == paramTypes.length) {
                matchingCount.add(m);
            }
        }

        if (matchingCount.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("Method '").append(methodName).append("' in class ").append(targetClass.getName())
                    .append(" has ").append(paramTypes.length).append(" parameter(s), but found:\n");
            for (Method m : candidates) {
                sb.append("  - ").append(methodName).append("(");
                Class<?>[] params = m.getParameterTypes();
                for (int i = 0; i < params.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(params[i].getName());
                }
                sb.append(") with ").append(params.length).append(" parameter(s)\n");
            }
            throw new NoSuchMethodException(sb.toString());
        }

        Method bestMatch = null;
        int bestScore = -1;

        for (Method m : matchingCount) {
            Class<?>[] targetParams = m.getParameterTypes();
            int score = calculateCompatibility(paramTypes, targetParams);
            if (score > bestScore) {
                bestScore = score;
                bestMatch = m;
            }
        }

        if (bestMatch == null) {
            StringBuilder sb = new StringBuilder();
            sb.append("Cannot find compatible method '").append(methodName).append("' in class ")
                    .append(targetClass.getName()).append(" with parameters: [");
            for (int i = 0; i < paramTypes.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(paramTypes[i].getName());
            }
            sb.append("]\n");
            sb.append("  Available methods with same name and parameter count:\n");
            for (Method m : matchingCount) {
                sb.append("  - ").append(methodName).append("(");
                Class<?>[] params = m.getParameterTypes();
                for (int i = 0; i < params.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(params[i].getName());
                }
                sb.append(")\n");
            }
            sb.append("\n  Hint: If you are using Object parameters, try adding hints to @Invoker:\n");
            sb.append("  Example: @Invoker(hints = {\"type1\", \"type2\"})");
            throw new NoSuchMethodException(sb.toString());
        }

        return bestMatch;
    }

    private static int calculateCompatibility(Class<?>[] from, Class<?>[] to) {
        int score = 0;
        for (int i = 0; i < from.length; i++) {
            if (from[i] == to[i]) {
                score += 100;
            } else if (from[i] == Object.class && !to[i].isPrimitive()) {
                score += 50;
            } else if (from[i] == Object.class && to[i].isPrimitive()) {
                score += 30;
            } else if (from[i].isPrimitive() && to[i].isPrimitive() && isPrimitiveCompatible(from[i], to[i])) {
                score += 80;
            } else if (to[i].isAssignableFrom(from[i])) {
                score += 70;
            } else if (canConvert(from[i], to[i])) {
                score += 40;
            } else {
                return -1;
            }
        }
        return score;
    }

    private static boolean canConvert(Class<?> from, Class<?> to) {
        if (from.isPrimitive() && to.isPrimitive()) {
            return isPrimitiveCompatible(from, to);
        }
        if (from.isPrimitive()) {
            return to == wrapPrimitive(from);
        }
        if (to.isPrimitive()) {
            return from == wrapPrimitive(to);
        }
        return false;
    }

    private static boolean isPrimitiveCompatible(Class<?> from, Class<?> to) {
        if (from == to) return true;
        if (from == byte.class)
            return to == short.class || to == int.class || to == long.class || to == float.class || to == double.class;
        boolean isNumber = to == int.class || to == long.class || to == float.class || to == double.class;
        if (from == short.class) return isNumber;
        if (from == char.class) return isNumber;
        if (from == int.class) return to == long.class || to == float.class || to == double.class;
        if (from == long.class) return to == float.class || to == double.class;
        if (from == float.class) return to == double.class;
        return false;
    }

    private static Class<?> wrapPrimitive(Class<?> type) {
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == void.class) return Void.class;
        return type;
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

    private static int getJavaVersion() {
        String version = System.getProperty("java.version");
        if (version.startsWith("1.")) {
            return Integer.parseInt(version.substring(2, 3));
        }
        int dotIndex = version.indexOf('.');
        if (dotIndex != -1) {
            return Integer.parseInt(version.substring(0, dotIndex));
        }
        return Integer.parseInt(version);
    }

    protected static class GenerateClassData {
        String name;
        byte[] bytes;
    }
}