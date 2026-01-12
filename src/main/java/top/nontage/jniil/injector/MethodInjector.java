package top.nontage.jniil.injector;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.LoaderClassPath;
import javassist.NotFoundException;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import me.fan87.nativeinstrumentation.NativeInstrumentation;
import top.nontage.auth.library.annotation.Protect;
import top.nontage.jniil.JNIIL;
import top.nontage.jniil.annotations.After;
import top.nontage.jniil.annotations.At;
import top.nontage.jniil.annotations.Before;
import top.nontage.jniil.annotations.FillLocalVariableTable;
import top.nontage.jniil.annotations.InjectMethodInfo;
import top.nontage.jniil.annotations.Null;
import top.nontage.jniil.annotations.ReplaceAll;
import top.nontage.jniil.annotations.ReplaceCall;
import top.nontage.jniil.asm.utils.LocalVariableTableFiller;
import top.nontage.jniil.interfaces.CtClassCallback;
import top.nontage.jniil.interfaces.Injectable;
import top.nontage.jniil.javassist.FileClassPath;
import top.nontage.jniil.javassist.JarFileClassPath;
import top.nontage.jniil.utils.InjectionUtil;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Protect
@Deprecated
public class MethodInjector {
    private static final NativeInstrumentation inst = new NativeInstrumentation();
    private static final Set<String> injectedClasses = new HashSet<>();
    private static final Map<Class<?>, byte[]> originalBytecodes = new HashMap<>();

    private static void redefineClass(Class<?> clazz, byte[] newBytecode) {
        ClassFileTransformer transformer = (loader, className, classBeingRedefined, protectionDomain, classfileBuffer) -> {
            if (classBeingRedefined == clazz) {
                if (JNIIL.isStoreOriginalByteCode() && !originalBytecodes.containsKey(clazz)) {
                    originalBytecodes.put(clazz, Arrays.copyOf(classfileBuffer, classfileBuffer.length));
                    System.out.println("Stored original bytecode for: " + clazz.getName());
                }
                return newBytecode;
            }
            return null;
        };

        try {
            inst.addTransformer(transformer, true);
            inst.retransformClasses(clazz);
        } catch (Throwable e) {
            throw new RuntimeException("Redefinition failed for class: " + clazz.getName(), e);
        } finally {
            inst.removeTransformer(transformer);
        }
    }

    public static void revertClass(Class<?> clazz) {
        byte[] original = originalBytecodes.get(clazz);
        if (original == null) {
            System.err.println("No original bytecode stored for class: " + clazz.getName());
            return;
        }

        ClassFileTransformer transformer = (loader, className, classBeingRedefined, protectionDomain, classfileBuffer) -> {
            if (classBeingRedefined == clazz) {
                return original;
            }
            return null;
        };

        try {
            inst.addTransformer(transformer, true);
            inst.retransformClasses(clazz);
            System.out.println("Reverted class: " + clazz.getName());
        } catch (Throwable e) {
            throw new RuntimeException("Failed to revert class: " + clazz.getName(), e);
        } finally {
            inst.removeTransformer(transformer);
        }
    }

