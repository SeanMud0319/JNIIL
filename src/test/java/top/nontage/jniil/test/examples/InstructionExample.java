package top.nontage.jniil.test.examples;

import top.nontage.jniil.agent.JNIILBootstrap;
import top.nontage.jniil.injector.insn.InstructionInjector;
import top.nontage.jniil.test.injector.instruction.InstructionBipushExample;
import top.nontage.jniil.test.injector.instruction.InstructionImulExample;
import top.nontage.jniil.test.injector.instruction.InstructionPutfieldExample;
import top.nontage.jniil.test.target.InstructionTarget;

/**
 * INSTRUCTION INJECTOR EXAMPLE (ASM-based)
 *
 * <p>This example demonstrates the {@code InstructionInjector},
 * which uses ASM for low-level bytecode manipulation at the instruction level.</p>
 *
 * <p><b>When to use InstructionInjector:</b></p>
 * <ul>
 *   <li>You need precise control over individual bytecode instructions</li>
 *   <li>You understand JVM opcodes and operand stack mechanics</li>
 *   <li>You need to modify complex method structures</li>
 * </ul>
 *
 * <p><b>Three injection scenarios demonstrated:</b></p>
 * <ol>
 *   <li>{@link InstructionBipushExample} - BIPUSH replacement (condition modification)</li>
 *   <li>{@link InstructionImulExample} - IMUL operand manipulation</li>
 *   <li>{@link InstructionPutfieldExample} - PUTFIELD field access tracing</li>
 * </ol>
 *
 * <p><b>Design Note:</b></p>
 * Unlike FunctionalInjector which can have multiple methods in one class,
 * each Instruction injector class can only target ONE injection point.
 * Therefore, each injection scenario has its own separate class file.
 *
 * <p><b>Prerequisite:</b> Basic understanding of JVM bytecode is recommended
 * before using this injector type.</p>
 *
 * <p><b>Important:</b> By using this library, it is assumed that you fully understand
 * what you are about to do. Improper usage can lead to critical, hard-to-debug errors.</p>
 *
 * @see InstructionInjector
 * @see InstructionBipushExample
 * @see InstructionImulExample
 * @see InstructionPutfieldExample
 */
public class InstructionExample {

    public static void main(String[] args) {
        // Mandatory initialization: You must include this line. You can choose between two modes (ATTACH_API / NATIVE).
        JNIILBootstrap.install(JNIILBootstrap.MODE.ATTACH_API);

        testInstruction();
    }

    private static void testInstruction() {
        try {
            System.out.println("=== Starting InstructionInjector Test ===");

            // 1. Initialize and register the ASM-based instruction injectors
            InstructionInjector injector = new InstructionInjector();
            injector.inject(
                    new InstructionBipushExample(),
                    new InstructionImulExample(),
                    new InstructionPutfieldExample()
            );

            // 2. Instantiate the target object (The bytecode of this class has now been modified at runtime)
            InstructionTarget target = new InstructionTarget();

            // [Test 1] Triggers 'InstructionBipushExample' injector (processReward)
            target.processReward(5);

            // [Test 2] Triggers 'InstructionImulExample' injector (calculateBonus)
            int bonusResult = target.calculateBonus(100, 2);
            System.out.println("[Example] Bonus result (Expected 550): " + bonusResult);

            // [Test 3] Triggers 'InstructionPutfieldExample' injector (toggleStatus)
            target.toggleStatus();

            System.out.println("=========================================\n");
            /*
             * Expected Console Output:
             * ------------------------------------------------------------------------
             * [Target] Processing reward: 5
             * [Target] Reward points: 500
             * [Target] calculateBonus: base=100, multiplier=2
             * [Example] Bonus result (Expected 550): 550
             * [Target] Status toggled from false to true
             * [ASM] Field mutation detected via PUTFIELD!
             * ------------------------------------------------------------------------
             */
        } catch (Exception e) {
            throw new RuntimeException("An error occurred during the runtime instrumentation execution sequence", e);
        }
    }
}