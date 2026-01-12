package top.nontage.jniil.asm.shadow.metadata;

import java.util.function.Supplier;

public final class ShadowBinding {
    public final Class<?> shadowClass;
    public final Supplier<Object> instanceSupplier;

    public ShadowBinding(Class<?> shadowClass, Object instance) {
        this.shadowClass = shadowClass;
        this.instanceSupplier = () -> instance;
    }

    public ShadowBinding(Class<?> shadowClass, Supplier<Object> instanceSupplier) {
        this.shadowClass = shadowClass;
        this.instanceSupplier = instanceSupplier;
    }
}