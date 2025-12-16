package top.nontage.jniil.builder;

import top.nontage.auth.library.annotation.Protect;

@Protect
public class LocalValue<T> implements Expr {

    private final String name;
    private final Class<T> type;

    public LocalValue(String name, Class<T> type) {
        this.name = name;
        this.type = type;
    }

    public String get() {
        return name;
    }

    public Class<T> getType() {
        return type;
    }

    public String setExpr(Object value) {
        return name + " = " + value + ";";
    }

    public String setString(String value) {
        return name + " = \"" + value + "\";";
    }

    public String setFrom(LocalValue<?> other) {
        return name + " = " + other.get() + ";";
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public String compile() {
        return name;
    }
}
