package top.nontage.jniil.builder;

import top.nontage.auth.library.annotation.Protect;

@Protect
public class BinaryExpr implements Expr {
    private final Expr left;
    private final String op;
    private final Expr right;

    public BinaryExpr(Expr left, String op, Expr right) {
        this.left = left;
        this.op = op;
        this.right = right;
    }

    @Override
    public String compile() {
        return "(" + left.compile() + " " + op + " " + right.compile() + ")";
    }
}