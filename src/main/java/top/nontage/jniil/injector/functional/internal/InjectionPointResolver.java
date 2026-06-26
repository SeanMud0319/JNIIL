package top.nontage.jniil.injector.functional.internal;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import top.nontage.jniil.annotations.After;
import top.nontage.jniil.annotations.At;
import top.nontage.jniil.annotations.Before;

import java.lang.reflect.Method;

import static top.nontage.jniil.injector.insn.InstructionInjector.findAnchorByAt;

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
        if (at != null && at.line() >= 0) return InjectionType.AT_LINE;
        if (at != null && at.opcode() != 114514) return InjectionType.AT_OPCODE;
        return InjectionType.BEFORE;
        //throw new IllegalArgumentException("Missing injection point annotation (@Before, @After, or @At)");
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

    public enum InjectionType {
        BEFORE, AFTER, AT_LINE, AT_OPCODE
    }
}