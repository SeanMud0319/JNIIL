package top.nontage.jniil.injector.base;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.LoaderClassPath;
import javassist.NotFoundException;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import top.nontage.jniil.JNIIL;
import top.nontage.jniil.annotations.After;
import top.nontage.jniil.annotations.At;
import top.nontage.jniil.annotations.Before;
import top.nontage.jniil.annotations.FillLocalVariableTable;
import top.nontage.jniil.annotations.InjectMethodInfo;
import top.nontage.jniil.annotations.Null;
import top.nontage.jniil.annotations.ReplaceAll;
import top.nontage.jniil.annotations.ReplaceCall;
import top.nontage.jniil.asm.LocalVariableTableFiller;
import top.nontage.jniil.exception.BytecodeVerifyException;
import top.nontage.jniil.interfaces.Injectable;
import top.nontage.jniil.javassist.FileClassPath;
import top.nontage.jniil.javassist.JarFileClassPath;
import top.nontage.jniil.utils.InjectionUtil;
import top.nontage.jniil.verify.BytecodeVerifier;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
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

    protected static final Instrumentation inst = JNIIL.getInstrumentation();
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
     * Performs bytecode injection for the specified {@link Injectable} instance.
     * <p>
     * This is the core method of the JNIIL framework. It scans all methods within the given
     * {@code injectable} object for injection annotations (such as {@code @Before}, {@code @After},
     * {@code @At}, etc.), determines the target class and method, modifies their bytecode using
     * Javassist, and finally redefines the class through {@link java.lang.instrument.Instrumentation}.
     * </p>
     *
     * <h3>Workflow Overview</h3>
     * <ol>
     *   <li>Scan all declared methods of {@code injectable} for recognized injection annotations.</li>
     *   <li>Extract target metadata using {@link #extractTargetInfo(Injectable, Method)}.</li>
     *   <li>Prepare a {@link ClassPool} and insert all required class paths (including system, file, and JAR paths).</li>
     *   <li>Load the target {@link CtClass} and, if annotated with {@link FillLocalVariableTable},
     *       reconstruct its LocalVariableTable.</li>
     *   <li>Obtain the corresponding {@link CtMethod} and insert the injection source code
     *       (retrieved from {@link Injectable#getInjectSourceCode()} or
     *       {@link Injectable#getInjectSourceCode(CtMethod)}).</li>
     *   <li>If {@link JNIIL#isMethodOutputEnabled()} is enabled, dump the modified class file to
     *       the configured output directory.</li>
     *   <li>If {@link JNIIL#isBytecodeVerifying()} is enabled, verify the modified bytecode using
     *       {@link BytecodeVerifier}. Verification failures raise a {@link BytecodeVerifyException}.</li>
     *   <li>If verification succeeds, redefine the target class using the agent's API.</li>
     *   <li>Invoke {@link #onInjected(CtClass, Injectable)} to notify post-injection callbacks.</li>
     * </ol>
     *
     * <h3>Supported Injection Annotations</h3>
     * <ul>
     *   <li>{@link InjectMethodInfo}</li>
     *   <li>{@link Before}</li>
     *   <li>{@link After}</li>
     *   <li>{@link At}</li>
     *   <li>{@link ReplaceCall}</li>
     *   <li>{@link Null}</li>
     * </ul>
     *
     * <h3>Notes</h3>
     * <ul>
     *   <li>Frozen classes will be automatically defrosted before modification.</li>
     *   <li>If bytecode verification fails, a {@link BytecodeVerifyException} is thrown and
     *       the injection process is aborted.</li>
     *   <li>This method performs unsafe runtime redefinition and must be executed in an environment
     *       with instrumentation privileges.</li>
     * </ul>
     *
     * @param injectable the {@link Injectable} instance containing annotated injection methods and logic.
     * @throws Exception if any of the following occur:
     *                   <ul>
     *                     <li>The target class or method cannot be found.</li>
     *                     <li>Javassist fails to read, modify, or write the class bytecode.</li>
     *                     <li>The redefinition process via Instrumentation throws an exception.</li>
     *                     <li>{@link BytecodeVerifier} detects an invalid or corrupted class structure.</li>
     *                   </ul>
     * @see Injectable
     * @see BytecodeVerifier
     * @see BytecodeVerifyException
     * @see FillLocalVariableTable
     * @see ClassPool
     * @see CtMethod
     * @see JNIIL
     */
    public final void inject(Injectable injectable) throws Exception {
        Class<?> clazz = injectable.getClass();

        for (Method method : clazz.getDeclaredMethods()) {
            boolean hasInjectAnnotation =
                    method.isAnnotationPresent(InjectMethodInfo.class) ||
                            method.isAnnotationPresent(After.class) ||
                            method.isAnnotationPresent(Before.class) ||
                            method.isAnnotationPresent(At.class) ||
                            method.isAnnotationPresent(ReplaceAll.class) ||
                            method.isAnnotationPresent(ReplaceCall.class) ||
                            method.isAnnotationPresent(Null.class);

            if (!hasInjectAnnotation) {
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
                byte[] modified = new LocalVariableTableFiller().fillLocalVariableNames(Class.forName(info.typeName), false);
                if (modified != null && modified.length > 0) {
                    ctClass.defrost();
                    ctClass = pool.makeClass(new ByteArrayInputStream(modified));
                }
            }

            ctClass = modifyCtClassBeforeInsertCode(ctClass, injectable);

            byte[] originalBytecode = InjectionUtil.getClassBytes(Class.forName(info.typeName));

            CtMethod ctMethod = getCtMethod(ctClass, info);

            String src = injectable.getInjectSourceCode();
            if (src == null) src = injectable.getInjectSourceCode(ctMethod);

            if (ctClass.isFrozen()) ctClass.defrost();

            insertCode(ctMethod, method, src);

            ctClass = modifyCtClassBeforeRedefinition(ctClass, injectable);

            if (JNIIL.isMethodOutputEnabled()) {
                File outputDir = JNIIL.getMethodOutputDir();
                if (!outputDir.exists()) {
                    outputDir.mkdirs();
                }
                ctClass.writeFile(outputDir.getAbsolutePath());
                System.out.println("Dumped injected method to: " + outputDir.getAbsolutePath());
            }

            byte[] bytecode = ctClass.toBytecode();

            if (JNIIL.isBytecodeVerifying()) {
                BytecodeVerifier.Result result = BytecodeVerifier.verifyAll(info.typeName, originalBytecode, bytecode);
                if (!result.isAsmValid() || !result.isJvmValid()) {
                    String msg = "[BytecodeVerifier] Class " + ctClass.getName() +
                            " failed verification:\n" + result.getDetails();
                    throw new BytecodeVerifyException(msg);
                }
            }

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
        ReplaceAll replaceAllAnn = method.getAnnotation(ReplaceAll.class);
        ReplaceCall replaceCallAnn = method.getAnnotation(ReplaceCall.class);

        if (afterAnn != null) {
            ctMethod.insertAfter(src);
            return;
        }
        if (beforeAnn != null) {
            ctMethod.insertBefore(src);
            return;
        }
        if (atAnn != null && atAnn.line() >= 0) {
            ctMethod.insertAt(atAnn.line(), src);
            return;
        }
        if (replaceAllAnn != null) {
            if (!src.startsWith("{") && !src.endsWith("}")) {
                src = "{" + src + "}";
            }
            ctMethod.setBody(src);
            return;
        }
        if (replaceCallAnn != null && !replaceCallAnn.value().isEmpty()) {
            String[] parts = replaceCallAnn.value().split("#");
            if (parts.length != 2) throw new IllegalArgumentException("Invalid ReplaceCall format");
            String replaceCallClass = parts[0];
            String replaceCallMethod = parts[1];
            int limit = replaceCallAnn.limit();
            int[] counts = replaceCallAnn.counts();
            String finalSrc = src;
            ctMethod.instrument(new ExprEditor() {
                int current = 1;

                @Override
                public void edit(MethodCall m) {
                    if (m.getClassName().equals(replaceCallClass) && m.getMethodName().equals(replaceCallMethod)) {
                        boolean shouldReplace = limit >= 0 ? current <= limit
                                : counts.length == 0 || Arrays.stream(counts).anyMatch(c -> c == current);
                        if (shouldReplace) {
                            try {
                                m.replace(finalSrc);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                        current++;
                    }
                }
            });
        } else {
            throw new IllegalArgumentException("No valid injection point specified via @After, @Before, @At, @ReplaceAll or @ReplaceCall");
        }
    }

    /**
     * Redefines a class using {@link Instrumentation} with the new bytecode.
     * <p>
     * Stores the original bytecode if JNIIL.isStoreOriginalByteCode() is enabled.
     *
     * @param clazz       the class to redefine
     * @param newBytecode the new bytecode
     **/
    protected void redefineClass(Class<?> clazz, byte[] newBytecode) throws UnmodifiableClassException {
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
     **/
    protected TargetInfo extractTargetInfo(Injectable injectable, Method method) {
        TargetInfo info = new TargetInfo();
        boolean isNull = !method.isAnnotationPresent(InjectMethodInfo.class);
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
            info.methodParams = annotation.targetMethodParams();
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
