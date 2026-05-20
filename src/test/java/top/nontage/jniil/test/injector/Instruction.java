package top.nontage.jniil.test.injector;

import org.objectweb.asm.tree.*;
import top.nontage.jniil.annotations.At;
import top.nontage.jniil.annotations.InjectMethodInfo;
import top.nontage.jniil.injector.insn.InsnContext;
import top.nontage.jniil.interfaces.InsnInjectable;
import top.nontage.jniil.test.target.InstructionTarget;

/*
 * Instruction (ASM Bytecode Injector) Demonstration
 * * Operational Principle:
 * This class handles low-level runtime bytecode manipulation using the ASM tree API.
 * Unlike Javassist-based injectors that compile raw Java source strings, this
 * framework intercepts the target method's instruction list (InsnList) as an ASM
 * MethodNode. It iterates through the sequence to locate the bytecode anchor
 * specified by the @At annotation, then directly links your custom InsnList into
 * the underlying JVM execution stack at runtime.
 */
public class Instruction implements InsnInjectable {

    /*
     * @At(opcode = BIPUSH, shiftAfter = true):
     * Targets the BIPUSH instruction. Since shiftAfter is set to true, the framework anchors
     * the injection point immediately AFTER the BIPUSH opcode node.
     * * This implementation pops the original constant (10) off the operand stack and pushes 5 instead.
     */
    @InjectMethodInfo(
            targetType = InstructionTarget.class,
            targetMethodName = "processReward",
            targetMethodParamTypes = {int.class}
    )
    @At(opcode = BIPUSH, shiftAfter = true)
    @Override
    public InsnList apply(InsnContext ctx, InsnList insns) {
        insns.add(new InsnNode(POP));
        insns.add(new VarInsnNode(BIPUSH, 5));
        return insns;
    }

    /**
     * Example 2: Target arithmetic instructions inside a multi-line mathematical calculation method.
     */
    public static class Instruction2 implements InsnInjectable {

        /*
         * @At(opcode = IMUL, shiftAfter = false):
         * Anchors the injection point right BEFORE the IMUL (Integer Multiplication) opcode occurs.
         * * This implementation pops the original 'multiplier' from the top of the stack and
         * forces it to be 5, modifying the calculation result dynamically.
         */
        @InjectMethodInfo(
                targetType = InstructionTarget.class,
                targetMethodName = "calculateBonus",
                targetMethodParamTypes = {int.class, int.class}
        )
        @At(opcode = IMUL)
        @Override
        public InsnList apply(InsnContext ctx, InsnList insns) {
            // Stack layout before IMUL: [..., (base + 10), multiplier] -> multiplier is at the top.
            insns.add(new InsnNode(POP));            // Pop the original multiplier off the stack
            insns.add(new VarInsnNode(BIPUSH, 5));   // Push a forced multiplier value of 5
            return insns;
        }
    }

    /**
     * Example 3: Target field mutation boundaries within a toggling method.
     */
    public static class Instruction3 implements InsnInjectable {

        /*
         * @At(opcode = PUTFIELD, shiftAfter = true):
         * Anchors the injection point immediately AFTER a PUTFIELD opcode operation concludes.
         * * This implementation injects a custom System.out.println call right after the field is updated.
         */
        @InjectMethodInfo(
                targetType = InstructionTarget.class,
                targetMethodName = "toggleStatus"
        )
        @At(opcode = PUTFIELD, shiftAfter = true)
        @Override
        public InsnList apply(InsnContext ctx, InsnList insns) {
            // Injects: System.out.println("[ASM] Field mutation detected via PUTFIELD!");
            insns.add(new FieldInsnNode(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;"));
            insns.add(new LdcInsnNode("[ASM] Field mutation detected via PUTFIELD!"));
            insns.add(new MethodInsnNode(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false));
            return insns;
        }
    }
}