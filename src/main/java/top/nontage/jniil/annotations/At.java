package top.nontage.jniil.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies the injection point within a method.
 * <p>
 * You must provide <b>EITHER</b> a direct line number <b>OR</b> an opcode-based
 * search pattern. Using both or neither will lead to ambiguity or errors.
 * </p>
 *
 * <h3>Usage Patterns:</h3>
 * <ul>
 * <li><b>Pattern 1 (Line-based):</b> Use {@link #line()} to target a specific source code line.</li>
 * <li><b>Pattern 2 (Opcode-based):</b> Use {@link #opcode()} along with {@link #identifier()}
 * and {@link #ordinal()} to find a specific bytecode instruction and derive its line number.</li>
 * </ul>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface At {

    /**
     * The direct line number to inject at.
     *
     * @return the target line number, or -1 if not using line-based injection
     */
    int line() default -1;

    /**
     * The mnemonic of the instruction to search for (e.g., "INVOKEVIRTUAL", "ALOAD").
     *
     * @return the opcode mnemonic
     */
    int opcode() default 114514;

    /**
     * An optional identifier to filter the opcode search (e.g., field name, method name, or variable index).
     *
     * @return the identifier for filtering
     */
    String identifier() default "";

    /**
     * The nth occurrence of the specified opcode (starting from 1).
     *
     * @return the occurrence index
     */
    int ordinal() default 1;

    /**
     * If true, the injection will occur at the beginning of the NEXT line following the target opcode.
     * <b>Note:</b> This attribute is only available for {@link top.nontage.jniil.injector.insn.InstructionInjector} and
     * {@link top.nontage.jniil.injector.functional.FunctionalInjector}
     *
     * @return true if the injection should shift after the target
     */
    boolean shiftAfter() default false;

    /**
     * If true, the injection will directly override the target opcode.
     * <b>Note:</b> This attribute is only available for {@link top.nontage.jniil.injector.insn.InstructionInjector}
     *
     * @return true if the injection should override the target opcode
     */
    boolean override() default false;

    /**
     * Enables InsnInjector debug logs in the console during the injection process.
     * <b>Note:</b> This attribute is only available for {@link top.nontage.jniil.injector.insn.InstructionInjector}
     *
     * @return true if debug mode is enabled
     */
    boolean debug() default false;
}