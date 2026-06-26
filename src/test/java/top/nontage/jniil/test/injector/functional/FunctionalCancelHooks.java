package top.nontage.jniil.test.injector.functional;

import org.objectweb.asm.Opcodes;
import top.nontage.jniil.annotations.Before;
import top.nontage.jniil.annotations.InjectMethodInfo;
import top.nontage.jniil.injector.functional.MethodInfo;
import top.nontage.jniil.interfaces.FunctionalInjectable;
import top.nontage.jniil.test.target.FunctionalTarget;

/**
 * FUNCTIONAL INJECTOR - Control Flow Cancellation Hook
 *
 * <p>This class demonstrates how to cancel method execution and force
 * an early return using {@code info.cancel()} and {@code info.setReturnValue()}.</p>
 *
 * <p><b>Key Feature:</b></p>
 * <ul>
 *   <li>Intercept a method at entry point using {@code @Before}</li>
 *   <li>Call {@code info.cancel()} to prevent original method execution</li>
 *   <li>Use {@code info.setReturnValue()} to specify the forced return value</li>
 * </ul>
 *
 * <p><b>Operational Principle:</b></p>
 * This class handles high-level reflection-style runtime instrumentation hooks.
 * Instead of dealing directly with ASM instruction arrays, it intercepts methods
 * by capturing state variables into a MethodInfo container. The framework drops
 * a bridge invocation to your public static void hook methods at runtime, allowing
 * context reading, parameter mutation, or control flow cancellation.
 *
 * <p><b>Effect on program behavior:</b></p>
 * <ul>
 *   <li>Original: {@code getLoginAttempts()} returns the actual attempt count</li>
 *   <li>Modified: {@code getLoginAttempts()} returns 1 without executing original logic</li>
 * </ul>
 *
 * <p><b>Design Note:</b></p>
 * Unlike Standard/Instruction injectors which can only have ONE injection point
 * per class, FunctionalInjector supports multiple hook methods in a single class.
 * Each method with {@code @Before}, {@code @After}, or {@code @At} annotation
 * becomes an independent injection point.
 *
 * <p><b>Important:</b></p>
 * When calling {@code info.cancel()}:
 * <ul>
 *   <li>If the target method has a return value, you MUST call {@code info.setReturnValue()}</li>
 *   <li>If the target method is {@code void}, you do NOT need to set a return value</li>
 * </ul>
 *
 * @see FunctionalInjectable
 * @see Before
 * @see InjectMethodInfo
 * @see MethodInfo#cancel()
 * @see MethodInfo#setReturnValue(Object)
 */
public class FunctionalCancelHooks implements FunctionalInjectable, Opcodes {

    /*
     * [Test 6] Control Flow Cancellation Hook:
     * Leverages execution flags to shortcut original class routing routines.
     *
     * This implementation intercepts getLoginAttempts() and calls info.cancel().
     * The generator code registers this state, drops dead code execution tracks,
     * and routes out an instant return value cascade.
     *
     * Since getLoginAttempts() returns an int, we MUST call setReturnValue(1)
     * after calling cancel().
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