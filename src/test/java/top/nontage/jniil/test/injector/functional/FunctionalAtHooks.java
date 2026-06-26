package top.nontage.jniil.test.injector.functional;

import org.objectweb.asm.Opcodes;
import top.nontage.jniil.annotations.At;
import top.nontage.jniil.annotations.Capture;
import top.nontage.jniil.annotations.InjectMethodInfo;
import top.nontage.jniil.injector.functional.MethodInfo;
import top.nontage.jniil.interfaces.FunctionalInjectable;
import top.nontage.jniil.test.target.FunctionalTarget;

import static org.objectweb.asm.Opcodes.INVOKESTATIC;

/**
 * FUNCTIONAL INJECTOR - @At Point Hooks
 *
 * <p>This class demonstrates precise injection at specific execution points
 * using the {@code @At} annotation with {@code @Capture}.</p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li>{@code @At(line = N)} - Targets a specific source code line</li>
 *   <li>{@code @At(opcode = XXX)} - Targets a specific bytecode instruction</li>
 *   <li>{@code @Capture} - Extracts local variables by name or slot index</li>
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
 * <p><b>Important Notes:</b></p>
 * <ul>
 *   <li>Line-number based injection ({@code @At(line)}) requires the target class
 *       to be compiled with debug symbols ({@code -g} or {@code -g:lines}).</li>
 *   <li>Slot-based capture ({@code "=1"}) uses JVM local variable slot indices.
 *       In instance methods, slot 0 is {@code this}, slot 1 is the first parameter.</li>
 * </ul>
 *
 * @see FunctionalInjectable
 * @see At
 * @see Capture
 * @see InjectMethodInfo
 */
public class FunctionalAtHooks implements FunctionalInjectable, Opcodes {

    /*
     * [Test 3] @At(line = 24, shiftAfter = true) with @Capture:
     * Targets line 24 using JVM's native LocalVariableTable metadata.
     *
     * Note: Relies on target class compiled with debug symbols (-g)
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
     * [Test 4] @At(opcode = INVOKESTATIC, shiftAfter = true) with Slot-based @Capture:
     * Targets specific opcodes inside the instruction pipeline using index mappings.
     *
     * This implementation stops flow right after Integer.parseInt runs. Since no
     * name info is passed, it uses "=1" format to pull variable references directly
     * out of local variable slot index 1.
     *
     * Why slot 1? In instance methods:
     *   - slot 0 = this (the target object instance)
     *   - slot 1 = the first parameter (amountStr)
     *   - slot 2 = the first local variable (amount), which may be reused for 'e' in catch block
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
}