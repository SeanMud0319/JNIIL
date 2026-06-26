package top.nontage.jniil.test.injector.instruction;

import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import top.nontage.jniil.annotations.At;
import top.nontage.jniil.annotations.InjectMethodInfo;
import top.nontage.jniil.injector.insn.InsnContext;
import top.nontage.jniil.interfaces.InsnInjectable;
import top.nontage.jniil.test.target.InstructionTarget;

import static org.objectweb.asm.Opcodes.BIPUSH;
import static org.objectweb.asm.Opcodes.POP;

/**
 * INSTRUCTION INJECTOR EXAMPLE 1: BIPUSH Replacement
 *
 * <p>This injector targets the {@code BIPUSH} (Byte Immediate PUSH) instruction
 * in the {@code InstructionTarget.processReward(int)} method.</p>
 *
 * <p><b>What it does:</b></p>
 * <ul>
 *   <li>Anchors at the BIPUSH instruction with {@code shiftAfter = true}</li>
 *   <li>Pops the original constant (10) off the operand stack</li>
 *   <li>Pushes a forced value of 5 instead</li>
 * </ul>
 *
 * <p><b>Effect on program behavior:</b></p>
 * <ul>
 *   <li>Original: {@code if (points >= 10)} -> false for points=5</li>
 *   <li>Modified: {@code if (points >= 5)} -> true for points=5</li>
 *   <li>Result: if-block executes instead of else-block</li>
 * </ul>
 *
 * <p><b>Design Note:</b></p>
 * Unlike FunctionalInjector which can have multiple methods in one class,
 * each Instruction injector class can only target ONE injection point.
 * Therefore, each injection scenario has its own separate class file.
 *
 * <p><b>Operational Principle:</b></p>
 * This class handles low-level runtime bytecode manipulation using the ASM tree API.
 * The framework intercepts the target method's instruction list (InsnList) as an ASM
 * MethodNode, locates the bytecode anchor specified by the {@code @At} annotation,
 * then directly links your custom InsnList into the underlying JVM execution stack
 * at runtime.
 *
 * @see InsnInjectable
 * @see At
 * @see InjectMethodInfo
 */
public class InstructionBipushExample implements InsnInjectable {

    /*
     * @At(opcode = BIPUSH, shiftAfter = true):
     * Targets the BIPUSH instruction. Since shiftAfter is set to true, the framework anchors
     * the injection point immediately AFTER the BIPUSH opcode node.
     *
     * This implementation pops the original constant (10) off the operand stack
     * and pushes 5 instead.
     */
    @InjectMethodInfo(
            targetType = InstructionTarget.class,
            targetMethodName = "processReward",
            targetMethodParamTypes = {int.class}
    )
    @At(opcode = BIPUSH, shiftAfter = true)
    @Override
    public InsnList apply(InsnContext ctx, InsnList insns) {
        // Stack before: [10] (the BIPUSH constant)
        insns.add(new InsnNode(POP));            // Pop the original 10 off the stack
        insns.add(new VarInsnNode(BIPUSH, 5));   // Push our forced value 5 instead
        // Stack after: [5]
        return insns;
    }
}