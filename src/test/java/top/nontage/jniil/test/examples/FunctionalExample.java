package top.nontage.jniil.test.examples;

import top.nontage.jniil.agent.JNIILBootstrap;
import top.nontage.jniil.injector.functional.FunctionalInjector;
import top.nontage.jniil.test.injector.functional.FunctionalAtHooks;
import top.nontage.jniil.test.injector.functional.FunctionalCancelHooks;
import top.nontage.jniil.test.injector.functional.FunctionalLifecycleHooks;
import top.nontage.jniil.test.target.FunctionalTarget;

/**
 * FUNCTIONAL INJECTOR EXAMPLE (Event-driven, Recommended)
 *
 * <p>This example demonstrates the {@code FunctionalInjector},
 * which is the most user-friendly and powerful injection method.</p>
 *
 * <p><b>Why use FunctionalInjector?</b></p>
 * <ul>
 *   <li>Event-driven: hooks are triggered by method execution events</li>
 *   <li>Easy data extraction: use {@code @Capture} to pull local variables by name</li>
 *   <li>Flow control: can cancel execution or modify return values</li>
 *   <li>No raw bytecode or Javassist strings needed</li>
 *   <li>Multiple hooks can be defined in a single class</li>
 * </ul>
 *
 * <p><b>Six injection scenarios demonstrated (grouped into 3 classes):</b></p>
 * <ol>
 *   <li>{@link FunctionalLifecycleHooks}:
 *     <ul>
 *       <li>@Before - method entry interception (login)</li>
 *       <li>@After - method exit interception (login)</li>
 *       <li>@Before - parameter sanitization (processTransaction)</li>
 *     </ul>
 *   </li>
 *   <li>{@link FunctionalAtHooks}:
 *     <ul>
 *       <li>@At(line) - specific source line interception with @Capture (login)</li>
 *       <li>@At(opcode) - specific bytecode instruction interception (processTransaction)</li>
 *     </ul>
 *   </li>
 *   <li>{@link FunctionalCancelHooks}:
 *     <ul>
 *       <li>@Before + info.cancel() - flow cancellation with forced return (getLoginAttempts)</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p><b>Prerequisite:</b> For line-number-based injection ({@code @At(line)}),
 * target classes must be compiled with debug symbols ({@code -g}).</p>
 *
 * <p><b>Design Note:</b></p>
 * Unlike Standard/Instruction injectors which can only have ONE injection point
 * per class, FunctionalInjector supports multiple hook methods in a single class.
 * Each method with {@code @Before}, {@code @After}, or {@code @At} annotation
 * becomes an independent injection point. This is why we group related hooks
 * into the same class (e.g., all login hooks in FunctionalLifecycleHooks).
 *
 * <p><b>Important:</b> By using this library, it is assumed that you fully understand
 * what you are about to do. Improper usage can lead to critical, hard-to-debug errors.</p>
 *
 * @see FunctionalInjector
 * @see FunctionalLifecycleHooks
 * @see FunctionalAtHooks
 * @see FunctionalCancelHooks
 */
public class FunctionalExample {

    public static void main(String[] args) {
        // Mandatory initialization: You must include this line. You can choose between two modes (ATTACH_API / NATIVE).
        JNIILBootstrap.install(JNIILBootstrap.MODE.ATTACH_API);

        testFunctional();
    }

    private static void testFunctional() {
        try {
            System.out.println("=== Starting FunctionalInjector Test ===");
            // 1. Initialize the event-driven functional instrumentation runner
            FunctionalInjector functionalInjector = new FunctionalInjector();

            // 2. Register all hook configurations into the instrumentation runtime
            functionalInjector.inject(
                    new FunctionalLifecycleHooks(),
                    new FunctionalAtHooks(),
                    new FunctionalCancelHooks()
            );

            // 3. Instantiate the target verification model (The bytecode has now been modified at runtime)
            FunctionalTarget target = new FunctionalTarget("Nontage");

            // [Test 1, 2, 3] Triggers multiple interception points within the login routine
            // Expected sequence: @Before captures args -> @At Line 24 extracts live locals -> @After monitors exit frame
            System.out.println("\n--- [Triggering Login Test Sequence] ---");
            target.login("secret123");

            // [Test 4] Triggers opcode-based instruction pipeline tracking
            // Expected sequence: @At Opcode traps INVOKESTATIC of Integer.parseInt, pulling local variable via slot index 1 ("=1")
            System.out.println("\n--- [Triggering Normal Transaction Test Sequence] ---");
            target.processTransaction("100");

            // [Test 5] Triggers input sanitization and argument hot-swapping
            // Expected sequence: Hostile string is passed, but @Before modifies the argument mapping to "500" to abort crashes
            System.out.println("\n--- [Triggering Argument Hijacking Test Sequence] ---");
            target.processTransaction("INVALID_CRASH_TEST");

            // [Test 6] Triggers execution flow cancellation and routing shortcut simulation
            // Expected sequence: @Before intercepts the getter call and raises info.cancel() to verify early return injection
            System.out.println("\n--- [Triggering Method Cancellation Test Sequence] ---");
            int attempts = target.getLoginAttempts();
            System.out.println("[Example] Method getLoginAttempts returned value: " + attempts);

            System.out.println("=========================================\n");
            /*
             * Expected Console Output:
             * ------------------------------------------------------------------------
             * [JNIIL-Hook] @Before Login invoked. Password attempted: secret123
             * [Target] Attempting login for user: Nontage
             * [JNIIL-Hook] @At Line 24 + Capture Hit!
             * [JNIIL-Hook] -> Captured 'currentAttempt': 1
             * [JNIIL-Hook] -> Captured 'passwordMatches': true
             * [Target] Login successful!
             * [JNIIL-Hook] @After Login invoked. Cancelled status: false
             *
             * [Target] Processing transaction amount: 100
             * [JNIIL-Hook] @At Opcode Triggered! Input string was: 100
             * [Target] Transaction completed successfully for amount: $100
             *
             * [JNIIL-Hook] Sanitize Hook: Overwriting argument to '500'
             * [Target] Processing transaction amount: 500
             * [JNIIL-Hook] @At Opcode Triggered! Input string was: 500
             * [Target] Transaction completed successfully for amount: $500
             *
             * [JNIIL-Hook] getLoginAttempts intercepted. Forcing cancellation flag.
             * [Example] Method getLoginAttempts returned value: 0
             * ------------------------------------------------------------------------
             */
        } catch (Exception e) {
            throw new RuntimeException("An error occurred during the functional instrumentation execution sequence", e);
        }
    }
}