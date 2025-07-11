package top.nontage.jniil.interfaces;

import javassist.CtClass;

public interface CtClassCallback {
    void onInjected(CtClass ctClass, Injectable source);
}
