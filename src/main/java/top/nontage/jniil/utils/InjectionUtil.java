package top.nontage.jniil.utils;

import top.nontage.jniil.JNIIL;
import top.nontage.jniil.verify.BytecodeVerifier;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility class for classloading, bytecode manipulation, and class injection.
 *
 * <p><b>Why not just use {@code JNIIL.getInstrumentation()} directly?</b></p>
 *
 * <p>In the Bootstrap ClassLoader deployment mode (when JNIIL is installed with
 * {@code useBootLoader=true}), the {@code JNIIL} class must be loaded by the
 * Bootstrap ClassLoader first. However, {@code InjectionUtil} is a utility class
 * with static initialization. If any static field or static block in this class
 * references {@code JNIIL}, the JVM will attempt to load {@code JNIIL} at the
 * time {@code InjectionUtil} is initialized.</p>
 *
 * <p>If {@code InjectionUtil} happens to be loaded by a child ClassLoader
 * (e.g., AppClassLoader) before {@code JNIIL} is installed into the Bootstrap
 * ClassLoader, the child ClassLoader will load {@code JNIIL} first. This would
 * cause two separate copies of {@code JNIIL} to exist in different classloaders,
 * breaking the "single shared instance" assumption and causing classloader
 * conflicts, {@code ClassCastException}, and {@code NoSuchMethodError} when
 * cross-loader operations are attempted.</p>
 *
 * <p>Therefore, all methods in this class are static and rely on
 * {@code JNIIL.getInstrumentation()} being called at method invocation time,
 * not at class initialization time. This ensures that when {@code JNIIL} is
 * finally loaded, it is in the correct classloader context — the Bootstrap
 * ClassLoader.</p>
 *
 * <p><b>tl;dr:</b> Static fields cause early classloading. Methods avoid this
 * problem by deferring the {@code JNIIL} reference until the actual method call,
 * by which time the Bootstrap ClassLoader setup has completed.</p>
 */
public class InjectionUtil {

    private InjectionUtil() {

    }

    public static Class<?> findClassAcrossClassLoaders(String className) throws ClassNotFoundException {
        for (Class<?> clazz : JNIIL.getInstrumentation().getAllLoadedClasses()) {
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
        for (Class<?> clazz : JNIIL.getInstrumentation().getAllLoadedClasses()) {
            ClassLoader loader = clazz.getClassLoader();
            loaders.add(loader);
        }
        System.out.println("All ClassLoaders:");
        for (ClassLoader loader : loaders) {
            System.out.println(loader);
        }
    }

    public static void dumpClass(byte[] bytes, String path) {
        try {
            Path out = Paths.get(path);
            Path parent = out.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(out, bytes);
        } catch (IOException e) {
            throw new RuntimeException("Failed to dump class to " + path, e);
        }
    }

    public static Class<?> forceLoadClass(String className, ClassLoader loader) {
        try {
            return Class.forName(className, false, loader);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    // Return the original bytes of the class in file
    public static byte[] getClassBytes(String className) throws IOException {
        String path = className.replace('.', '/') + ".class";
        try (InputStream in = InjectionUtil.class.getClassLoader().getResourceAsStream(path)) {
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

    // Return the original bytes of the class in file
    public static byte[] getClassBytes(Class<?> clazz) throws IOException {
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

    // Return the class bytes in memory
    public static byte[] getOriginalClassBytes(Class<?> clazz) throws Exception {
        return getOriginalClassBytes(clazz.getName());
    }

    // Return the class bytes in memory
    public static byte[] getOriginalClassBytes(String targetClassName) throws Exception {
        final byte[][] result = new byte[1][];
        ClassFileTransformer transformer = (loader, className, classBeingRedefined, protectionDomain, classfileBuffer) -> {
            if (className.equals(targetClassName.replace('.', '/'))) {
                result[0] = classfileBuffer;
            }
            return null;
        };
        JNIIL.getInstrumentation().addTransformer(transformer, true);
        for (Class<?> clazz : JNIIL.getInstrumentation().getAllLoadedClasses()) {
            if (clazz.getName().equals(targetClassName)) {
                JNIIL.getInstrumentation().retransformClasses(clazz);
                break;
            }
        }
        JNIIL.getInstrumentation().removeTransformer(transformer);
        return result[0];
    }

    public static String getMethodDescriptor(String[] params, String returnType) {
        StringBuilder sb = new StringBuilder("(");
        if (params != null) {
            for (String param : params) {
                sb.append(getDescriptor(param));
            }
        }
        sb.append(")");
        sb.append(getDescriptor(returnType));
        return sb.toString();
    }

    public static String getDescriptor(String className) {
        if (className.startsWith("L") && className.endsWith(";")) {
            return className;
        }
        if (className.startsWith("[")) {
            return className;
        }

        switch (className) {
            case "int": return "I";
            case "long": return "J";
            case "boolean": return "Z";
            case "char": return "C";
            case "byte": return "B";
            case "short": return "S";
            case "float": return "F";
            case "double": return "D";
            case "void":
            case "V":
                return "V";
        }

        if (className.endsWith("[]")) {
            String base = className.substring(0, className.length() - 2);
            return "[" + getDescriptor(base);
        }

        return "L" + className.replace('.', '/') + ";";
    }

    // Just define class into exists ClassLoader
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
        MethodHandles.Lookup lookup = UnsafeUtil.IMPL_LOOKUP;

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
