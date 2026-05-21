package top.nontage.jniil.test.injector;

import org.objectweb.asm.Opcodes;
import top.nontage.jniil.annotations.*;
import top.nontage.jniil.injector.functional.MethodInfo;
import top.nontage.jniil.interfaces.FunctionalInjectable;
import top.nontage.jniil.test.target.FunctionalTarget;

/*
 * Functional (Event-Driven Hook Injector) Demonstration
 * * Operational Principle:
 * This class handles high-level reflection-style runtime instrumentation hooks.
 * Instead of dealing directly with ASM instruction arrays, it intercepts methods
 * by capturing state variables into a MethodInfo container. The framework drops
 * a bridge invocation to your public static void hook methods at runtime, allowing
 * context reading, parameter mutation, or control flow cancellation.
 */
public class Functional implements FunctionalInjectable, Opcodes {

    /*
     * [Test 1] @Before:
     * Targets the absolute entry point of the method before any logic runs.
     * * This implementation intercepts login() to inspect the incoming
     * password string parameter from the method arguments array layout.
     */
    @InjectMethodInfo(
            targetType = FunctionalTarget.class,
            targetMethodName = "login",
            targetMethodParamTypes = {String.class}
    )
    @Before
    public static void beforeLogin(MethodInfo info) {
        String inputPassword = info.getArgument(0);
        System.out.println("[JNIIL-Hook] @Before Login invoked. Password attempted: " + inputPassword);
    }

    /*
     * [Test 2] @After:
     * Targets the final return points (IRETURN to RETURN) of the execution flow.
     * * This implementation catches the exit boundary of login() right before
     * execution frame pops back to the caller to check method state flags.
     */
    @InjectMethodInfo(
            targetType = FunctionalTarget.class,
            targetMethodName = "login",
            targetMethodParamTypes = {String.class}
    )
    @After
    public static void afterLogin(MethodInfo info) {
        System.out.println("[JNIIL-Hook] @After Login invoked. Cancelled status: " + info.isCancelled());
    }

    /*
     * [Test 3] @At(line = 24, shiftAfter = true) with @Capture:
     * Targets line 24 using JVM's native LocalVariableTable metadata.
     * * Note: Relies on target class compiled with debug symbols (-g)
     * to preserve local variable names.
     */
    @InjectMethodInfo(
            targetType = FunctionalTarget.class,
            targetMethodName = "login",
            targetMethodParamTypes = {String.class}
    )
    @At(line = 24, shiftAfter = true)
    @Capture({"currentAttempt", "passwordMatches"})
    public static void hookLoginState(MethodInfo info) {
        System.out.println("[JNIIL-Hook] @At Line 24 + Capture Hit!");
        int attempts = info.getLocal("currentAttempt");
        boolean matched = info.getLocal("passwordMatches");
        System.out.println("[JNIIL-Hook] -> Captured 'currentAttempt': " + attempts);
        System.out.println("[JNIIL-Hook] -> Captured 'passwordMatches': " + matched);
    }

    /*
     * [Test 4] @At(opcode = INVOKESTATIC) with Slot-based @Capture:
     * Targets specific opcodes inside the instruction pipeline using index mappings.
     * * This implementation stops flow right after Integer.parseInt runs. Since no
     * name info is passed, it uses "=1" format to pull variable references directly
     * out of local variable slot index 1.
     */
    @InjectMethodInfo(
            targetType = FunctionalTarget.class,
            targetMethodName = "processTransaction",
            targetMethodParamTypes = {String.class}
    )
    @At(opcode = INVOKESTATIC, shiftAfter = true)
    @Capture({"=1"})
    public static void postParseInt(MethodInfo info) {
        String amountStr = info.getLocal(1);
        System.out.println("[JNIIL-Hook] @At Opcode Triggered! Input string was: " + amountStr);
    }

    /*
     * [Test 5] Parameter Manipulation Hook:
     * Uses @Before entry intercept to overwrite stack arrays before standard execution.
     * * This implementation flags specific malicious input text strings and swaps
     * the internal argument storage block to sanitize values, preventing crash cascades.
     */
    @InjectMethodInfo(
            targetType = FunctionalTarget.class,
            targetMethodName = "processTransaction",
            targetMethodParamTypes = {String.class}
    )
    @Before
    public static void sanitizeTransactionArgs(MethodInfo info) {
        String originalArg = info.getArgument(0);
        if ("INVALID_CRASH_TEST".equals(originalArg)) {
            System.out.println("[JNIIL-Hook] Sanitize Hook: Overwriting argument to '500'");
            info.setArgument(0, "500");
        }
    }

    /*
     * [Test 6] Control Flow Cancellation Hook:
     * Leverages execution flags to shortcut original class routing routines.
     * * This implementation intercepts getLoginAttempts() and calls info.cancel().
     * The generator code registers this state, drops dead code execution tracks,
     * and routes out an instant return value cascade.
     */
    @InjectMethodInfo(
            targetType = FunctionalTarget.class,
            targetMethodName = "getLoginAttempts"
    )
    @Before
    public static void cancelExecution(MethodInfo info) {
        System.out.println("[JNIIL-Hook] getLoginAttempts intercepted. Forcing cancellation flag.");
        info.cancel();
        info.setReturnValue(1);
    }
}