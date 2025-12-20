package top.nontage.jniil.builder;

public interface Expr {
    String compile();

    default Expr add(Expr other) {
        return new BinaryExpr(this, "+", other);
    }

    default Expr add(Object other) {
        return new BinaryExpr(this, "+", Expr.of(other));
    }

    default Expr sub(Expr other) {
        return new BinaryExpr(this, "-", other);
    }

    default Expr sub(Object other) {
        return new BinaryExpr(this, "-", Expr.of(other));
    }

    default Expr mul(Expr other) {
        return new BinaryExpr(this, "*", other);
    }

    default Expr mul(Object other) {
        return new BinaryExpr(this, "*", Expr.of(other));
    }

    default Expr div(Expr other) {
        return new BinaryExpr(this, "/", other);
    }

    default Expr div(Object other) {
        return new BinaryExpr(this, "/", Expr.of(other));
    }

    default Expr gt(Expr other) {
        return new BinaryExpr(this, ">", other);
    }

    default Expr gt(Object other) {
        return new BinaryExpr(this, ">", Expr.of(other));
    }

    default Expr lt(Expr other) {
        return new BinaryExpr(this, "<", other);
    }

    default Expr lt(Object other) {
        return new BinaryExpr(this, "<", Expr.of(other));
    }

    default Expr gte(Expr other) {
        return new BinaryExpr(this, ">=", other);
    }

    default Expr gte(Object other) {
        return new BinaryExpr(this, ">=", Expr.of(other));
    }

    default Expr lte(Expr other) {
        return new BinaryExpr(this, "<=", other);
    }

    default Expr lte(Object other) {
        return new BinaryExpr(this, "<=", Expr.of(other));
    }

    default Expr eq(Expr other) {
        return new BinaryExpr(this, "==", other);
    }

    default Expr eq(Object other) {
        return new BinaryExpr(this, "==", Expr.of(other));
    }

    default Expr ne(Expr other) {
        return new BinaryExpr(this, "!=", other);
    }

    default Expr ne(Object other) {
        return new BinaryExpr(this, "!=", Expr.of(other));
    }

    default Expr and(Expr other) {
        return new BinaryExpr(this, "&&", other);
    }

    default Expr and(Object other) {
        return new BinaryExpr(this, "&&", Expr.of(other));
    }

    default Expr or(Expr other) {
        return new BinaryExpr(this, "||", other);
    }

    default Expr or(Object other) {
        return new BinaryExpr(this, "||", Expr.of(other));
    }

    default Expr equalsExpr(Expr other) {
        return () -> this.compile() + ".equals(" + other.compile() + ")";
    }

    default Expr equalsExpr(Object other) {
        return () -> this.compile() + ".equals(" + Expr.of(other).compile() + ")";
    }

    static Expr of(Object value) {
        if (value instanceof Expr)
            return (Expr) value;
        if (value instanceof Number)
            return value::toString;
        if (value instanceof String)
            return () -> "\"" + ((String) value)
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n") + "\"";

        throw new IllegalArgumentException("Unsupported expression type: " + value);
    }
}
