package top.nontage.jniil.injector.functional.internal;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.Printer;
import top.nontage.jniil.annotations.After;
import top.nontage.jniil.annotations.At;
import top.nontage.jniil.annotations.Before;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class InjectionPointResolver {

    private final MethodNode targetMethod;
    private final Before before;
    private final After after;
    private final At at;

    public InjectionPointResolver(MethodNode targetMethod, Method injectionMethod) {
        this.targetMethod = targetMethod;
        this.before = injectionMethod.getAnnotation(Before.class);
        this.after = injectionMethod.getAnnotation(After.class);
        this.at = injectionMethod.getAnnotation(At.class);
    }

    public InjectionType getType() {
        if (before != null) return InjectionType.BEFORE;
        if (after != null) return InjectionType.AFTER;
        if (at != null && at.override()) {
            throw new UnsupportedOperationException("Override attribute is not available in FunctionalInjector.");
        }
        if (at != null && at.line() >= 0) return InjectionType.AT_LINE;
        if (at != null && at.opcode() != 114514) return InjectionType.AT_OPCODE;
        //return InjectionType.BEFORE;
        throw new IllegalArgumentException("Missing injection point annotation (@Before, @After, or @At)");
    }

    public int getInjectionLine() {
        if (before != null) return -1;
        if (after != null) return Integer.MAX_VALUE;
        if (at != null && at.line() >= 0) return at.line();
        return -1;
    }

    public boolean isShiftAfter() {
        return at != null && at.shiftAfter();
    }

    public void inject(InsnList code) {
        switch (getType()) {
            case BEFORE:
                targetMethod.instructions.insert(code);
                break;
            case AFTER:
                insertAfter(code);
                break;
            case AT_LINE:
                insertAtLine(at.line(), code);
                break;
            case AT_OPCODE:
                insertAtOpcode(code);
                break;
        }
    }

    private void insertAfter(InsnList toInsert) {
        AbstractInsnNode lastReturn = null;
        for (AbstractInsnNode insn = targetMethod.instructions.getLast();
             insn != null; insn = insn.getPrevious()) {
            int opcode = insn.getOpcode();
            if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
                lastReturn = insn;
                break;
            }
        }

        if (lastReturn != null) {
            targetMethod.instructions.insertBefore(lastReturn, toInsert);
        } else {
            targetMethod.instructions.add(toInsert);
        }
    }

    private void insertAtLine(int line, InsnList toInsert) {
        AbstractInsnNode target = null;
        for (AbstractInsnNode insn = targetMethod.instructions.getFirst();
             insn != null; insn = insn.getNext()) {
            if (insn instanceof LineNumberNode && ((LineNumberNode) insn).line == line) {
                target = insn;
                break;
            }
        }

        if (target == null) {
            throw new RuntimeException("Line " + line + " not found in method " + targetMethod.name);
        }

        if (at.shiftAfter()) {
            targetMethod.instructions.insert(target, toInsert);
        } else {
            targetMethod.instructions.insertBefore(target, toInsert);
        }
    }

    private void insertAtOpcode(InsnList toInsert) {
        AbstractInsnNode anchor = findAnchorByAt(targetMethod, at);
        if (at.shiftAfter()) {
            targetMethod.instructions.insert(anchor, toInsert);
        } else {
            targetMethod.instructions.insertBefore(anchor, toInsert);
        }
    }

    // InstructionInjector in default loader and FunctionalInjector in bootloader so we cant direct access it.
    private static AbstractInsnNode findAnchorByAt(MethodNode mn, At at) {
        int targetLine = at.line();
        if (targetLine >= 0) {
            if (at.debug()) {
                System.out.println("[JNIIL-DEBUG] Looking for line number: " + targetLine);
            }
            return getAbstractInsnNode(mn, targetLine);
        }

        int targetOpcode = at.opcode();
        String targetId = at.identifier();
        int targetOrdinal = at.ordinal();
        boolean debug = at.debug();

        if (targetOpcode == 114514 || targetOpcode <= 0) {
            throw new IllegalArgumentException(String.format(
                    "Illegal @At configuration in method %s: Opcode %d is invalid! " +
                            "You must specify a valid opcode to locate an anchor.",
                    mn.name, targetOpcode
            ));
        }

        String targetOpcodeName = targetOpcode < Printer.OPCODES.length
                ? Printer.OPCODES[targetOpcode]
                : "UNKNOWN_OP_" + targetOpcode;

        if (debug) {
            System.out.println("[JNIIL-DEBUG] Scanning method: " + mn.name + mn.desc);
            System.out.println("[JNIIL-DEBUG] Target: Opcode=" + targetOpcodeName + "(" + targetOpcode + "), ID=" + targetId + ", Ordinal=" + targetOrdinal);
        }

        List<AbstractInsnNode> candidates = new ArrayList<>();
        AbstractInsnNode[] allInsns = mn.instructions.toArray();

        for (int i = 0; i < allInsns.length; i++) {
            AbstractInsnNode insn = allInsns[i];

            if (insn.getOpcode() == targetOpcode) {
                boolean idMatch = (targetId == null || targetId.isEmpty() || checkIdentifierSafe(insn, targetId));

                if (debug)
                    System.out.println("[JNIIL-DEBUG] Found potential match at index " + i + " (ID Match: " + idMatch + ")");

                if (idMatch) {
                    candidates.add(insn);
                }
            }
        }

        if (candidates.isEmpty()) {
            throw new RuntimeException(String.format(
                    "Injection error: No occurrences of opcode %s(%d) found in method %s.",
                    targetOpcodeName, targetOpcode, mn.name
            ));
        }

        try {
            return candidates.get(targetOrdinal - 1);
        } catch (IndexOutOfBoundsException e) {
            throw new IndexOutOfBoundsException(String.format(
                    "Injection error: @At(opcode=%s, ordinal=%d) failed in method %s. Only %d occurrence(s) found.",
                    targetOpcodeName, targetOrdinal, mn.name, candidates.size()
            ));
        }
    }

    private static AbstractInsnNode getAbstractInsnNode(MethodNode mn, int targetLine) {
        AbstractInsnNode target = null;
        for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof LineNumberNode) {
                LineNumberNode ln = (LineNumberNode) insn;
                if (ln.line == targetLine) {
                    target = insn;
                    break;
                }
            }
        }

        if (target == null) {
            throw new RuntimeException(String.format(
                    "Injection error: Line %d not found in method %s.",
                    targetLine, mn.name
            ));
        }
        return target;
    }

    private static boolean checkIdentifierSafe(AbstractInsnNode insn, String id) {
        if (id == null || id.isEmpty()) return true;

        String normalizedId = id.replace('/', '.');

        if (insn instanceof FieldInsnNode) {
            FieldInsnNode f = (FieldInsnNode) insn;
            String ownerDotted = f.owner.replace('/', '.');
            String fullName = ownerDotted + "." + f.name;
            return normalizedId.equals(f.name) || normalizedId.equals(fullName) || normalizedId.equals(ownerDotted);
        }

        if (insn instanceof MethodInsnNode) {
            MethodInsnNode m = (MethodInsnNode) insn;
            String ownerDotted = m.owner.replace('/', '.');
            String fullName = ownerDotted + "." + m.name;
            return normalizedId.equals(m.name) || normalizedId.equals(fullName) || normalizedId.equals(ownerDotted);
        }

        if (insn instanceof TypeInsnNode) {
            String typeDotted = ((TypeInsnNode) insn).desc.replace('/', '.');
            return typeDotted.equals(normalizedId) || typeDotted.endsWith("." + normalizedId);
        }

        if (insn instanceof LdcInsnNode) {
            Object cst = ((LdcInsnNode) insn).cst;
            if (cst instanceof String) {
                return ((String) cst).replace('/', '.').contains(normalizedId);
            }
            return cst != null && cst.toString().equals(id);
        }

        if (insn instanceof VarInsnNode) {
            return id.equals(String.valueOf(((VarInsnNode) insn).var));
        }

        return false;
    }


    public enum InjectionType {
        BEFORE, AFTER, AT_LINE, AT_OPCODE
    }
}