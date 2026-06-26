package top.nontage.jniil.test.examples;

import top.nontage.jniil.agent.JNIILBootstrap;
import top.nontage.jniil.injector.StandardMethodInjector;
import top.nontage.jniil.test.injector.standard.StandardAtLineExample;
import top.nontage.jniil.test.injector.standard.StandardBeforeExample;
import top.nontage.jniil.test.injector.standard.StandardParameterExample;
import top.nontage.jniil.test.target.StandardTarget;

/**
 * STANDARD METHOD INJECTOR EXAMPLE (Javassist-based)
 *
 * <p>This example demonstrates the {@code StandardMethodInjector},
 * which uses Javassist for line-number-based source code injection.</p>
 *
 * <p><b>When to use StandardMethodInjector:</b></p>
 * <ul>
 *   <li>You need simple line-number-based injection</li>
 *   <li>You prefer writing raw Java source strings</li>
 *   <li>Your target methods are not too complex</li>
 * </ul>
 *
 * <p><b>Three injection scenarios demonstrated:</b></p>
 * <ol>
 *   <li>{@link StandardBeforeExample} - @Before at method entry (prints "Hello World")</li>
 *   <li>{@link StandardParameterExample} - @Before with parameter access ($0, $1)</li>
 *   <li>{@link StandardAtLineExample} - @At(line) at specific source lines (line 20)</li>
 * </ol>
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
public class StandardExample {

    public static void main(String[] args) {
        // Mandatory initialization: You must include this line. You can choose between two modes (ATTACH_API / NATIVE).
        JNIILBootstrap.install(JNIILBootstrap.MODE.ATTACH_API);

        testStandard();
    }

    private static void testStandard() {
        try {
            System.out.println("=== Starting StandardInjector Test ===");
            // Instantiate the Javassist-based injector implementation
            StandardMethodInjector standardMethodInjector = new StandardMethodInjector();

            // Register and deploy the configuration classes into the instrumentation runtime
            standardMethodInjector.inject(
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
            System.out.println("=========================================\n");
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
        } catch (Exception e) {
            throw new RuntimeException("An error occurred during the runtime instrumentation execution sequence", e);
        }
    }
}