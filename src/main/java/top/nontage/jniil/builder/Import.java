package top.nontage.jniil.builder;

public class Import {
    private final String className;

    public Import(String className) {
        this.className = className;
    }

    public Import(Class<?> clazz) {
        this.className = clazz.getName();
    }

    public String getClassName() {
        return className;
    }
}
