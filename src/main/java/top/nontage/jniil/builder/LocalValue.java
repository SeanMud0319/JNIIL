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

    public Expr add(Expr other) {
        return new BinaryExpr(this, "+", other);
    }
    public Expr sub(Expr other) {
        return new BinaryExpr(this, "-", other);
    }

    public Expr mul(Expr other) {
        return new BinaryExpr(this, "*", other);
    }

    public Expr div(Expr other) {
        return new BinaryExpr(this, "/", other);
    }

    public Expr gt(Expr other) {
        return new BinaryExpr(this, ">", other);
    }

    public Expr lt(Expr other) {
        return new BinaryExpr(this, "<", other);
    }

    public Expr gte(Expr other) {
        return new BinaryExpr(this, ">=", other);
    }

    public Expr lte(Expr other) {
        return new BinaryExpr(this, "<=", other);
    }

    public Expr eq(Expr other) {
        return new BinaryExpr(this, "==", other);
    }

    public Expr ne(Expr other) {
        return new BinaryExpr(this, "!=", other);
    }

    public Expr or(Expr other) {
        return new BinaryExpr(this, "||", other);
    }

    public Expr and(Expr other) {
        return new BinaryExpr(this, "&&", other);
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
