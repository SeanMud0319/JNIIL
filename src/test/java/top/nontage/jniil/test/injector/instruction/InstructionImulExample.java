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
import static org.objectweb.asm.Opcodes.IMUL;
import static org.objectweb.asm.Opcodes.POP;

/**
 * INSTRUCTION INJECTOR EXAMPLE 2: IMUL Operand Manipulation
 *
 * <p>This injector targets the {@code IMUL} (Integer Multiplication) instruction
 * in the {@code InstructionTarget.calculateBonus(int, int)} method.</p>
 *
 * <p><b>What it does:</b></p>
 * <ul>
 *   <li>Anchors at the IMUL instruction with {@code shiftAfter = false} (default)</li>
 *   <li>Pops the original multiplier off the operand stack</li>
 *   <li>Pushes a forced multiplier value of 5 instead</li>
 * </ul>
 *
 * <p><b>Effect on program behavior:</b></p>
 * <ul>
 *   <li>Original: {@code (base + 10) * multiplier} where multiplier is passed as argument</li>
 *   <li>Modified: {@code (base + 10) * 5} regardless of the passed multiplier</li>
 *   <li>Result: calculateBonus(100, 2) returns 550 instead of 220</li>
 * </ul>
 *
 * <p><b>Stack manipulation details:</b></p>
 * <pre>
 * Before IMUL: [..., (base + 10), multiplier]  (multiplier is at the top)
 * After POP:   [..., (base + 10)]
 * After BIPUSH: [..., (base + 10), 5]
 * After IMUL:  [..., (base + 10) * 5]
 * </pre>
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
public class InstructionImulExample implements InsnInjectable {

    /*
     * @At(opcode = IMUL, shiftAfter = false):
     * Anchors the injection point right BEFORE the IMUL (Integer Multiplication) opcode occurs.
     * (shiftAfter = false is the default behavior)
     *
     * This implementation pops the original 'multiplier' from the top of the stack and
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
        // Stack layout before IMUL: [..., (base + 10), multiplier]
        //                                         ↑ multiplier is at the top
        insns.add(new InsnNode(POP));            // Pop the original multiplier off the stack
        insns.add(new VarInsnNode(BIPUSH, 5));   // Push a forced multiplier value of 5
        // Stack layout after injection: [..., (base + 10), 5]
        // The original IMUL will now multiply by 5 instead of the original multiplier
        return insns;
    }
}