package top.nontage.jniil.injector;

import javassist.ClassPool;
import javassist.CtClass;
import top.nontage.jniil.injector.base.AbstractMethodInjector;
import top.nontage.jniil.interfaces.Injectable;
import top.nontage.jniil.utils.InjectionUtil;

public class StandardMethodInjector extends AbstractMethodInjector {
    @Override
    protected ClassPool prepareClassPool() {
        return new ClassPool(null);
    }

    @Override
    protected ClassLoader getTargetLoader(TargetInfo info) throws Exception {
        if (info.targetTypeThreadName == null || info.targetTypeThreadName.isEmpty()) {
            return InjectionUtil.findClassAcrossClassLoaders(info.typeName).getClassLoader();
        }
        return InjectionUtil.findClassLoaderByThread(info.targetTypeThreadName);
    }

    @Override
    protected void modifyCtClassBeforeRedefinition(CtClass ctClass, Injectable injectable) {
    }

    @Override
    protected void onInjected(CtClass ctClass, Injectable injectable) {
    }

    @Override
    protected boolean shouldReturnCtClass() {
        return false;
    }
}
