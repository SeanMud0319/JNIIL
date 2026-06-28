package top.nontage.jniil.injector;

import javassist.ClassPool;
import top.nontage.jniil.injector.base.AbstractMethodInjector;

public final class StandardMethodInjector extends AbstractMethodInjector {
    @Override
    protected ClassPool prepareClassPool() {
        return new ClassPool(null);
    }

    @Override
    public void inject(Object... injectable) throws Exception {
        for (Object i : injectable) super.inject(i);
    }
}
