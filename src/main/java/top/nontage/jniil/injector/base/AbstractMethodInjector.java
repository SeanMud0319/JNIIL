package top.nontage.jniil.injector.base;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.LoaderClassPath;
import javassist.NotFoundException;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import me.fan87.nativeinstrumentation.NativeInstrumentation;
import top.nontage.jniil.JNIIL;
import top.nontage.jniil.annotations.After;
import top.nontage.jniil.annotations.At;
import top.nontage.jniil.annotations.Before;
import top.nontage.jniil.annotations.FillLocalVariableTable;
import top.nontage.jniil.annotations.InjectMethodInfo;
import top.nontage.jniil.annotations.Null;
import top.nontage.jniil.annotations.ReplaceCall;
import top.nontage.jniil.asm.LocalVariableTableFiller;
import top.nontage.jniil.interfaces.Injectable;
import top.nontage.jniil.javassist.FileClassPath;
import top.nontage.jniil.javassist.JarFileClassPath;
import top.nontage.jniil.utils.InjectionUtil;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.instrument.ClassFileTransformer;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Abstract base class for method injection.
 * <p>
 * This class provides the full injection workflow using Javassist and NativeInstrumentation.
 * Subclasses can override specific steps to customize ClassPool, ClassLoader, CtClass processing,
 * or perform callbacks after injection.
 **/
public abstract class AbstractMethodInjector {

    protected static final NativeInstrumentation inst = new NativeInstrumentation();
    protected static final Set<String> injectedClasses = new HashSet<>();
    protected static final Map<Class<?>, byte[]> originalBytecodes = new HashMap<>();

    /**
     * Encapsulates target class and method information to simplify passing data between methods.
     **/
    public static class TargetInfo {
        public String typeName;
        public String methodName;
        public String[] methodParams;
        public Class<?>[] appendClasses;
        public String targetTypeThreadName;
        public String[] appendFileLoader;
        public String[] appendJarLoader;
        public boolean defaultLoader;
    }

