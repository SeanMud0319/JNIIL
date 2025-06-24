package top.nontage.jniil.utils;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.MethodInfo;
import javassist.bytecode.annotation.Annotation;
import me.fan87.javainjector.NativeInstrumentation;
import sun.misc.Unsafe;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class InjectionUtil {
    public static Class<?> findClassAcrossClassLoaders(String className) throws ClassNotFoundException {
        NativeInstrumentation inst = new NativeInstrumentation();
        for (Class<?> clazz : inst.getAllLoadedClasses()) {
            if (clazz.getName().equals(className)) {
                return clazz;
            }
        }
        throw new ClassNotFoundException("Class not found: " + className);
    }
    public static Class<?> forceLoadClass(String className, ClassLoader loader) {
        try {
            return Class.forName(className, false, loader);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
    public static byte[] getClassBytes(Class<?> clazz) throws IOException {
        String path = clazz.getName().replace('.', '/') + ".class";
        try (InputStream in = clazz.getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new IOException("Class not found: " + path);
            return in.readAllBytes();
        }
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

    public static void unsafeInjectClass(ClassLoader loader, String name, byte[] bytecode) throws Throwable {
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
        function.defineClass(loader, name, bytecode, 0, bytecode.length);
    }
    public static byte[] removeInjectMethodInfo(byte[] originalBytecode) throws Exception {
        ClassPool pool = new ClassPool(true);
        CtClass original = pool.makeClass(new ByteArrayInputStream(originalBytecode));
        CtClass newClass = pool.makeClass(original.getName());
        for (CtMethod method : original.getDeclaredMethods()) {
            MethodInfo methodInfo = method.getMethodInfo();
            AnnotationsAttribute visible = (AnnotationsAttribute) methodInfo.getAttribute(AnnotationsAttribute.visibleTag);
            boolean hasInject = false;
            if (visible != null) {
                Annotation injectMethodInfoAnno = visible.getAnnotation("top.nontage.jniil.annotations.InjectMethodInfo");
                if (injectMethodInfoAnno != null) {
                    hasInject = true;
                }
            }
            if (!hasInject) {
                newClass.addMethod(new CtMethod(method, newClass, null));
            }
        }

        byte[] bytecode = newClass.toBytecode();
        original.detach();
        newClass.detach();
        return bytecode;
    }


}
