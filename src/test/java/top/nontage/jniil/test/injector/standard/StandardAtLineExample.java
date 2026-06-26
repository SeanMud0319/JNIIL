package top.nontage.jniil.test.injector.standard;

import javassist.CtMethod;
import top.nontage.jniil.annotations.At;
import top.nontage.jniil.annotations.InjectMethodInfo;
import top.nontage.jniil.interfaces.Injectable;
import top.nontage.jniil.test.target.StandardTarget;

/**
 * EXAMPLE 3: @At(line) Injection at a Specific Source Line
 *
 * <p><b>What this example demonstrates:</b></p>
 * <ul>
 *   <li>How to inject code at a specific line number within a method</li>
 *   <li>Using the {@code @At} annotation with the {@code line} parameter</li>
 *   <li>Precise targeting of intermediate execution points (not just entry/exit)</li>
 * </ul>
 *
 * <p><b>Critical Requirement - Debug Information:</b></p>
 * <strong>Line number injection REQUIRES that the target class is compiled with
 * debug symbols enabled ({@code -g} or {@code -g:lines}).</strong>
 * Without this, the line numbers are not preserved in the bytecode, and the
 * injection will fail or target the wrong location.
 *
 * <p><b>How line number targeting works:</b></p>
 * <ol>
 *   <li>Javac (with {@code -g}) embeds a LineNumberTable in the .class file</li>
 *   <li>Javassist reads this table to map source lines to bytecode instructions</li>
 *   <li>The framework inserts your code at the bytecode position corresponding to the specified line</li>
 * </ol>
 *
 * <p><b>Use case:</b></p>
 * Injecting code at specific points in a method without affecting the beginning or end:
 * <ul>
 *   <li>Monitoring state changes at particular execution points</li>
 *   <li>Conditional logging only when a certain code path is reached</li>
 *   <li>Performance profiling of specific code sections</li>
 * </ul>
 *
 * <p><b>Expected behavior:</b></p>
 * When {@code StandardTarget.calculateBirthYear(int)} executes and reaches
 * line 20 in the original source file, this injector will execute:
 * <pre>
 * System.out.println("Hello World " + [age parameter]);
 * </pre>
 *
 * <p><b>Important warnings:</b></p>
 * <ul>
 *   <li>Line numbers are fragile - adding/removing lines in the target source
 *       will shift the injection point</li>
 *   <li>Always verify the actual line number in the current source version</li>
 *   <li>For more stable targeting, consider using opcode-based {@code @At}
 *       with {@code shiftAfter} instead</li>
 * </ul>
 *
 * @see Injectable
 * @see At
 * @see InjectMethodInfo
 * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.7.12">LineNumberTable (JVM Spec)</a>
 */
public class StandardAtLineExample implements Injectable {

    /**
     * Configuration for line-number-based injection.
     *
     * <p><b>@InjectMethodInfo parameters:</b></p>
     * <ul>
     *   <li>{@code targetType} - The class containing the target method</li>
     *   <li>{@code targetMethodName} - The method to instrument</li>
     *   <li>{@code targetMethodParamTypes} - Required to identify the correct overload</li>
     * </ul>
     *
     * <p><b>@At(line = 20) - Injection position:</b></p>
     * The code will be inserted right at line 20 of the {@code calculateBirthYear}
     * method's source file.
     *
     * <p><b>Why line 20?</b></p>
     * This is an arbitrary example value. In practice, you would:
     * <ol>
     *   <li>Open {@code StandardTarget.java}</li>
     *   <li>Find the line number where you want to inject</li>
     *   <li>Adjust this value to match your target</li>
     * </ol>
     *
     * <p><b>Troubleshooting:</b></p>
     * If injection at line 20 fails:
     * <ul>
     *   <li>Check that {@code StandardTarget} was compiled with {@code -g}</li>
     *   <li>Verify the actual line number hasn't changed due to code edits</li>
     *   <li>Consider using {@code @At(opcode = ...)} for more robust targeting</li>
     * </ul>
     */
    @InjectMethodInfo(
            targetType = StandardTarget.class,
            targetMethodName = "calculateBirthYear",
            targetMethodParamTypes = {int.class}
    )
    @At(line = 20)
    @Override
    public String getInjectSourceCode(CtMethod ctMethod) {
        /*
         * Injected code uses Javassist's $1 variable:
         * - $1 = the first parameter (age) passed to calculateBirthYear()
         *
         * Note: This code will be inserted AFTER the bytecode instructions
         * corresponding to source line 20 have been processed.
         *
         * For insertions BEFORE line 20, use @At(line = 20, shiftBefore = true)
         * For insertions AFTER line 20, use @At(line = 20, shiftAfter = true)
         * (shiftAfter is the default behavior when not specified)
         */
        return "System.out.println(\"Hello World \" + $1);";
    }
}