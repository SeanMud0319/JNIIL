package top.nontage.jniil.builder;

import javassist.CtMethod;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.LocalVariableAttribute;
import top.nontage.auth.library.annotation.Protect;

import java.lang.reflect.Method;
import java.util.*;

@Protect
public class ExprBuilder {

    private Method method;
    private MethodParams methodParams;

    private final Map<String, LocalValue<?>> builderLocals = new LinkedHashMap<>();
    private final Map<Integer, LocalValue<?>> paramLocals = new LinkedHashMap<>();
    private final Map<Integer, LocalValue<?>> existingLocals = new LinkedHashMap<>();

    private Block block;

    private ExprBuilder() {
    }

    public static ExprBuilder begin() {
        return new ExprBuilder();
    }

    public ExprBuilder invoke(Method method, MethodParams params) {
        this.method = method;
        this.methodParams = params;
        return this;
    }


    @SuppressWarnings("unchecked")
    public <T> LocalValue<T> param(int slot, Class<T> type) {
        if (!paramLocals.containsKey(slot)) {
            paramLocals.put(slot, new LocalValue<>("$" + slot, type));
        }
        return (LocalValue<T>) paramLocals.get(slot);
    }

    @SuppressWarnings({"unchecked", "UnusedReturnValue"})
    public <T> LocalValue<T> local(int slot, Class<T> type) {
        if (!existingLocals.containsKey(slot)) {
            existingLocals.put(slot, new LocalValue<>("$" + slot, type));
        }
        return (LocalValue<T>) existingLocals.get(slot);
    }

    @SuppressWarnings("unchecked")
    public <T> LocalValue<T> addLocal(String name, Class<T> type) {
        if (!builderLocals.containsKey(name)) {
            builderLocals.put(name, new LocalValue<>(name, type));
        }
        return (LocalValue<T>) builderLocals.get(name);
    }

    public ExprBuilder codeBlock(Block.CodeBlockConsumer consumer) {
        this.block = new Block(builderLocals, paramLocals, existingLocals, method, methodParams);
        consumer.accept(block);
        return this;
    }

    public String compile() {
        StringBuilder sb = new StringBuilder();
        for (LocalValue<?> lv : builderLocals.values()) {
            sb.append(lv.getType().getSimpleName())
                    .append(" ")
                    .append(lv.get())
                    .append(";")
                    .append("\n");
        }
        if (block != null) sb.append(block.build());
        return sb.toString();
    }

    public void extractExistingLocals(CtMethod ctMethod) {
        try {
            CodeAttribute codeAttr = ctMethod.getMethodInfo().getCodeAttribute();
            if (codeAttr == null) return;
            LocalVariableAttribute attr = (LocalVariableAttribute) codeAttr.getAttribute(LocalVariableAttribute.tag);
            if (attr != null) {
                for (int i = 0; i < attr.tableLength(); i++) {
                    String name = attr.variableName(i);
                    if (name != null && !name.equals("this")) {
                        local(i, Object.class);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    public Map<String, LocalValue<?>> getBuilderLocals() {
        return builderLocals;
    }

    @FunctionalInterface
    public interface CodeBlockConsumer {
        void accept(Block block);
    }
}
