package top.nontage.jniil.injector.functional.internal;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.Printer;
import top.nontage.jniil.annotations.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class InjectionPointResolver {

    private final MethodNode targetMethod;
    private final Before before;
    private final After after;
    private final At at;
    private final Overwrite overwrite;
    private final InvokeRedirect invokeRedirect;

    public InjectionPointResolver(MethodNode targetMethod, Method injectionMethod) {
        this.targetMethod = targetMethod;
        this.before = injectionMethod.getAnnotation(Before.class);
        this.after = injectionMethod.getAnnotation(After.class);
        this.at = injectionMethod.getAnnotation(At.class);
        this.overwrite = injectionMethod.getAnnotation(Overwrite.class);
        this.invokeRedirect = injectionMethod.getAnnotation(InvokeRedirect.class);
    }

    public InjectionType getType() {
        if (before != null) return InjectionType.BEFORE;
        if (after != null) return InjectionType.AFTER;
        if (at != null && at.override()) {
            throw new UnsupportedOperationException("Override attribute is not available in FunctionalInjector.");
        }
        if (at != null && at.line() >= 0) return InjectionType.AT_LINE;
        if (at != null && at.opcode() != 114514) return InjectionType.AT_OPCODE;
        if (overwrite != null) return InjectionType.OVERWRITE;
        if (invokeRedirect != null) return InjectionType.INVOKE_REDIRECT;
        throw new IllegalArgumentException("Missing injection point annotation (@Before, @After, @At, @Overwrite or @InvokeRedirect)");
    }

    public int getInjectionLine() {
        if (before != null || overwrite != null) return -1;
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
            case OVERWRITE:
                targetMethod.instructions.clear();
                if (targetMethod.tryCatchBlocks != null) targetMethod.tryCatchBlocks.clear();
                if (targetMethod.localVariables != null) targetMethod.localVariables.clear();
                if (targetMethod.visibleLocalVariableAnnotations != null)
                    targetMethod.visibleLocalVariableAnnotations.clear();
                if (targetMethod.invisibleLocalVariableAnnotations != null)
                    targetMethod.invisibleLocalVariableAnnotations.clear();
                targetMethod.instructions.insert(code);
                break;
            case INVOKE_REDIRECT:
                insertInvokeRedirect(code);
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

    private void insertInvokeRedirect(InsnList toInsert) {
        if (invokeRedirect == null) {
            throw new IllegalStateException("Not an @InvokeRedirect injection point");
        }

        At redirectAt = new At() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return At.class;
            }

            @Override
            public int line() {
                return -1;
            }

            @Override
            public int opcode() {
                return invokeRedirect.value().getValue();
            }

            @Override
            public String identifier() {
                return invokeRedirect.target();
            }

            @Override
            public int ordinal() {
                return invokeRedirect.ordinal();
            }

            @Override
            public boolean shiftAfter() {
                return false;
            }

            @Override
            public boolean override() {
                return false;
            }

            @Override
            public boolean debug() {
                return false;
            }
        };

        AbstractInsnNode anchor = findAnchorByAt(targetMethod, redirectAt);
        if (!(anchor instanceof MethodInsnNode)) {
            throw new IllegalStateException(
                    "@InvokeRedirect matched a non-method instruction: " + anchor.getClass().getSimpleName());
        }
        targetMethod.instructions.insertBefore(anchor, toInsert);
        targetMethod.instructions.remove(anchor);
    }

    public MethodInsnNode resolveInvokeRedirectAnchor() {
        if (invokeRedirect == null) {
            throw new IllegalStateException("Not an @InvokeRedirect injection point");
        }

        At redirectAt = new At() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return At.class;
            }

            @Override
            public int line() {
                return -1;
            }

            @Override
            public int opcode() {
                return invokeRedirect.value().getValue();
            }

            @Override
            public String identifier() {
                return invokeRedirect.target();
            }

            @Override
            public int ordinal() {
                return invokeRedirect.ordinal();
            }

            @Override
            public boolean shiftAfter() {
                return false;
            }

            @Override
            public boolean override() {
                return false;
            }

            @Override
            public boolean debug() {
                return false;
            }
        };

        AbstractInsnNode anchor = findAnchorByAt(targetMethod, redirectAt);
        if (!(anchor instanceof MethodInsnNode)) {
            throw new IllegalStateException(
                    "@InvokeRedirect matched a non-method instruction: " + anchor.getClass().getSimpleName());
        }
        return (MethodInsnNode) anchor;
    }

    public void replaceInvokeRedirectAnchor(MethodInsnNode anchor, InsnList generated) {
        targetMethod.instructions.insertBefore(anchor, generated);
        targetMethod.instructions.remove(anchor);
    }

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
                    "Injection error: No occurrences of opcode %s(%d) '%s' found in method %s.",
                    targetOpcodeName, targetOpcode, targetId, mn.name
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

        String normalizedId = id.replace('/', '.').replace(" ", "");

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
            String fullNameWithDesc = fullName + m.desc;
            String nameWithDesc = m.name + m.desc;

            return normalizedId.equals(m.name)
                    || normalizedId.equals(fullName)
                    || normalizedId.equals(ownerDotted)
                    || normalizedId.equals(fullNameWithDesc)
                    || normalizedId.equals(nameWithDesc);
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
        BEFORE, AFTER, AT_LINE, AT_OPCODE, OVERWRITE, INVOKE_REDIRECT
    }
}