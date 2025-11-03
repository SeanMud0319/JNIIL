package top.nontage.jniil.injector;

import javassist.CtClass;
import javassist.ClassPool;
import top.nontage.jniil.injector.base.AbstractMethodInjector;
import top.nontage.jniil.interfaces.Injectable;

import java.util.HashMap;
import java.util.Map;

public class ReusableMethodInjector extends AbstractMethodInjector {

    private final Map<String, CtClass> modifiedClasses = new HashMap<>();

    @Override
    protected ClassPool prepareClassPool() {
        return new ClassPool(null);
    }

    @Override
    protected CtClass modifyCtClassBeforeInsertCode(CtClass ctClass, Injectable injectable) {
        if (modifiedClasses.containsKey(ctClass.getName())) {
            return modifiedClasses.get(ctClass.getName());
        }
        return ctClass;
    }

    @Override
    protected void onInjected(CtClass ctClass, Injectable injectable) {
    }

    @Override
    protected void getModifiedCtClass(CtClass ctClass) {
        modifiedClasses.put(ctClass.getName(), ctClass);
    }
}
