package top.nontage.injector;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.LoaderClassPath;
import javassist.NotFoundException;
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
                InjectMethodInfo.InjectionPoint injectionPoint = info.injectionPoint();
                switch (injectionPoint) {
                    case AFTER:
                        ctMethod.insertAfter(src);
                        break;
                    case AT:
                        if (info.atLine() < 0) {
                            throw new IllegalArgumentException("atLine must be specified for AT injection point");
                        }
                        ctMethod.insertAt(info.atLine(), src);
                        break;
                    case BEFORE:
                        ctMethod.insertBefore(src);
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown injection point: " + injectionPoint);
                }
                byte[] bytecode = ctClass.toBytecode();
                Class<?> clazzz = Class.forName(info.targetTypeInternalName());
                NativeInstrumentation inst = new NativeInstrumentation();
                inst.redefineClasses(new ClassDefinition(clazzz, bytecode));
            }
        }
    }
}
