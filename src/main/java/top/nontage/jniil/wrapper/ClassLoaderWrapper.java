package top.nontage.jniil.wrapper;

public class ClassLoaderWrapper {
    private final ClassLoader internalLoader;

    private ClassLoaderWrapper(ClassLoader classLoader) {
        this.internalLoader = classLoader;
    }

    public static ClassLoaderWrapper of(ClassLoader classLoader) {
        return new ClassLoaderWrapper(classLoader);
    }

    public ClassLoader unwarp() {
        return internalLoader;
    }

    public boolean isBootstrap() {
        return  internalLoader == null;
    }

    @Override
    public String toString() {
        return isBootstrap() ? "PlaceHolder[BootstrapLoader]" : internalLoader.toString();
    }
}