    public static void injectMethod(Injectable injectable) {
        try {
            System.out.println("Injecting methods from: " + injectable.getClass().getName());
            Class<?> clazz = injectable.getClass();

            for (Method method : clazz.getDeclaredMethods()) {
                boolean isNull = method.isAnnotationPresent(Null.class);
                String typeName;
                String methodName;
                String[] methodParams;
                Class<?>[] appendClasses;
                String targetTypeThreadName;
                String[] appendFileLoader;
                String[] appendJarLoader;
                boolean defaultLoader;

                if (isNull) {
                    typeName = injectable.targetTypeInternalName();
                    methodName = injectable.targetMethodName();
                    methodParams = injectable.targetMethodParams();
                    appendClasses = injectable.appendClassLoader();
                    targetTypeThreadName = injectable.targetTypeThreadName();
                    appendFileLoader = injectable.appendFileLoader();
                    appendJarLoader = injectable.appendJarLoader();
                    defaultLoader = injectable.defaultLoader();
                } else if (method.isAnnotationPresent(InjectMethodInfo.class)) {
                    InjectMethodInfo info = method.getAnnotation(InjectMethodInfo.class);
                    typeName = info.targetTypeInternalName();
                    methodName = info.targetMethodName();
                    methodParams = info.targetMethodParams();
                    appendClasses = info.appendClassLoader();
                    targetTypeThreadName = info.targetTypeThreadName();
                    appendFileLoader = info.appendFileLoader();
                    appendJarLoader = info.appendJarLoader();
                    defaultLoader = info.defaultLoader();
                } else {
                    continue;
                }

                ClassPool pool = new ClassPool(null);
                ClassLoader targetLoader = (targetTypeThreadName == null || targetTypeThreadName.isEmpty())
                        ? InjectionUtil.findClassAcrossClassLoaders(typeName).getClassLoader()
                        : InjectionUtil.findClassLoaderByThread(targetTypeThreadName);

                if (defaultLoader) {
                    pool.insertClassPath(new LoaderClassPath(targetLoader));
                    pool.appendSystemPath();
                }

                for (Class<?> appendClass : appendClasses)
                    pool.appendClassPath(new LoaderClassPath(appendClass.getClassLoader()));
                if (appendFileLoader != null)
                    for (String appendFile : appendFileLoader)
                        if (!appendFile.isEmpty()) pool.appendClassPath(new FileClassPath(new File(appendFile)));
                if (appendJarLoader != null)
                    for (String appendJarFile : appendJarLoader)
                        if (!appendJarFile.isEmpty())
                            pool.insertClassPath(new JarFileClassPath(new File(appendJarFile)));

                CtClass ctClass = pool.get(typeName);
                if (ctClass.isFrozen()) ctClass.defrost();
                if (method.isAnnotationPresent(FillLocalVariableTable.class)) {
                    byte[] modified = new LocalVariableTableFiller().fillLocalVariableNames(Class.forName(typeName), false);
                    if (modified != null && modified.length > 0) {
                        ctClass.defrost();
                        ctClass = pool.makeClass(new ByteArrayInputStream(modified));
                    }
                }

                CtMethod ctMethod;
                if (methodParams.length == 0) ctMethod = ctClass.getDeclaredMethod(methodName);
                else {
                    CtClass[] paramTypes = Arrays.stream(methodParams)
                            .map(type -> {
                                try {
                                    return pool.get(type);
                                } catch (NotFoundException e) {
                                    throw new RuntimeException("Parameter type not found: " + type, e);
                                }
                            })
                            .toArray(CtClass[]::new);
                    ctMethod = ctClass.getDeclaredMethod(methodName, paramTypes);
                }

                String src = injectable.getInjectSourceCode(ctMethod);
                After afterAnn = method.getAnnotation(After.class);
                Before beforeAnn = method.getAnnotation(Before.class);
                At atAnn = method.getAnnotation(At.class);
                ReplaceCall replaceCallAnn = method.getAnnotation(ReplaceCall.class);

                if (afterAnn != null) ctMethod.insertAfter(src);
                else if (beforeAnn != null) ctMethod.insertBefore(src);
                else if (atAnn != null && atAnn.line() >= 0) ctMethod.insertAt(atAnn.line(), src);
                else if (replaceCallAnn != null && !replaceCallAnn.value().isEmpty()) {
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
                        public void edit(MethodCall m) throws CannotCompileException {
                            if (m.getClassName().equals(replaceCallClass) && m.getMethodName().equals(replaceCallMethod)) {
                                boolean shouldReplace = limit >= 0 ? current <= limit : counts.length == 0 || Arrays.stream(counts).anyMatch(c -> c == current);
                                if (shouldReplace) m.replace(finalSrc);
                                current++;
                            }
                        }
                    });
                } else throw new IllegalArgumentException("No valid injection point specified");

                byte[] afterBytecode = ctClass.toBytecode();

                if (JNIIL.isMethodOutputEnabled()) {
                    File outputDir = JNIIL.getMethodOutputDir();
                    if (!outputDir.exists()) outputDir.mkdirs();
                    ctClass.writeFile(outputDir.getAbsolutePath());
                }
                Class<?> clazzz = Class.forName(typeName, true, targetLoader);
                redefineClass(clazzz, afterBytecode);
                injectedClasses.add(typeName);
                System.out.println("Injected method: " + typeName + "#" + methodName);
            }

        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static void injectMethodsAsync(Injectable... injectables) {
        new Thread(() -> {
            Map<String, List<Injectable>> grouped = new LinkedHashMap<>();
            for (Injectable injectable : injectables) {
                try {
                    Method method = Arrays.stream(injectable.getClass().getDeclaredMethods())
                            .filter(m -> m.isAnnotationPresent(InjectMethodInfo.class)
                                    || m.isAnnotationPresent(Null.class))
                            .findFirst()
                            .orElse(null);
                    if (method == null) continue;
                    InjectMethodInfo info = method.getAnnotation(InjectMethodInfo.class);
                    String key;
                    if (info != null) {
                        key = info.targetTypeInternalName();
                    } else {
                        key = injectable.targetTypeInternalName();
                    }
                    grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(injectable);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            for (Map.Entry<String, List<Injectable>> entry : grouped.entrySet()) {
                List<Injectable> group = entry.getValue();
                for (int i = 0; i < group.size(); i++) {
                    Injectable injectable = group.get(i);
                    try {
                        injectMethod(injectable);
                    } catch (Exception e) {
                        System.err.println("Injection failed for: " + injectable.getClass().getName());
                        e.printStackTrace();
                    }
                    if (i < group.size() - 1) {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException ignored) {
                        }
                    }
                }
            }
        }, "InjectThread").start();
    }

    //its for badlion injection
    public static CtClass injectMethodAndReturnCtClass(Injectable injectable) throws Exception {
        try {
            System.out.println("Injecting method (return CtClass): " + injectable.getClass().getName());
            Class<?> clazz = injectable.getClass();
            for (Method method : clazz.getDeclaredMethods()) {
                boolean isNull = !method.isAnnotationPresent(InjectMethodInfo.class);
                String typeName;
                String methodName;
                String[] methodParams;
                Class<?>[] appendClasses;
                String targetTypeThreadName;
                String[] appendFileLoader;
                String[] appendJarLoader;
                boolean defaultLoader;

                if (isNull) {
                    typeName = injectable.targetTypeInternalName();
                    methodName = injectable.targetMethodName();
                    methodParams = injectable.targetMethodParams();
                    appendClasses = injectable.appendClassLoader();
                    targetTypeThreadName = injectable.targetTypeThreadName();
                    appendFileLoader = injectable.appendFileLoader();
                    appendJarLoader = injectable.appendJarLoader();
                    defaultLoader = injectable.defaultLoader();
                } else if (method.isAnnotationPresent(InjectMethodInfo.class)) {
                    InjectMethodInfo info = method.getAnnotation(InjectMethodInfo.class);
                    typeName = info.targetTypeInternalName();
                    methodName = info.targetMethodName();
                    methodParams = info.targetMethodParams();
                    appendClasses = info.appendClassLoader();
                    targetTypeThreadName = info.targetTypeThreadName();
                    appendFileLoader = info.appendFileLoader();
                    appendJarLoader = info.appendJarLoader();
                    defaultLoader = info.defaultLoader();
                } else {
                    continue;
                }

                ClassPool pool = new ClassPool(null);
                ClassLoader targetLoader;
                if (targetTypeThreadName == null || targetTypeThreadName.isEmpty()) {
                    targetLoader = InjectionUtil.findClassAcrossClassLoaders(typeName).getClassLoader();
                } else {
                    targetLoader = InjectionUtil.findClassLoaderByThread(targetTypeThreadName);
                }
                if (defaultLoader) {
                    pool.appendSystemPath();
                    pool.insertClassPath(new LoaderClassPath(targetLoader));
                }

                for (Class<?> appendClass : appendClasses) {
                    pool.appendClassPath(new LoaderClassPath(appendClass.getClassLoader()));
                }

                if (appendFileLoader != null) {
                    for (String appendFile : appendFileLoader) {
                        if (appendFile.isEmpty()) continue;
                        pool.appendClassPath(new FileClassPath(new File(appendFile)));
                    }
                }

                if (appendJarLoader != null) {
                    for (String appendJarFile : appendJarLoader) {
                        if (appendJarFile.isEmpty()) continue;
                        pool.insertClassPath(new JarFileClassPath(new File(appendJarFile)));
                    }
                }

                if (injectedClasses.contains(typeName)) {
                    pool.insertClassPath(new FileClassPath(new File("libs/minecraft-source/")));
                }

                CtClass ctClass = pool.get(typeName);

                if (ctClass.isFrozen()) {
                    ctClass.defrost();
                }
                if (method.isAnnotationPresent(FillLocalVariableTable.class)) {
                    byte[] modified = new LocalVariableTableFiller().fillLocalVariableNames(Class.forName(typeName), false);
                    if (modified != null && modified.length > 0) {
                        ctClass.defrost();
                        ctClass = pool.makeClass(new ByteArrayInputStream(modified));
                    }
                }

                CtMethod ctMethod;
                if (methodParams.length == 0) {
                    ctMethod = ctClass.getDeclaredMethod(methodName);
                } else {
                    CtClass[] paramTypes = Arrays.stream(methodParams)
                            .map(type -> {
                                try {
                                    return pool.get(type);
                                } catch (NotFoundException e) {
                                    throw new RuntimeException("Parameter type not found: " + type, e);
                                }
                            })
                            .toArray(CtClass[]::new);
                    ctMethod = ctClass.getDeclaredMethod(methodName, paramTypes);
                }
                String src = injectable.getInjectSourceCode(ctMethod);
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
                    if (parts.length != 2) {
                        throw new IllegalArgumentException("Invalid ReplaceCall format, expected 'class#method'");
                    }
                    String replaceCallClass = parts[0];
                    String replaceCallMethod = parts[1];
                    int limit = replaceCallAnn.limit();
                    int[] counts = replaceCallAnn.counts();
                    String finalSrc = src;
                    ctMethod.instrument(new ExprEditor() {
                        int current = 1;

                        @Override
                        public void edit(MethodCall m) throws CannotCompileException {
                            if (m.getClassName().equals(replaceCallClass) && m.getMethodName().equals(replaceCallMethod)) {
                                boolean shouldReplace = false;

                                if (limit >= 0) {
                                    shouldReplace = current <= limit;
                                } else if (counts.length > 0) {
                                    for (int c : counts) {
                                        if (current == c) {
                                            shouldReplace = true;
                                            break;
                                        }
                                    }
                                } else {
                                    shouldReplace = true;
                                }

                                if (shouldReplace) {
                                    m.replace(finalSrc);
                                }
                                current++;
                            }
                        }
                    });
                } else {
                    throw new IllegalArgumentException("No valid injection point specified via @After, @Before, @At or @ReplaceCall");
                }

                byte[] bytecode = ctClass.toBytecode();
                if (JNIIL.isMethodOutputEnabled()) {
                    File outputDir = JNIIL.getMethodOutputDir();
                    if (!outputDir.exists()) {
                        outputDir.mkdirs();
                    }
                    ctClass.writeFile(outputDir.getAbsolutePath());
                    System.out.println("Dumped injected method to: " + outputDir.getAbsolutePath());
                }

                Class<?> clazzz = Class.forName(typeName, true, targetLoader);
                redefineClass(clazzz, bytecode);
                injectedClasses.add(typeName);
                System.out.println("Injected method: " + typeName + "#" + methodName);
                return ctClass;
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        return null;
    }


    public static void injectMethodsAsyncAndReturnCtClass(CtClassCallback callback, Injectable... injectables) {
        new Thread(() -> {
            Map<String, List<Injectable>> grouped = new LinkedHashMap<>();
            for (Injectable injectable : injectables) {
                try {
                    Method method = Arrays.stream(injectable.getClass().getDeclaredMethods())
                            .filter(m -> m.isAnnotationPresent(InjectMethodInfo.class) || m.isAnnotationPresent(Null.class)
                             || m.isAnnotationPresent(At.class) || m.isAnnotationPresent(After.class) || m.isAnnotationPresent(Before.class)
                            || m.isAnnotationPresent(ReplaceAll.class) || m.isAnnotationPresent(ReplaceCall.class))
                            .findFirst().orElse(null);
                    if (method == null) continue;
                    String key = method.isAnnotationPresent(InjectMethodInfo.class)
                            ? method.getAnnotation(InjectMethodInfo.class).targetTypeInternalName()
                            : injectable.targetTypeInternalName();
                    grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(injectable);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            for (Map.Entry<String, List<Injectable>> entry : grouped.entrySet()) {
                List<Injectable> group = entry.getValue();
                for (int i = 0; i < group.size(); i++) {
                    Injectable injectable = group.get(i);
                    try {
                        CtClass ct = injectMethodAndReturnCtClass(injectable);
                        if (callback != null) callback.onInjected(ct, injectable);
                    } catch (Exception e) {
                        System.err.println("Injection failed for: " + injectable.getClass().getName());
                        e.printStackTrace();
                    }
                    if (i < group.size() - 1) {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException ignored) {
                        }
                    }
                }
            }
        }, "InjectThread").start();
    }

    //因為上面那陀我爆改給Nontage Clint用了 所以插件暫時用這邊的
    public static void injectPluginMethod(Injectable injectable) throws ClassNotFoundException, NotFoundException, CannotCompileException, IOException, IllegalAccessException, UnmodifiableClassException {
        Class<?> clazz = injectable.getClass();
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(InjectMethodInfo.class)) {
                InjectMethodInfo info = method.getAnnotation(InjectMethodInfo.class);
                if (info == null) continue;

                ClassPool pool = ClassPool.getDefault();
                ClassLoader targetLoader = InjectionUtil.findClassAcrossClassLoaders(info.targetTypeInternalName()).getClassLoader();
                pool.insertClassPath(new LoaderClassPath(targetLoader));
                pool.appendClassPath(new LoaderClassPath(JNIIL.class.getClassLoader()));
                for (Class<?> appendClass : info.appendClassLoader()) {
                    pool.appendClassPath(new LoaderClassPath(appendClass.getClassLoader()));
                }

                CtClass ctClass = pool.get(info.targetTypeInternalName());
                if (ctClass.isFrozen()) {
                    ctClass.defrost();
                }

                if (method.isAnnotationPresent(FillLocalVariableTable.class)) {
                    byte[] modified = new LocalVariableTableFiller().fillLocalVariableNames(Class.forName(info.targetTypeInternalName()), false);
                    if (modified != null && modified.length > 0) {
                        ctClass.defrost();
                        ctClass = pool.makeClass(new ByteArrayInputStream(modified));
                    }
                }

                CtMethod ctMethod = ctClass.getDeclaredMethod(info.targetMethodName());
                String src = injectable.getInjectSourceCode(ctMethod);
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
                    if (parts.length != 2) {
                        throw new IllegalArgumentException("Invalid ReplaceCall format, expected 'class#method'");
                    }
                    String replaceCallClass = parts[0];
                    String replaceCallMethod = parts[1];
                    int limit = replaceCallAnn.limit();
                    int[] counts = replaceCallAnn.counts();
                    String finalSrc = src;
                    ctMethod.instrument(new ExprEditor() {
                        int current = 1;

                        @Override
                        public void edit(MethodCall m) throws CannotCompileException {
                            if (m.getClassName().equals(replaceCallClass) && m.getMethodName().equals(replaceCallMethod)) {
                                boolean shouldReplace = false;

                                if (limit >= 0) {
                                    shouldReplace = current <= limit;
                                } else if (counts.length > 0) {
                                    for (int c : counts) {
                                        if (current == c) {
                                            shouldReplace = true;
                                            break;
                                        }
                                    }
                                } else {
                                    shouldReplace = true;
                                }
                                if (shouldReplace) {
                                    m.replace(finalSrc);
                                }
                                current++;
                            }
                        }
                    });
                } else {
                    throw new IllegalArgumentException("No valid injection point specified via @After, @Before, @At or @ReplaceCall");
                }

                byte[] bytecode = ctClass.toBytecode();
                Class<?> clazzz = Class.forName(info.targetTypeInternalName());
                redefineClass(clazzz, bytecode);
                System.out.println("Injected method: " + info.targetTypeInternalName() + "#" + info.targetMethodName());
                if (JNIIL.isMethodOutputEnabled()) {
                    File outputDir = JNIIL.getMethodOutputDir();
                    if (!outputDir.exists()) {
                        outputDir.mkdirs();
                    }
                    ctClass.writeFile(outputDir.getAbsolutePath());
                    System.out.println("Dumped injected method to: " + outputDir.getAbsolutePath());
                }
            }
        }
    }

