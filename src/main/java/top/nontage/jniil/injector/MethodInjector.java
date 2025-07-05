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
import top.nontage.jniil.JNIIL;
import top.nontage.jniil.annotations.After;
import top.nontage.jniil.annotations.At;
import top.nontage.jniil.annotations.Before;
import top.nontage.jniil.annotations.InjectMethodInfo;
import top.nontage.jniil.annotations.Null;
import top.nontage.jniil.annotations.ReplaceCall;
import top.nontage.jniil.interfaces.Injectable;
import top.nontage.jniil.javassist.FileClassPath;
import top.nontage.jniil.javassist.JarFileClassPath;
import top.nontage.jniil.utils.InjectionUtil;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MethodInjector {
    private static final NativeInstrumentation inst = new NativeInstrumentation();

    private static void redefineClass(Class<?> clazz, byte[] bytecode) throws ClassNotFoundException, CannotCompileException {
        ClassFileTransformer transformer = (loader, className, classBeingRedefined, protectionDomain, classfileBuffer) -> {
            if (clazz == classBeingRedefined) {
                return bytecode;
            }
            return new byte[0];
        };
        inst.addTransformer(transformer, true);
        inst.retransformClasses(clazz);
        inst.removeTransformer(transformer);
    }
    public static void injectMethod(Injectable injectable) throws ClassNotFoundException, NotFoundException, CannotCompileException, IOException {
        try {
            System.out.println("Injecting method: " + injectable.getClass().getName());
            Class<?> clazz = injectable.getClass();
            for (Method method : clazz.getDeclaredMethods()) {
                boolean isNull = method.isAnnotationPresent(Null.class);
                String typeName;
                String methodName;
                String[] methodParams;
                Class<?>[] appendClasses;
                String targetTypeThreadName;
                String appendFileLoader;
                String appendJarLoader;
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
                    methodParams = info.targetMethodParms();
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
                    pool.appendClassPath(new LoaderClassPath(JNIIL.class.getClassLoader()));
                }
                for (Class<?> appendClass : appendClasses) {
                    pool.appendClassPath(new LoaderClassPath(appendClass.getClassLoader()));
                }
                if (appendFileLoader != null && !appendFileLoader.isEmpty()) {
                    pool.appendClassPath(new FileClassPath(new File(appendFileLoader)));
                }
                if (appendJarLoader != null && !appendJarLoader.isEmpty()) {
                    pool.appendClassPath(new JarFileClassPath(new File(appendJarLoader)));
                }

                CtClass ctClass = pool.get(typeName);

                if (ctClass.isFrozen()) {
                    System.out.println("Defrosting class: " + ctClass.getName());
                    ctClass.defrost();
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

                String src = injectable.getInjectSourceCode();
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
                                    m.replace(src);
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
}
