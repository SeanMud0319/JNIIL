package top.nontage.jniil.injector;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.LoaderClassPath;
import javassist.NotFoundException;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import me.fan87.javainjector.NativeInstrumentation;
import top.nontage.jniil.annotations.After;
import top.nontage.jniil.annotations.At;
import top.nontage.jniil.annotations.Before;
import top.nontage.jniil.annotations.InjectMethodInfo;
import top.nontage.jniil.annotations.ReplaceCall;
import top.nontage.jniil.interfaces.Injectable;
import top.nontage.jniil.utils.InjectionUtil;

import java.io.IOException;
import java.lang.instrument.ClassDefinition;
import java.lang.reflect.Method;

public class MethodInjector {
    public static void injectMethod(Injectable injectable) throws ClassNotFoundException, NotFoundException, CannotCompileException, IOException {
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
                CtMethod ctMethod = ctClass.getDeclaredMethod(info.targetMethodName());
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
                Class<?> clazzz = Class.forName(info.targetTypeInternalName());
                NativeInstrumentation inst = new NativeInstrumentation();
                inst.redefineClasses(new ClassDefinition(clazzz, bytecode));
            }
        }
    }
}
