package top.nontage.jniil.utils;

import sun.misc.Unsafe;
import top.nontage.auth.library.annotation.Protect;
import top.nontage.jniil.JNIIL;
import top.nontage.jniil.verify.BytecodeVerifier;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

@Protect
public class InjectionUtil {
    private static final Instrumentation inst = JNIIL.getInstrumentation();

    private InjectionUtil() {

    }

    public static Class<?> findClassAcrossClassLoaders(String className) throws ClassNotFoundException {
        for (Class<?> clazz : inst.getAllLoadedClasses()) {
            ClassLoader loader = clazz.getClassLoader();

            if (BytecodeVerifier.VERIFIER_LOADERS.contains(loader)) {
                continue;
            }

            if (clazz.getName().equals(className)) {
                return clazz;
            }
        }
        throw new ClassNotFoundException("[JNIIL] Class not found: " + className);
    }

    public static ClassLoader findClassLoaderByThread(String threadName) {
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.getName().equals(threadName)) {
                return thread.getContextClassLoader();
            }
        }
        throw new RuntimeException("Thread not found: " + threadName);
    }

    public static void printAllClassLoader() {
        Set<ClassLoader> loaders = new HashSet<>();
        for (Class<?> clazz : inst.getAllLoadedClasses()) {
            ClassLoader loader = clazz.getClassLoader();
            loaders.add(loader);
        }
        System.out.println("All ClassLoaders:");
        for (ClassLoader loader : loaders) {
            System.out.println(loader);
        }
    }

    public static Class<?> forceLoadClass(String className, ClassLoader loader) {
        try {
            return Class.forName(className, false, loader);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    // It will return the original bytecode of the class, not the modified one.
    public static byte[] getOriginalClassBytes(Class<?> clazz) throws IOException {
        String path = clazz.getName().replace('.', '/') + ".class";
        try (InputStream in = clazz.getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new IOException("Class not found: " + path);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[4096];
            int nRead;
            while ((nRead = in.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            buffer.flush();
            return buffer.toByteArray();
        }
    }

    public static byte[] getOriginalClassBytes(String targetClassName) throws Exception {
        final byte[][] result = new byte[1][];
        ClassFileTransformer transformer = (loader, className, classBeingRedefined, protectionDomain, classfileBuffer) -> {
            if (className.equals(targetClassName.replace('.', '/'))) {
                result[0] = classfileBuffer;
            }
            return null;
        };
        inst.addTransformer(transformer, true);
        for (Class<?> clazz : inst.getAllLoadedClasses()) {
            if (clazz.getName().equals(targetClassName)) {
                inst.retransformClasses(clazz);
                break;
            }
        }
        inst.removeTransformer(transformer);
        return result[0];
    }

    public static void injectClass(ClassLoader loader, String name, byte[] bytecode) throws Exception {
        Method defineClass = ClassLoader.class.getDeclaredMethod(
                "defineClass", String.class, byte[].class, int.class, int.class);
        defineClass.setAccessible(true);
        defineClass.invoke(loader, name, bytecode, 0, bytecode.length);
    }

    @FunctionalInterface
    public interface DefineClassInterface {
        Class<?> defineClass(ClassLoader loader, String name, byte[] b, int off, int len) throws Throwable;
    }

    public static Class<?> unsafeInjectClass(ClassLoader loader, String name, byte[] bytecode) throws Throwable {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);

        Field implLookupField = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
        MethodHandles.Lookup lookup = (MethodHandles.Lookup)
                unsafe.getObject(
                        unsafe.staticFieldBase(implLookupField),
                        unsafe.staticFieldOffset(implLookupField)
                );

        Method defineClassMethod = ClassLoader.class.getDeclaredMethod(
                "defineClass", String.class, byte[].class, int.class, int.class
        );

        MethodHandle methodHandle = lookup.findVirtual(
                defineClassMethod.getDeclaringClass(),
                defineClassMethod.getName(),
                MethodType.methodType(
                        defineClassMethod.getReturnType(),
                        defineClassMethod.getParameterTypes()
                )
        );
        DefineClassInterface function = MethodHandleProxies.asInterfaceInstance(DefineClassInterface.class, methodHandle);
        return function.defineClass(loader, name, bytecode, 0, bytecode.length);
    }
}
