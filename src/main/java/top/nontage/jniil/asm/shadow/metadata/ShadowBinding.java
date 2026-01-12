package top.nontage.jniil.asm.shadow.metadata;

public final class ShadowBinding {
    public final Class<?> shadowClass;
    public final Object instance;

    public ShadowBinding(Class<?> shadowClass, Object instance) {
        this.shadowClass = shadowClass;
        this.instance = instance;
    }
}