    //跟上面一樣
    public static void injectPluginMethodsAsync(Injectable... injectables) {
        new Thread(() -> {
            Map<String, List<Injectable>> grouped = new LinkedHashMap<>();
            for (Injectable injectable : injectables) {
                try {
                    Method method = Arrays.stream(injectable.getClass().getDeclaredMethods())
                            .filter(m -> m.isAnnotationPresent(InjectMethodInfo.class)
                                    || m.isAnnotationPresent(Null.class))
                            .findFirst()
                            .orElse(null);
                    if (method == null) continue;
                    InjectMethodInfo info = method.getAnnotation(InjectMethodInfo.class);
                    String key;
                    if (info != null) {
                        key = info.targetTypeInternalName();
                    } else {
                        key = injectable.targetTypeInternalName();
                    }
                    grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(injectable);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            for (Map.Entry<String, List<Injectable>> entry : grouped.entrySet()) {
                List<Injectable> group = entry.getValue();
                for (int i = 0; i < group.size(); i++) {
                    Injectable injectable = group.get(i);
                    try {
                        injectPluginMethod(injectable);
                    } catch (Exception e) {
                        System.err.println("Injection failed for: " + injectable.getClass().getName());
                        e.printStackTrace();
                    }
                    if (i < group.size() - 1) {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException ignored) {
                        }
                    }
                }
            }
        }, "InjectThread").start();
    }

    public static Map<Class<?>, byte[]> getOriginalBytecodes() {
        return originalBytecodes;
    }
}
