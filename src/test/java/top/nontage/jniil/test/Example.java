package top.nontage.jniil.test;

import top.nontage.jniil.agent.JNIILBootstrap;
import top.nontage.jniil.test.examples.FunctionalExample;

/**
 * By using this library, it is assumed that you fully understand what you are about to do.
 * Improper usage can lead to critical, hard-to-debug errors.
 *
 * <p>This is the main entry point for JNIIL demonstrations.
 * For detailed examples of each component, please refer to:</p>
 *
 * <ul>
 *   <li><b>StandardMethodInjector</b> (Javassist-based):
 *     <ul>
 *       <li>Manual demo: {@link top.nontage.jniil.test.examples.StandardExample}</li>
 *       <li>JUnit tests: {@link StandardInjectorTest}</li>
 *     </ul>
 *   </li>
 *   <li><b>InstructionInjector</b> (ASM-based):
 *     <ul>
 *       <li>Manual demo: {@link top.nontage.jniil.test.examples.InstructionExample}</li>
 *       <li>JUnit tests: {@link InstructionInjectorTest}</li>
 *     </ul>
 *   </li>
 *   <li><b>FunctionalInjector</b> (Event-driven, Recommended):
 *     <ul>
 *       <li>Manual demo: {@link top.nontage.jniil.test.examples.FunctionalExample}</li>
 *       <li>JUnit tests: {@link FunctionalInjectorTest}</li>
 *     </ul>
 *   </li>
 *   <li><b>InvocationMonitor</b> (AOP-style runtime interception):
 *     <ul>
 *       <li>Manual demo: {@link top.nontage.jniil.test.examples.MonitorExample}</li>
 *       <li>JUnit tests: {@link MonitorTest}</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p><b>Quick Start:</b></p>
 * The easiest way to get started is to run {@link FunctionalExample#main(String[])}
 * which demonstrates all features of the recommended injector.
 *
 * @see top.nontage.jniil.test.examples.FunctionalExample
 * @see FunctionalInjectorTest
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
         *
         * JNIIL provides three types of Injectors based on your technical needs:
         *
         * 1) StandardMethodInjector - Powered by Javassist. Perfect for line-number-based, straightforward injections.
         *    However, it is less suited for handling highly complex method structures or advanced logic.
         *    See: examples.StandardExample and StandardInjectorTest
         *
         * 2) InstructionInjector - Fully built on top of ASM. Allows precise, low-level modifications at any arbitrary
         *    bytecode instruction. It has a steeper learning curve and requires a solid understanding of JVM bytecode.
         *    See: examples.InstructionExample and InstructionInjectorTest
         *
         * 3) FunctionalInjector - (Highly Recommended) Merges the strengths of both worlds. An event-driven injector that
         *    simplifies data extraction and supports seamless injection points based on either line numbers or specific
         *    instruction blocks.
         *    See: examples.FunctionalExample and FunctionalInjectorTest
         *
         * [2] Monitor: AOP-style runtime method interception.
         *     Unlike Injectors which modify bytecode once at startup, Monitor works by:
         *     - Modifying target method bytecode to insert a dispatch call at the method entry
         *     - The dispatch call routes to registered InvocationListeners at runtime
         *     - Supports: caller inspection, argument inspection, execution cancellation, return value override
         *
         *     WARNING: It is NOT recommended to use both Injector and Monitor on the same method.
         *     They both modify bytecode and may interfere with each other, causing unexpected behavior.
         *     Choose one approach based on your needs:
         *     - Injector: When you need to permanently modify method logic (add/remove/replace code)
         *     - Monitor: When you need to observe or intercept invocations without permanent logic changes
         *
         *     See: examples.MonitorExample and MonitorTest
         *
         * For a quick demonstration, run FunctionalExample.main().
         */

        System.out.println("=== JNIIL Example Runner ===");
        System.out.println();
        System.out.println("This is the main entry point. To see actual instrumentation in action,");
        System.out.println("please run one of the specific example classes:");
        System.out.println();
        System.out.println("  - FunctionalExample  (Recommended - Event-driven)");
        System.out.println("  - StandardExample    (Javassist-based)");
        System.out.println("  - InstructionExample (ASM-based)");
        System.out.println("  - MonitorExample     (AOP-style runtime interception)");
        System.out.println();
        System.out.println("Or run the JUnit tests:");
        System.out.println();
        System.out.println("  - FunctionalInjectorTest");
        System.out.println("  - StandardInjectorTest");
        System.out.println("  - InstructionInjectorTest");
        System.out.println("  - MonitorTest");
        System.out.println();
        System.out.println("=========================================");
    }
}