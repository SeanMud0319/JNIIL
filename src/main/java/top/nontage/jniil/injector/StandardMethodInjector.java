package top.nontage.jniil.injector;

import javassist.ClassPool;
import javassist.CtClass;
import top.nontage.jniil.injector.base.AbstractMethodInjector;
import top.nontage.jniil.interfaces.Injectable;

public final class StandardMethodInjector extends AbstractMethodInjector {
    @Override
    protected ClassPool prepareClassPool() {
        return new ClassPool(null);
    }
    public void inject(Injectable... injectable) throws Exception {
        for (Injectable IInjectable : injectable) {
            inject(IInjectable);
        }
    }
}
