package top.nontage.jniil.injector.functional;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.*;

public class LocalVariableValidator {

    private final MethodNode method;

    public LocalVariableValidator(MethodNode method) {
        this.method = method;
    }

    public void validate(String[] localsToCapture, int injectionLine, boolean shiftAfter) {
        if (localsToCapture.length == 0) return;

        Map<String, Integer> localVarSlots = getLocalVarSlots();
        List<String> missing = new ArrayList<>();
        List<String> notInScope = new ArrayList<>();

        int effectiveLine = injectionLine;
        if (shiftAfter && injectionLine >= 0) {
            effectiveLine = injectionLine + 1;
        }

        for (String localName : localsToCapture) {
            if (!localVarSlots.containsKey(localName)) {
                missing.add(localName);
                continue;
            }

            if (!isLocalVariableLive(effectiveLine, localName)) {
                notInScope.add(localName);
            }
        }

        if (!missing.isEmpty() || !notInScope.isEmpty()) {
            throw buildException(missing, notInScope, injectionLine, shiftAfter);
        }
    }

    private boolean isLocalVariableLive(int line, String localName) {
        if (method.localVariables == null) return false;

        for (LocalVariableNode lv : method.localVariables) {
            if (lv.name.equals(localName)) {
                if (line == -1) return lv.start == null;
                if (line == Integer.MAX_VALUE) return true;

                int startLine = getLineNumberOfLabel(lv.start);
                int endLine = getLineNumberOfLabel(lv.end);
                return line >= startLine && line < endLine;
            }
        }
        return false;
    }

    private int getLineNumberOfLabel(LabelNode label) {
        if (label == null) return -1;

        for (AbstractInsnNode insn = method.instructions.getFirst();
             insn != null; insn = insn.getNext()) {
            if (insn == label) {
                AbstractInsnNode prev = insn.getPrevious();
                while (prev != null) {
                    if (prev instanceof LineNumberNode) {
                        return ((LineNumberNode) prev).line;
                    }
                    prev = prev.getPrevious();
                }
                break;
            }
        }
        return -1;
    }

    private Map<String, Integer> getLocalVarSlots() {
        Map<String, Integer> slots = new HashMap<>();
        if (method.localVariables != null) {
            for (LocalVariableNode lv : method.localVariables) {
                if (!lv.name.equals("this")) {
                    slots.put(lv.name, lv.index);
                }
            }
        }
        return slots;
    }

    private IllegalStateException buildException(List<String> missing, List<String> notInScope,
                                                 int injectionLine, boolean shiftAfter) {
        StringBuilder sb = new StringBuilder();
        sb.append("Target method: ").append(method.name).append(method.desc).append("\n");
        sb.append("Injection point: ");

        if (injectionLine == -1) {
            sb.append("@Before (method start)\n");
            sb.append("  Warning: @Before can only capture method parameters!\n");
        } else if (injectionLine == Integer.MAX_VALUE) {
            sb.append("@After (method end)\n");
        } else {
            sb.append("@At(line = ").append(injectionLine);
            if (shiftAfter) sb.append(", shiftAfter = true");
            sb.append(")\n");
        }

        if (!missing.isEmpty()) {
            sb.append("\n Variables not found:\n");
            for (String name : missing) sb.append("   • ").append(name).append("\n");
            sb.append("\n Check spelling or recompile with -g\n");
        }

        if (!notInScope.isEmpty()) {
            sb.append("\n Variables not in scope:\n");
            for (String name : notInScope) sb.append("   • ").append(name).append("\n");
            sb.append("\n Move @At to a line AFTER the variable is defined\n");
        }

        sb.append("\n Available locals:\n");
        if (method.localVariables == null || method.localVariables.isEmpty()) {
            sb.append("   (No debug info. Recompile with -g)\n");
        } else {
            for (LocalVariableNode lv : method.localVariables) {
                if (!lv.name.equals("this")) {
                    sb.append("   • ").append(lv.name)
                            .append(" : ").append(Type.getType(lv.desc).getClassName())
                            .append("\n");
                }
            }
        }

        return new IllegalStateException(sb.toString());
    }
}