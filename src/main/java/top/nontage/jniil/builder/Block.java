package top.nontage.jniil.builder;

import top.nontage.auth.library.annotation.Protect;

import java.lang.reflect.Method;
import java.util.*;
@Protect
public class Block {

    private final Map<String, LocalValue<?>> builderLocals;
    private final Map<Integer, LocalValue<?>> paramLocals;
    private final Map<Integer, LocalValue<?>> existingLocals;
    private final Method method;
    private final MethodParams methodParams;

    private final List<String> lines = new ArrayList<>();

    public Block(Map<String, LocalValue<?>> builderLocals,
                 Map<Integer, LocalValue<?>> paramLocals,
                 Map<Integer, LocalValue<?>> existingLocals,
                 Method method,
                 MethodParams methodParams) {
        this.builderLocals = builderLocals;
        this.paramLocals = paramLocals;
        this.existingLocals = existingLocals;
        this.method = method;
        this.methodParams = methodParams;
    }

    @SuppressWarnings("unchecked")
    public <T> LocalValue<T> param(int slot, Class<T> type) {
        if (!paramLocals.containsKey(slot)) {
            paramLocals.put(slot, new LocalValue<>("$" + slot, type));
        }
        return (LocalValue<T>) paramLocals.get(slot);
    }

    @SuppressWarnings("unchecked")
    public <T> LocalValue<T> local(int slot) {
        return (LocalValue<T>) existingLocals.get(slot);
    }

    @SuppressWarnings("unchecked")
    public <T> LocalValue<T> addLocal(String name, Class<T> type) {
        if (!builderLocals.containsKey(name)) {
            builderLocals.put(name, new LocalValue<>(name, type));
        }
        return (LocalValue<T>) builderLocals.get(name);
    }

    public void addLine(String line) {
        lines.add(line);
    }

    public void ifThen(String condition, Runnable body) {
        lines.add("if (" + condition + ") {");
        body.run();
        lines.add("}");
    }

    public void ifElse(String condition, Runnable thenBody, Runnable elseBody) {
        lines.add("if (" + condition + ") {");
        thenBody.run();
        lines.add("} else {");
        elseBody.run();
        lines.add("}");
    }

    public void whileLoop(String condition, Runnable body) {
        lines.add("while (" + condition + ") {");
        body.run();
        lines.add("}");
    }

    public void doWhileLoop(Runnable doBody, String condition, Runnable whileBody) {
        lines.add("do {");
        doBody.run();
        lines.add("} while (" + condition + ") {");
        whileBody.run();
        lines.add("}");
    }

    public void forLoop(String init, String condition, String update, Runnable body) {
        lines.add("for (" + init + "; " + condition + "; " + update + ") {");
        body.run();
        lines.add("}");
    }

    public void increase(String value) {
        lines.add(value + "++;");
    }

    public void decrease(String value) {
        lines.add(value + "--;");
    }
    public void tryCatch(Runnable tryBody, Class<? extends Throwable> exceptionType, String exceptionName, Runnable catchBody) {
        lines.add("try {");
        tryBody.run();
        lines.add("} catch (" + exceptionType.getSimpleName() + " " + exceptionName + ") {");
        catchBody.run();
        lines.add("}");
    }
    public void tryCatchFinally(Runnable tryBody, Class<? extends Throwable> exceptionType, String exceptionName, Runnable catchBody, Runnable finallyBody) {
        lines.add("try {");
        tryBody.run();
        lines.add("} catch (" + exceptionType.getSimpleName() + " " + exceptionName + ") {");
        catchBody.run();
        lines.add("} finally {");
        finallyBody.run();
        lines.add("}");
    }
    public void switchCase(String expression, Map<String, Runnable> cases, Runnable defaultCase) {
        lines.add("switch (" + expression + ") {");
        for (Map.Entry<String, Runnable> entry : cases.entrySet()) {
            lines.add("case " + entry.getKey() + ":");
            entry.getValue().run();
            lines.add("break;");
        }
        lines.add("default:");
        defaultCase.run();
        lines.add("}");
    }

    public void println(Object... args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(" + ");
            Object e = args[i];
            if (e instanceof String && !((String) e).startsWith("$") && !((String) e).contains("(")) {
                sb.append("\"").append(e).append("\"");
            } else {
                sb.append(e);
            }
        }
        addLine("System.out.println(" + sb + ");");
    }

    public void returnVoid() {
        lines.add("return;");
    }

    public void returnValue(String value) {
        lines.add("return " + value + ";");
    }

    public String build() {
        return String.join("\n", lines);
    }

    public void buildInvoke() {
        String invokeMethod = method.getDeclaringClass().getName() + "." + method.getName() + "(" + methodParams.build() + ");";
        lines.add(invokeMethod);
    }

    public String getInvokeExpr() {
        return method.getDeclaringClass().getName() + "." + method.getName() + "(" + methodParams.build() + ")";
    }

    @FunctionalInterface
    public interface CodeBlockConsumer {
        void accept(Block block);
    }
}
