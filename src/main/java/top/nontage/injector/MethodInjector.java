package top.nontage.injector;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.LoaderClassPath;
import javassist.NotFoundException;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import me.fan87.javainjector.NativeInstrumentation;
import top.nontage.JNIIL;
import top.nontage.annotations.InjectMethodInfo;
import top.nontage.interfaces.Injectable;
import top.nontage.utils.InjectionUtil;

import java.io.IOException;
import java.lang.instrument.ClassDefinition;
import java.lang.reflect.Method;

public class MethodInjector {
    public static void injectMethod(Injectable injectable) throws ClassNotFoundException, NotFoundException, CannotCompileException, IOException {
        Class<?> clazz = injectable.getClass();
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(InjectMethodInfo.class)) {
                InjectMethodInfo info = method.getAnnotation(InjectMethodInfo.class);
                if (info == null) return;

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

                if (info.after()) {
                    ctMethod.insertAfter(src);
                } else if (info.before()) {
                    ctMethod.insertBefore(src);
                } else if (info.atLine() >= 0) {
                    ctMethod.insertAt(info.atLine(), src);
                } else if (!info.replaceCallClass().isEmpty() && !info.replaceCallMethod().isEmpty()) {
                    ctMethod.instrument(new ExprEditor() {
                        @Override
                        public void edit(MethodCall m) throws CannotCompileException {
                            if (m.getClassName().equals(info.replaceCallClass()) && m.getMethodName().equals(info.replaceCallMethod())) {
                                m.replace(src);
                            }
                        }
                    });
                } else {
                    throw new IllegalArgumentException("No valid injection point specified in InjectMethodInfo");
                }

                byte[] bytecode = ctClass.toBytecode();
                Class<?> clazzz = Class.forName(info.targetTypeInternalName());
                NativeInstrumentation inst = new NativeInstrumentation();
                inst.redefineClasses(new ClassDefinition(clazzz, bytecode));
            }
        }
    }
}
