package top.nontage.jniil.test.injector.functional;

import org.objectweb.asm.Opcodes;
import top.nontage.jniil.annotations.After;
import top.nontage.jniil.annotations.Before;
import top.nontage.jniil.annotations.InjectMethodInfo;
import top.nontage.jniil.injector.functional.MethodInfo;
import top.nontage.jniil.interfaces.FunctionalInjectable;
import top.nontage.jniil.test.target.FunctionalTarget;

/**
 * FUNCTIONAL INJECTOR - Lifecycle Hooks (@Before & @After)
 *
 * <p>This class demonstrates the basic entry and exit point interception
 * using {@code @Before} and {@code @After} annotations.</p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li>{@code @Before} - Intercepts method entry before any logic executes</li>
 *   <li>{@code @After} - Intercepts method exit right before returning to caller</li>
 * </ul>
 *
 * <p><b>Operational Principle:</b></p>
 * This class handles high-level reflection-style runtime instrumentation hooks.
 * Instead of dealing directly with ASM instruction arrays, it intercepts methods
 * by capturing state variables into a MethodInfo container. The framework drops
 * a bridge invocation to your public static void hook methods at runtime, allowing
 * context reading, parameter mutation, or control flow cancellation.
 *
 * <p><b>Design Note:</b></p>
 * Unlike Standard/Instruction injectors which can only have ONE injection point
 * per class, FunctionalInjector supports multiple hook methods in a single class.
 * Each method with {@code @Before}, {@code @After}, or {@code @At} annotation
 * becomes an independent injection point.
 *
 * @see FunctionalInjectable
 * @see Before
 * @see After
 * @see InjectMethodInfo
 */
public class FunctionalLifecycleHooks implements FunctionalInjectable, Opcodes {

    /*
     * [Test 1] @Before:
     * Targets the absolute entry point of the method before any logic runs.
     *
     * This implementation intercepts login() to inspect the incoming
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
     *
     * This implementation catches the exit boundary of login() right before
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
     * [Test 5] Parameter Manipulation Hook (also uses @Before):
     * Uses @Before entry intercept to overwrite stack arrays before standard execution.
     *
     * This implementation flags specific malicious input text strings and swaps
     * the internal argument storage block to sanitize values, preventing crash cascades.
     *
     * Note: This is grouped here because it uses @Before, even though it's
     * targeting a different method (processTransaction).
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
}