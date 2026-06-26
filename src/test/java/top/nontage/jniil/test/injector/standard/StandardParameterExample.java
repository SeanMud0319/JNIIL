package top.nontage.jniil.test.injector.standard;

import javassist.CtMethod;
import top.nontage.jniil.annotations.Before;
import top.nontage.jniil.annotations.InjectMethodInfo;
import top.nontage.jniil.interfaces.Injectable;
import top.nontage.jniil.test.target.StandardTarget;

/**
 * EXAMPLE 2: @Before with Parameter Access
 *
 * <p><b>What this example demonstrates:</b></p>
 * <ul>
 *   <li>How to access and inspect method parameters during injection</li>
 *   <li>Using Javassist special variables ({@code $0}, {@code $1}, etc.)</li>
 *   <li>Distinguishing overloaded methods using {@code targetMethodParamTypes}</li>
 * </ul>
 *
 * <p><b>Javassist Special Variables used in this example:</b></p>
 * <table border="1">
 *   <tr><th>Variable</th><th>Meaning</th><th>Example</th></tr>
 *   <tr><td>{@code $0}</td><td>The target object instance ({@code this})</td><td>{@code $0.name}</td></tr>
 *   <tr><td>{@code $1}</td><td>First method parameter</td><td>{@code $1} (String)</td></tr>
 *   <tr><td>{@code $2}</td><td>Second method parameter</td><td>{@code $2} (int)</td></tr>
 *   <tr><td>{@code $args}</td><td>Array of all parameters</td><td>{@code $args[0]}</td></tr>
 * </table>
 *
 * <p><b>Use case:</b></p>
 * Auditing parameter values, logging input data, argument validation, or
 * recording method calls with their arguments for debugging purposes.
 *
 * <p><b>Method signature resolution:</b></p>
 * Since {@code StandardTarget} may have multiple overloaded {@code setName}
 * methods, we must specify {@code targetMethodParamTypes} to uniquely identify
 * the one we want: {@code setName(String)}.
 *
 * <p><b>Expected behavior:</b></p>
 * When {@code StandardTarget.setName(String)} is called, this injector prints:
 * <pre>
 * Original Name: [current name], New Name: [input value]
 * </pre>
 * The original method then executes normally.
 *
 * @see Injectable
 * @see Before
 * @see InjectMethodInfo
 * @see <a href="https://www.javassist.org/tutorial/tutorial.html#special">Javassist Special Variables</a>
 */
public class StandardParameterExample implements Injectable {

    /**
     * Configuration for injecting into the overloaded {@code setName(String)} method.
     *
     * <p><b>Why specify targetMethodParamTypes?</b></p>
     * Without this, the framework cannot distinguish between:
     * <ul>
     *   <li>{@code setName(String)}</li>
     *   <li>{@code setName(String, int)}</li>
     *   <li>{@code setName()}</li>
     * </ul>
     * The parameter type array ({@code {String.class}}) uniquely identifies
     * the exact overload to be instrumented.
     *
     * <p><b>About the injected code:</b></p>
     * The source string uses Javassist's special variables:
     * <ul>
     *   <li>{@code $0} - The target instance (allows accessing its {@code name} field)</li>
     *   <li>{@code $1} - The first argument (the new name being set)</li>
     * </ul>
     */
    @InjectMethodInfo(
            targetType = StandardTarget.class,
            targetMethodName = "setName",
            targetMethodParamTypes = {String.class}
    )
    @Before
    @Override
    public String getInjectSourceCode(CtMethod ctMethod) {
        /*
         * This code accesses:
         * - $0.name: The 'name' field of the target StandardTarget instance
         * - $1: The String parameter passed to setName()
         *
         * IMPORTANT: Field access like $0.name requires that the field
         * is accessible (public or has a getter). If the field is private,
         * you may need to use reflection or Javassist's setAccessible().
         */
        return "System.out.println(\"Original Name: \" + $0.name + \", New Name: \" + $1);";
    }
}