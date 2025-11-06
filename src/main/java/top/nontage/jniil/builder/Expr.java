package top.nontage.jniil.builder;

public interface Expr {
    String compile();
    static Expr of(Object value) {
        if (value instanceof Expr)
            return (Expr) value;
        if (value instanceof Number)
            return value::toString;
        if (value instanceof String)
            return () -> (String) value;
        throw new IllegalArgumentException("Unsupported expression type: " + value);
    }
}
