package top.nontage.jniil.injector;

import javassist.ClassPool;
import javassist.CtClass;
import top.nontage.jniil.injector.base.AbstractMethodInjector;
import top.nontage.jniil.interfaces.Injectable;

public class StandardMethodInjector extends AbstractMethodInjector {
    @Override
    protected ClassPool prepareClassPool() {
        return new ClassPool(null);
    }

    @Override
    protected void onInjected(CtClass ctClass, Injectable injectable) {
    }

    @Override
    protected void getModifiedCtClass(CtClass ctClass) {

    }


}
