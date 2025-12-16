package top.nontage.jniil.builder;

import javassist.CtMethod;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.LocalVariableAttribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import top.nontage.auth.library.annotation.Protect;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Protect
public class ExprBuilder {

    private Method method;
    private MethodParams methodParams;
    private final Set<String> imports = new HashSet<>();
    private final Map<String, LocalValue<?>> builderLocals = new LinkedHashMap<>();
    private final Map<Integer, LocalValue<?>> paramLocals = new LinkedHashMap<>();
    private final Map<Integer, Map<String, LocalValue<?>>> existingLocals = new LinkedHashMap<>();
    private CtMethod ctMethod;
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

    public ExprBuilder imports(Import... imports) {
        for (Import imp : imports) {
            this.imports.add(imp.getClassName());
        }
        return this;
    }

    @SuppressWarnings({"unchecked", "UnusedReturnValue"})
    public <T> LocalValue<T> local(int slot, String name, Class<T> type) {
        existingLocals.computeIfAbsent(slot, k -> new LinkedHashMap<>());
        Map<String, LocalValue<?>> slotMap = existingLocals.get(slot);
        slotMap.computeIfAbsent(name, k -> new LocalValue<>(name, type));
        return (LocalValue<T>) slotMap.get(name);
    }


    @SuppressWarnings("unchecked")
    public <T> LocalValue<T> addLocal(String name, Class<T> type) {
        if (!builderLocals.containsKey(name)) {
            builderLocals.put(name, new LocalValue<>(name, type));
        }
        return (LocalValue<T>) builderLocals.get(name);
    }

    public ExprBuilder codeBlock(Block.CodeBlockConsumer consumer) {
        this.block = new Block(builderLocals, paramLocals, existingLocals, method, methodParams, this.ctMethod);
        consumer.accept(block);
        return this;
    }

    public String compile() {
        StringBuilder sb = new StringBuilder();

        for (LocalValue<?> lv : builderLocals.values()) {
            String typeName = lv.getType().getSimpleName();

            for (String fullName : imports) {
                String shortName = fullName.substring(fullName.lastIndexOf('.') + 1);
                if (typeName.equals(shortName)) {
                    typeName = fullName;
                    break;
                }
            }

            sb.append(typeName)
                    .append(" ")
                    .append(lv.get())
                    .append(";")
                    .append("\n");
        }

        String code = block != null ? block.build() : "";

        for (String fullName : imports) {
            String shortName = fullName.substring(fullName.lastIndexOf('.') + 1);
            code = code.replaceAll("\\b" + shortName + "\\b", fullName);
        }

        sb.append(code);
        return sb.toString();
    }


    public void extractExistingLocals(byte[] classBytes, CtMethod ctMethod) {
        try {
            this.ctMethod = ctMethod;
            ClassReader reader = new ClassReader(classBytes);
            ClassNode classNode = new ClassNode();
            reader.accept(classNode, 0);
            for (MethodNode mn : classNode.methods) {
                if (!mn.name.equals(ctMethod.getName())) continue;

                System.out.println("Extracting existing locals from method: " + mn.name);

                if (mn.localVariables == null) {
                    System.out.println("No local variable table found in ASM.");
                    continue;
                }

                for (LocalVariableNode var : mn.localVariables) {
                    if (var.name.equals("this")) continue;

                    System.out.println("Extracted local variable: slot=" + var.index + ", name=" + var.name);
                    this.local(var.index, var.name, Object.class);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void extractExistingLocals(CtMethod ctMethod) {
        try {
            this.ctMethod = ctMethod;
            CodeAttribute codeAttr = ctMethod.getMethodInfo().getCodeAttribute();
            if (codeAttr == null) return;
            LocalVariableAttribute attr = (LocalVariableAttribute) codeAttr.getAttribute(LocalVariableAttribute.tag);
            if (attr != null) {
                for (int i = 0; i < attr.tableLength(); i++) {
                    String name = attr.variableName(i);
                    if (name != null && !name.equals("this")) {
                        local(i, name, Object.class);
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
