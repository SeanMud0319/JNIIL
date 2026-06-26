package top.nontage.jniil.test.injector.standard;

import javassist.CtMethod;
import top.nontage.jniil.annotations.Before;
import top.nontage.jniil.annotations.InjectMethodInfo;
import top.nontage.jniil.interfaces.Injectable;
import top.nontage.jniil.test.target.StandardTarget;

/**
 * EXAMPLE 1: Basic @Before Injection
 *
 * <p><b>What this example demonstrates:</b></p>
 * <ul>
 *   <li>How to inject custom code at the very beginning of a target method</li>
 *   <li>The simplest form of method interception using the {@code @Before} annotation</li>
 *   <li>Implementation of the {@code Injectable} interface with Javassist source generation</li>
 * </ul>
 *
 * <p><b>Use case:</b></p>
 * Logging, input validation, security checks, or performance monitoring before
 * the actual business logic executes.
 *
 * <p><b>Expected behavior:</b></p>
 * When {@code StandardTarget.printInfo()} is called, this injector will insert
 * {@code System.out.println("Hello World");} at the method entry point.
 * The original method then continues execution normally.
 *
 * <p><b>Execution flow:</b></p>
 * <pre>
 * Original:   printInfo() { [original logic] }
 * Injected:   printInfo() { System.out.println("Hello World"); [original logic] }
 * </pre>
 *
 * @see Injectable
 * @see Before
 * @see InjectMethodInfo
 */
public class StandardBeforeExample implements Injectable {

    /**
     * Configuration for the injection target.
     *
     * <p><b>@InjectMethodInfo parameters:</b></p>
     * <ul>
     *   <li>{@code targetType} - The class containing the method to be instrumented</li>
     *   <li>{@code targetMethodName} - The exact method name within the target class</li>
     * </ul>
     *
     * <p><b>@Before</b> - Injects the returned source code at the <i>entry point</i>
     * of the target method (before any original statements execute).</p>
     */
    @InjectMethodInfo(
            targetType = StandardTarget.class,
            targetMethodName = "printInfo"
    )
    @Before
    @Override
    public String getInjectSourceCode(CtMethod ctMethod) {
        /*
         * The returned string is raw Java source code that will be inserted
         * directly into the target method's bytecode at the injection point.
         *
         * Note: This code runs inside the target class context, so:
         * - 'this' refers to the StandardTarget instance
         * - All fields and methods of StandardTarget are accessible
         */
        return "System.out.println(\"Hello World\");";
    }
}