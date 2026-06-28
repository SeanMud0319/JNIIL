package top.nontage.jniil.test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.nontage.jniil.agent.JNIILBootstrap;
import top.nontage.jniil.injector.insn.InstructionInjector;
import top.nontage.jniil.test.injector.instruction.InstructionBipushExample;
import top.nontage.jniil.test.injector.instruction.InstructionImulExample;
import top.nontage.jniil.test.injector.instruction.InstructionPutfieldExample;
import top.nontage.jniil.test.target.InstructionTarget;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * INSTRUCTION INJECTOR TEST SUITE (ASM-based)
 *
 * <p>This test suite demonstrates and verifies the {@code InstructionInjector},
 * which uses ASM for low-level bytecode manipulation at the instruction level.</p>
 *
 * <p><b>Test coverage:</b></p>
 * <ul>
 *   <li>{@link #testBipushReplacement()} - BIPUSH opcode modification</li>
 *   <li>{@link #testImulManipulation()} - IMUL operand stack manipulation</li>
 *   <li>{@link #testPutfieldTracing()} - PUTFIELD field access tracing</li>
 *   <li>{@link #testAllInjectorsTogether()} - All three injectors working together</li>
 * </ul>
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
@DisplayName("InstructionInjector Test Suite")
class InstructionInjectorTest {

    private final PrintStream originalOut = System.out;
    private ByteArrayOutputStream outputStream;

    @BeforeEach
    void setUp() {
        JNIILBootstrap.install(JNIILBootstrap.MODE.NATIVE, true);
        outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }

    @Test
    @DisplayName("Test 1: BIPUSH replacement - processReward()")
    void testBipushReplacement() throws Exception {
        InstructionInjector injector = new InstructionInjector();
        injector.inject(new InstructionBipushExample());

        InstructionTarget target = new InstructionTarget();
        target.processReward(5);

        String output = outputStream.toString();

        assertAll(
                () -> assertTrue(output.contains("Current score: 500"),
                        "Expected score 500 (if-block executed)"),
                () -> assertFalse(output.contains("Current score: 100"),
                        "Expected else-block NOT to execute (score should not be 100)")
        );
    }

    @Test
    @DisplayName("Test 2: IMUL operand manipulation - calculateBonus()")
    void testImulManipulation() throws Exception {
        InstructionInjector injector = new InstructionInjector();
        injector.inject(new InstructionImulExample());

        InstructionTarget target = new InstructionTarget();
        int bonusResult = target.calculateBonus(100, 2);

        assertEquals(550, bonusResult,
                "Expected calculateBonus(100, 2) to return 550 (forced multiplier 5), but got " + bonusResult);
    }

    @Test
    @DisplayName("Test 3: PUTFIELD field access tracing - toggleStatus()")
    void testPutfieldTracing() throws Exception {
        InstructionInjector injector = new InstructionInjector();
        injector.inject(new InstructionPutfieldExample());

        InstructionTarget target = new InstructionTarget();
        target.toggleStatus();

        String output = outputStream.toString();

        assertTrue(output.contains("[ASM] Field mutation detected via PUTFIELD!"),
                "Expected injected trace message to be present");
    }

    @Test
    @DisplayName("Test 4: All injectors together (integration test)")
    void testAllInjectorsTogether() throws Exception {
        InstructionInjector injector = new InstructionInjector();
        injector.inject(
                new InstructionBipushExample(),
                new InstructionImulExample(),
                new InstructionPutfieldExample()
        );

        InstructionTarget target = new InstructionTarget();
        target.processReward(5);
        int bonusResult = target.calculateBonus(100, 2);
        System.out.println("[Example] Bonus result (Expected 550): " + bonusResult);
        target.toggleStatus();

        String output = outputStream.toString();

        assertAll(
                () -> assertTrue(output.contains("Current score: 500"),
                        "Expected score 500 (if-block executed)"),
                () -> assertTrue(output.contains("Bonus result (Expected 550): 550"),
                        "Expected bonus result 550"),
                () -> assertTrue(output.contains("[ASM] Field mutation detected via PUTFIELD!"),
                        "Expected injected trace message to be present"),
                () -> {
                    int rewardIndex = output.indexOf("Current score: 500");
                    int bonusIndex = output.indexOf("Bonus result (Expected 550): 550");
                    int traceIndex = output.indexOf("[ASM] Field mutation detected via PUTFIELD!");
                    assertTrue(rewardIndex < bonusIndex && bonusIndex < traceIndex,
                            "Expected order: processReward -> calculateBonus -> toggleStatus");
                }
        );
    }
}