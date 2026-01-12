package top.nontage.jniil.interfaces;

import javassist.CtClass;
import top.nontage.auth.library.annotation.Protect;

@Deprecated
@Protect
public interface CtClassCallback {
    void onInjected(CtClass ctClass, Injectable source);
}
