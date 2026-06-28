package top.nontage.jniil.test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.nontage.jniil.agent.JNIILBootstrap;
import top.nontage.jniil.injector.StandardMethodInjector;
import top.nontage.jniil.test.injector.standard.StandardAtLineExample;
import top.nontage.jniil.test.injector.standard.StandardBeforeExample;
import top.nontage.jniil.test.injector.standard.StandardParameterExample;
import top.nontage.jniil.test.target.StandardTarget;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * STANDARD METHOD INJECTOR TEST SUITE (Javassist-based)
 *
 * <p>This test suite demonstrates and verifies the {@code StandardMethodInjector},
 * which uses Javassist for line-number-based source code injection.</p>
 *
 * <p><b>Test coverage:</b></p>
 * <ul>
 *   <li>{@link #testBeforeBasic()} - @Before at method entry</li>
 *   <li>{@link #testBeforeWithParameter()} - @Before with parameter access ($0, $1)</li>
 *   <li>{@link #testAtLineInjection()} - @At(line) at specific source lines</li>
 *   <li>{@link #testAllInjectorsTogether()} - All three injectors working together</li>
 * </ul>
 *
 * <p><b>Prerequisite:</b> Target classes must be compiled with debug symbols ({@code -g})
 * for line-number-based injection to work.</p>
 *
 * <p><b>Important:</b> By using this library, it is assumed that you fully understand
 * what you are about to do. Improper usage can lead to critical, hard-to-debug errors.</p>
 *
 * @see StandardMethodInjector
 * @see StandardBeforeExample
 * @see StandardParameterExample
 * @see StandardAtLineExample
 */
@DisplayName("StandardMethodInjector Test Suite")
class StandardInjectorTest {

    private final PrintStream originalOut = System.out;
    private ByteArrayOutputStream outputStream;

    /**
     * Sets up the test environment before each test.
     *
     * <p>This method:</p>
     * <ul>
     *   <li>Initializes the JNIIL framework (required for all instrumentation)</li>
     *   <li>Redirects {@code System.out} to capture console output for verification</li>
     * </ul>
     */
    @BeforeEach
    void setUp() {
        // Mandatory initialization: You must include this line. You can choose between two modes (ATTACH_API / NATIVE).
        JNIILBootstrap.install(JNIILBootstrap.MODE.NATIVE);

        // Redirect System.out to capture output
        outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));
    }

    /**
     * Restores the original {@code System.out} after each test.
     */
    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }

    /**
     * TEST 1: Basic @Before Injection
     *
     * <p>Verifies that a simple "Hello World" print statement is inserted
     * at the entry point of {@code StandardTarget.printInfo()}.</p>
     *
     * <p><b>Expected behavior:</b></p>
     * <ul>
     *   <li>"Hello World" appears BEFORE "Name: Steve, Age: 20"</li>
     *   <li>The original {@code printInfo()} logic still executes</li>
     * </ul>
     */
    @Test
    @DisplayName("Test 1: @Before basic injection - printInfo()")
    void testBeforeBasic() throws Exception {
        // Instantiate the Javassist-based injector implementation
        StandardMethodInjector injector = new StandardMethodInjector();

        // Register and deploy the configuration classes into the instrumentation runtime
        injector.inject(new StandardBeforeExample());

        // Instantiate the target object. All specified target methods are now dynamically instrumented.
        StandardTarget target = new StandardTarget("Steve", 20);

        // Triggers 'StandardBeforeExample' injection: Prints "Hello World" right before logging object metadata
        target.printInfo();

        String output = outputStream.toString();

        // Verify: "Hello World" appears before "Name: Steve, Age: 20"
        int helloIndex = output.indexOf("Hello World");
        int infoIndex = output.indexOf("Name: Steve, Age: 20");

        assertAll(
                () -> assertTrue(output.contains("Hello World"),
                        "Expected injected 'Hello World' to be present"),
                () -> assertTrue(output.contains("Name: Steve, Age: 20"),
                        "Expected original method output 'Name: Steve, Age: 20' to be present"),
                () -> assertTrue(helloIndex < infoIndex,
                        "Expected 'Hello World' to appear BEFORE 'Name: Steve, Age: 20'")
        );
    }

    /**
     * TEST 2: @Before with Parameter Access
     *
     * <p>Verifies that the injector can access and log both the target
     * instance state ({@code $0.name}) and the method parameter ({@code $1}).</p>
     *
     * <p><b>Expected behavior:</b></p>
     * <ul>
     *   <li>The injected code prints: "Original Name: Steve, New Name: Alex"</li>
     * </ul>
     *
     * <p><b>Javassist special variables used:</b></p>
     * <ul>
     *   <li>{@code $0} - The target object instance (allows accessing its {@code name} field)</li>
     *   <li>{@code $1} - The first argument (the new name being set)</li>
     * </ul>
     */
    @Test
    @DisplayName("Test 2: @Before with parameter access - setName()")
    void testBeforeWithParameter() throws Exception {
        // Instantiate the Javassist-based injector implementation
        StandardMethodInjector injector = new StandardMethodInjector();

        // Register and deploy the configuration classes into the instrumentation runtime
        injector.inject(new StandardParameterExample());

        // Instantiate the target object. All specified target methods are now dynamically instrumented.
        StandardTarget target = new StandardTarget("Steve", 20);

        // Triggers 'StandardParameterExample' injection: Captures and logs both the instance state ($0.name) and the parameter payload ($1)
        target.setName("Alex");

        String output = outputStream.toString();

        // Verify the injected output is present
        assertTrue(output.contains("Original Name: Steve, New Name: Alex"),
                "Expected injected code to log: 'Original Name: Steve, New Name: Alex'");
    }

    /**
     * TEST 3: @At(line) Injection at Specific Source Line
     *
     * <p>Verifies that code is inserted at line 20 of
     * {@code StandardTarget.calculateBirthYear()}.</p>
     *
     * <p><b>Expected behavior:</b></p>
     * <ul>
     *   <li>"Hello World 100" appears at the injection point (line 20)</li>
     *   <li>This appears BEFORE the original method prints
     *       "Processing birth year calculation for: Alex"</li>
     * </ul>
     *
     * <p><b>Critical Requirement - Debug Information:</b></p>
     * This test requires that the target class is compiled with debug symbols
     * enabled ({@code -g} or {@code -g:lines}). Without this, the line numbers
     * are not preserved in the bytecode, and the injection will fail or target
     * the wrong location.
     */
    @Test
    @DisplayName("Test 3: @At(line) injection - calculateBirthYear()")
    void testAtLineInjection() throws Exception {
        // Instantiate the Javassist-based injector implementation
        StandardMethodInjector injector = new StandardMethodInjector();

        // Register and deploy the configuration classes into the instrumentation runtime
        injector.inject(new StandardAtLineExample());

        // Instantiate the target object. All specified target methods are now dynamically instrumented.
        StandardTarget target = new StandardTarget("Steve", 20);

        // Triggers 'StandardAtLineExample' injection: Inserts a custom log at the designated internal line number (line 20)
        target.calculateBirthYear(100);

        String output = outputStream.toString();

        // Verify the injected line executed before the original method's logic
        int helloIndex = output.indexOf("Hello World 100");
        int processIndex = output.indexOf("Processing birth year calculation");

        assertAll(
                () -> assertTrue(output.contains("Hello World 100"),
                        "Expected injected output 'Hello World 100' to be present"),
                () -> assertTrue(output.contains("Processing birth year calculation"),
                        "Expected original method output to be present"),
                () -> assertTrue(helloIndex < processIndex,
                        "Expected 'Hello World 100' to appear BEFORE the original method output")
        );
    }

    /**
     * TEST 4: All Three Injectors Working Together
     *
     * <p>Verifies that multiple injectors can be registered and executed
     * in sequence without interfering with each other.</p>
     *
     * <p><b>Expected output sequence:</b></p>
     * <pre>
     * Hello World
     * Name: Steve, Age: 20
     * Original Name: Steve, New Name: Alex
     * Hello World 100
     * Processing birth year calculation for: Alex
     * </pre>
     *
     * <p>This test also serves as the integration test that matches the
     * original {@code testStandard()} method from {@code Example}.</p>
     */
    @Test
    @DisplayName("Test 4: All injectors together (integration test)")
    void testAllInjectorsTogether() throws Exception {
        // Instantiate the Javassist-based injector implementation
        StandardMethodInjector injector = new StandardMethodInjector();

        // Register and deploy all three configuration classes into the instrumentation runtime
        injector.inject(
                new StandardBeforeExample(),
                new StandardParameterExample(),
                new StandardAtLineExample()
        );

        // Instantiate the target object. All specified target methods are now dynamically instrumented.
        StandardTarget target = new StandardTarget("Steve", 20);

        // Triggers 'StandardBeforeExample' injection: Prints "Hello World" right before logging object metadata
        target.printInfo();

        // Triggers 'StandardParameterExample' injection: Captures and logs both the instance state ($0.name) and the parameter payload ($1)
        target.setName("Alex");

        // Triggers 'StandardAtLineExample' injection: Inserts a custom log at the designated internal line number (line 20)
        target.calculateBirthYear(100);

        String output = outputStream.toString();

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

        // Verify the complete sequence is correct
        int helloIndex = output.indexOf("Hello World");
        int infoIndex = output.indexOf("Name: Steve, Age: 20");
        int originalNameIndex = output.indexOf("Original Name: Steve, New Name: Alex");
        int hello100Index = output.indexOf("Hello World 100");
        int processIndex = output.indexOf("Processing birth year calculation");

        assertAll(
                // All expected outputs are present
                () -> assertTrue(output.contains("Hello World"),
                        "Expected injected 'Hello World' to be present"),
                () -> assertTrue(output.contains("Name: Steve, Age: 20"),
                        "Expected original method output 'Name: Steve, Age: 20' to be present"),
                () -> assertTrue(output.contains("Original Name: Steve, New Name: Alex"),
                        "Expected injected code to log: 'Original Name: Steve, New Name: Alex'"),
                () -> assertTrue(output.contains("Hello World 100"),
                        "Expected injected output 'Hello World 100' to be present"),
                () -> assertTrue(output.contains("Processing birth year calculation"),
                        "Expected original method output to be present"),

                // Correct order is maintained
                () -> assertTrue(helloIndex < infoIndex,
                        "Expected 'Hello World' to appear BEFORE 'Name: Steve, Age: 20'"),
                () -> assertTrue(infoIndex < originalNameIndex,
                        "Expected 'Name: Steve, Age: 20' to appear BEFORE 'Original Name: Steve, New Name: Alex'"),
                () -> assertTrue(originalNameIndex < hello100Index,
                        "Expected 'Original Name: Steve, New Name: Alex' to appear BEFORE 'Hello World 100'"),
                () -> assertTrue(hello100Index < processIndex,
                        "Expected 'Hello World 100' to appear BEFORE 'Processing birth year calculation'")
        );
    }
}