    /**
     * Performs the full injection process for a given {@link Injectable}.
     * <p>
     * Iterates over all methods in the Injectable class that are annotated with
     * {@link InjectMethodInfo} or {@link Null} and injects code into the target methods.
     *
     * @param injectable the object providing injection information and source code
     * @throws Exception if any Javassist, reflection, or instrumentation error occurs
     */
    public final void inject(Injectable injectable) throws Exception {
        Class<?> clazz = injectable.getClass();

        for (Method method : clazz.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(InjectMethodInfo.class) && !method.isAnnotationPresent(Null.class)) {
                continue;
            }

            TargetInfo info = extractTargetInfo(injectable, method);
            ClassPool pool = prepareClassPool();

            ClassLoader loader = getTargetLoader(info);

            if (info.defaultLoader) {
                pool.insertClassPath(new LoaderClassPath(loader));
                pool.appendSystemPath();
            }

            for (Class<?> append : info.appendClasses) {
                pool.appendClassPath(new LoaderClassPath(append.getClassLoader()));
            }
            if (info.appendFileLoader != null) {
                for (String f : info.appendFileLoader) {
                    if (!f.isEmpty()) pool.appendClassPath(new FileClassPath(new File(f)));
                }
            }
            if (info.appendJarLoader != null) {
                for (String f : info.appendJarLoader) {
                    if (!f.isEmpty()) pool.insertClassPath(new JarFileClassPath(new File(f)));
                }
            }

            CtClass ctClass = pool.get(info.typeName);
            if (ctClass.isFrozen()) ctClass.defrost();

            if (method.isAnnotationPresent(FillLocalVariableTable.class)) {
                byte[] modified = new LocalVariableTableFiller().fillLocalVariableNames(
                        Class.forName(info.typeName), false);
                if (modified != null && modified.length > 0) {
                    ctClass.defrost();
                    ctClass = pool.makeClass(new ByteArrayInputStream(modified));
                }
            }

            ctClass = modifyCtClassBeforeInsertCode(ctClass, injectable);

            CtMethod ctMethod = getCtMethod(ctClass, info);

            String src = injectable.getInjectSourceCode();
            if (src == null) src = injectable.getInjectSourceCode(ctMethod);

            if (ctClass.isFrozen()) ctClass.defrost();

            insertCode(ctMethod, method, src);

            ctClass = modifyCtClassBeforeRedefinition(ctClass, injectable);

            byte[] bytecode = ctClass.toBytecode();
            Class<?> targetClass = Class.forName(info.typeName, true, loader);
            redefineClass(targetClass, bytecode);
            injectedClasses.add(info.typeName);

            onInjected(ctClass, injectable);

            getModifiedCtClass(ctClass);
        }
    }

    /**
     * Allows subclasses to provide a custom {@link ClassPool}.
     * <p>
     * Subclasses can append additional class paths or use a different ClassPool.
     *
     * @return a {@link ClassPool} to use for loading target classes
     **/
    protected ClassPool prepareClassPool() {
        return ClassPool.getDefault();
    }

    /**
     * Allows subclasses to provide a custom {@link ClassLoader} for the target class.
     *
     * @param info target information including class name and optional thread name
     * @return the {@link ClassLoader} used to load the target class
     * @throws ClassNotFoundException if the target class cannot be found
     **/
    protected ClassLoader getTargetLoader(TargetInfo info) throws ClassNotFoundException {
        if (info.targetTypeThreadName == null || info.targetTypeThreadName.isEmpty()) {
            return InjectionUtil.findClassAcrossClassLoaders(info.typeName).getClassLoader();
        }
        return InjectionUtil.findClassLoaderByThread(info.targetTypeThreadName);
    }

    /**
     * Allows subclasses to modify the {@link CtClass} before it is redefined.
     * <p>
     * This can be used to apply additional transformations, instrumentation, or bytecode modifications.
     *
     * @param ctClass    the target {@link CtClass} to modify
     * @param injectable the original {@link Injectable} providing injection information
     * @return the modified {@link CtClass}, can be the same instance or a new one
     */
    protected CtClass modifyCtClassBeforeRedefinition(CtClass ctClass, Injectable injectable) {
        return ctClass;
    }

    /**
     * Allows subclasses to modify the {@link CtClass} before code insertion.
     * <p>
     * This can be used to prepare the class, add fields, or perform other setup before injecting code.
     *
     * @param ctClass    the target {@link CtClass} to modify
     * @param injectable the original {@link Injectable} providing injection information
     * @return the modified {@link CtClass}, can be the same instance or a new one
     */
    protected CtClass modifyCtClassBeforeInsertCode(CtClass ctClass, Injectable injectable) {
        return ctClass;
    }

    /**
     * Callback invoked after injection is complete.
     * <p>
     * Subclasses can override to perform additional actions, logging, or further processing.
     *
     * @param ctClass    the target {@link CtClass} after injection
     * @param injectable the original {@link Injectable} used for injection
     **/
    protected abstract void onInjected(CtClass ctClass, Injectable injectable);

    /**
     * Retrieves the modified {@link CtClass} after injection.
     *
     * @param ctClass the target {@link CtClass}
     **/
    protected abstract void getModifiedCtClass(CtClass ctClass);

    /**
     * Retrieves the target method from the {@link CtClass} based on {@link TargetInfo}.
     *
     * @param ctClass the target {@link CtClass}
     * @param info    the target method information
     * @return the {@link CtMethod} corresponding to the target
     * @throws Exception if the method cannot be found
     **/
    protected CtMethod getCtMethod(CtClass ctClass, TargetInfo info) throws Exception {
        if (info.methodParams.length == 0) return ctClass.getDeclaredMethod(info.methodName);

        CtClass[] paramTypes = Arrays.stream(info.methodParams)
                .map(type -> {
                    try {
                        return ctClass.getClassPool().get(type);
                    } catch (NotFoundException e) {
                        throw new RuntimeException("Parameter type not found: " + type, e);
                    }
                })
                .toArray(CtClass[]::new);
        return ctClass.getDeclaredMethod(info.methodName, paramTypes);
    }

    /**
     * Inserts code into the {@link CtMethod} based on annotations.
     * <p>
     * Supports @After, @Before, @At(line), and @ReplaceCall.
     *
     * @param ctMethod the target method
     * @param method   the method in the {@link Injectable} class
     * @param src      the source code to insert
     * @throws Exception if insertion fails
     **/
    protected void insertCode(CtMethod ctMethod, Method method, String src) throws Exception {
        After afterAnn = method.getAnnotation(After.class);
        Before beforeAnn = method.getAnnotation(Before.class);
        At atAnn = method.getAnnotation(At.class);
        ReplaceCall replaceCallAnn = method.getAnnotation(ReplaceCall.class);

        if (afterAnn != null) {
            ctMethod.insertAfter(src);
        } else if (beforeAnn != null) {
            ctMethod.insertBefore(src);
        } else if (atAnn != null && atAnn.line() >= 0) {
            ctMethod.insertAt(atAnn.line(), src);
        } else if (replaceCallAnn != null && !replaceCallAnn.value().isEmpty()) {
            String[] parts = replaceCallAnn.value().split("#");
            if (parts.length != 2) throw new IllegalArgumentException("Invalid ReplaceCall format");
            String replaceCallClass = parts[0];
            String replaceCallMethod = parts[1];
            int limit = replaceCallAnn.limit();
            int[] counts = replaceCallAnn.counts();
            ctMethod.instrument(new ExprEditor() {
                int current = 1;

                @Override
                public void edit(MethodCall m) {
                    if (m.getClassName().equals(replaceCallClass) && m.getMethodName().equals(replaceCallMethod)) {
                        boolean shouldReplace = limit >= 0 ? current <= limit
                                : counts.length == 0 || Arrays.stream(counts).anyMatch(c -> c == current);
                        if (shouldReplace) {
                            try {
                                m.replace(src);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                        current++;
                    }
                }
            });
        } else {
            throw new IllegalArgumentException("No valid injection point specified via @After, @Before, @At or @ReplaceCall");
        }
    }

    /**
     * Redefines a class using {@link NativeInstrumentation} with the new bytecode.
     * <p>
     * Stores the original bytecode if JNIIL.isStoreOriginalByteCode() is enabled.
     *
     * @param clazz       the class to redefine
     * @param newBytecode the new bytecode
     * @throws Exception if redefinition fails
     **/
    protected void redefineClass(Class<?> clazz, byte[] newBytecode) throws Exception {
        ClassFileTransformer transformer = (loader, className, classBeingRedefined, protectionDomain, classfileBuffer) -> {
            if (classBeingRedefined == clazz) {
                if (JNIIL.isStoreOriginalByteCode() && !originalBytecodes.containsKey(clazz)) {
                    originalBytecodes.put(clazz, Arrays.copyOf(classfileBuffer, classfileBuffer.length));
                }
                return newBytecode;
            }
            return null;
        };

        inst.addTransformer(transformer, true);
        inst.retransformClasses(clazz);
        inst.removeTransformer(transformer);
    }

    /**
     * Extracts {@link TargetInfo} from a method annotated with @InjectMethodInfo or @Null.
     *
     * @param injectable the injectable instance
     * @param method     the method to read annotations from
     * @return the populated {@link TargetInfo}
     * @throws Exception if reflection access fails
     **/
    protected TargetInfo extractTargetInfo(Injectable injectable, Method method) throws Exception {
        TargetInfo info = new TargetInfo();
        boolean isNull = method.isAnnotationPresent(Null.class);

        if (isNull) {
            info.typeName = injectable.targetTypeInternalName();
            info.methodName = injectable.targetMethodName();
            info.methodParams = injectable.targetMethodParams();
            info.appendClasses = injectable.appendClassLoader();
            info.targetTypeThreadName = injectable.targetTypeThreadName();
            info.appendFileLoader = injectable.appendFileLoader();
            info.appendJarLoader = injectable.appendJarLoader();
            info.defaultLoader = injectable.defaultLoader();
        } else {
            InjectMethodInfo annotation = method.getAnnotation(InjectMethodInfo.class);
            info.typeName = annotation.targetTypeInternalName();
            info.methodName = annotation.targetMethodName();
            info.methodParams = annotation.targetMethodParms();
            info.appendClasses = annotation.appendClassLoader();
            info.targetTypeThreadName = annotation.targetTypeThreadName();
            info.appendFileLoader = annotation.appendFileLoader();
            info.appendJarLoader = annotation.appendJarLoader();
            info.defaultLoader = annotation.defaultLoader();
        }
        return info;
    }

    /**
     * Returns the original bytecodes stored for redefined classes.
     *
     * @return a map of class to its original bytecode
     **/
    public static Map<Class<?>, byte[]> getOriginalBytecodes() {
        return originalBytecodes;
    }
}
