package top.nontage.jniil.builder;

public interface Expr {
    String compile();

    default Expr add(Expr other) {
        return new BinaryExpr(this, "+", other);
    }

    default Expr sub(Expr other) {
        return new BinaryExpr(this, "-", other);
    }

    default Expr mul(Expr other) {
        return new BinaryExpr(this, "*", other);
    }

    default Expr div(Expr other) {
        return new BinaryExpr(this, "/", other);
    }

    default Expr gt(Expr other) {
        return new BinaryExpr(this, ">", other);
    }

    default Expr lt(Expr other) {
        return new BinaryExpr(this, "<", other);
    }

    default Expr gte(Expr other) {
        return new BinaryExpr(this, ">=", other);
    }

    default Expr lte(Expr other) {
        return new BinaryExpr(this, "<=", other);
    }

    default Expr eq(Expr other) {
        return new BinaryExpr(this, "==", other);
    }

    default Expr ne(Expr other) {
        return new BinaryExpr(this, "!=", other);
    }

    default Expr and(Expr other) {
        return new BinaryExpr(this, "&&", other);
    }

    default Expr or(Expr other) {
        return new BinaryExpr(this, "||", other);
    }

    default Expr equalsExpr(Expr other) {
        return () -> this.compile() + ".equals(" + other.compile() + ")";
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
