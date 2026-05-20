package top.nontage.jniil.test;

import top.nontage.jniil.agent.JNIILBootstrap;
import top.nontage.jniil.injector.StandardMethodInjector;
import top.nontage.jniil.injector.insn.InstructionInjector;
import top.nontage.jniil.test.injector.Instruction;
import top.nontage.jniil.test.injector.Standard;
import top.nontage.jniil.test.target.InstructionTarget;
import top.nontage.jniil.test.target.StandardTarget;

/**
 * By using this library, it is assumed that you fully understand what you are about to do.
 * Improper usage can lead to critical, hard-to-debug errors.
 */
public class Example {
    public static void main(String[] args) {
        // Mandatory initialization: You must include this line. You can choose between two modes (ATTACH_API / NATIVE).
        JNIILBootstrap.install(JNIILBootstrap.MODE.ATTACH_API);

        /*
         * JNIIL is a runtime dynamic instrumentation library. Core features include:
         * - Inject: Modify method bodies dynamically.
         * - Monitor: Intercept method entries to retrieve caller and method metadata.
         * - Shadow: Create shadow classes that synchronize with actual runtime classes.
         * - Accessor: Ultra-high-performance reflection with zero overhead (direct-call speed).
         * Combine these modular components to achieve high-quality runtime code modification.
         */

        /*
         * [1] Injector: Modifies method bodies at runtime.
         * Note: Injectors specifically target "Methods" because runtime modifications in JVM are restricted to method levels.
         * * JNIIL provides three types of Injectors based on your technical needs:
         * * - StandardMethodInjector: Powered by Javassist. Perfect for line-number-based, straightforward injections.
         * However, it is less suited for handling highly complex method structures or advanced logic.
         * * - InstructionInjector: Fully built on top of ASM. Allows precise, low-level modifications at any arbitrary
         * bytecode instruction. It has a steeper learning curve and requires a solid understanding of JVM bytecode.
         * * - FunctionalInjector: (Highly Recommended) Merges the strengths of both worlds. An event-driven injector that
         * simplifies data extraction and supports seamless injection points based on either line numbers or specific instruction blocks.
         */
        testStandard();
        testInsn();
    }

    private static void testStandard() {
        try {
            System.out.println("=== Starting StandardInjector Test ===");
            // Instantiate the Javassist-based injector implementation
            StandardMethodInjector standardMethodInjector = new StandardMethodInjector();

            // Register and deploy the configuration classes into the instrumentation runtime
            standardMethodInjector.inject(new Standard(), new Standard.Standard2(), new Standard.Standard3());

            // Instantiate the target object. All specified target methods are now dynamically instrumented.
            StandardTarget target = new StandardTarget("Steve", 20);

            // Triggers 'Standard' injection: Prints "Hello World" right before logging object metadata
            target.printInfo();

            // Triggers 'Standard2' injection: Captures and logs both the instance state ($0.name) and the parameter payload ($1)
            target.setName("Alex");

            // Triggers 'Standard3' injection: Inserts a custom log at the designated internal line number (line 20)
            target.calculateBirthYear(100);
            System.out.println("=========================================\n");
            /*
             * Expected Console Output:
             * ------------------------------------------------------------------------
             * Hello World
             * Name: Steve, Age: 20
             * Original Name: Steve, New Name: Alex
             * Hello World 100
             * Processing birth year calculation for: Alex
             * ------------------------------------------------------------------------
             */
        } catch (Exception e) {
            throw new RuntimeException("An error occurred during the runtime instrumentation execution sequence", e);
        }
    }

    private static void testInsn() {
        try {
            System.out.println("=== Starting InstructionInjector Test ===");

            // 1. Initialize and register the ASM-based instruction injectors
            InstructionInjector injector = new InstructionInjector();
            injector.inject(new Instruction(), new Instruction.Instruction2(), new Instruction.Instruction3());

            // 2. Instantiate the target object (The bytecode of this class has now been modified at runtime)
            InstructionTarget target = new InstructionTarget();

            // [Test 1] Triggers 'Instruction' injector (processReward)
            // Original logic: Passing 5 would fail the condition (5 >= 10 is false), executing the else block (+100 score).
            // Injected logic: The BIPUSH 10 opcode is popped and replaced with 5. The condition becomes (5 >= 5 is true).
            // Expected Result: It will now execute the if block instead, yielding a score of 500.
            target.processReward(5);

            // [Test 2] Triggers 'Instruction2' injector (calculateBonus)
            // Original logic: (100 + 10) * 2 = 220
            // Injected logic: Pops the multiplier '2' off the operand stack right before IMUL, and pushes a forced '5'.
            // Expected Result: (100 + 10) * 5 = 550
            int bonusResult = target.calculateBonus(100, 2);
            System.out.println("[Example] Bonus result (Expected 550): " + bonusResult);

            // [Test 3] Triggers 'Instruction3' injector (toggleStatus)
            // Original logic: Flips the boolean flag and prints the state.
            // Injected logic: Instruments a custom System.out.println right after the PUTFIELD operation completes.
            // Expected Result: The console will print our low-level custom message before the target method finishes.
            target.toggleStatus();

            System.out.println("=========================================\n");
        } catch (Exception e) {
            throw new RuntimeException("An error occurred during the runtime instrumentation execution sequence", e);
        }
    }
}
