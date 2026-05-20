package top.nontage.jniil.test.injector;

import javassist.CtMethod;
import top.nontage.jniil.annotations.At;
import top.nontage.jniil.annotations.Before;
import top.nontage.jniil.annotations.InjectMethodInfo;
import top.nontage.jniil.interfaces.Injectable;
import top.nontage.jniil.test.target.StandardTarget;

/**
 * All injectors must implement their corresponding Injectable interface.
 * It is highly recommended to use the @InjectMethodInfo() annotation instead of fully implementing Injectable manually.
 * Since utilizing the internal mechanics of Injectable requires familiarity with Javassist, using FunctionalInjector is generally preferred.
 * <p>
 * Note: Each class can only contain a single implementation of getInjectSourceCode().
 * If you need to perform multiple distinct injections, you must create separate classes or utilize nested static classes.
 */
public class Standard implements Injectable {

    /*
     * @InjectMethodInfo: Configures the deployment targets for the injection framework.
     * - targetType: Specifies the target Class where the code will be injected.
     * - targetMethodName: Identifies the precise method name inside the target class.
     * * @Before: Instructs the framework to inject the source code at the very beginning of the target method.
     */
    @InjectMethodInfo(
            targetType = StandardTarget.class,
            targetMethodName = "printInfo"
    )
    @Before
    @Override
    public String getInjectSourceCode(CtMethod ctMethod) {
        // The return string contains the raw Java code fragments to be injected into the target method body.
        return "System.out.println(\"Hello World\");";
    }

    /**
     * Example of using a nested static class to perform a secondary, distinct injection.
     */
    public static class Standard2 implements Injectable {

        /*
         * @InjectMethodInfo:
         * - targetMethodParamTypes: Defines the method signature (parameter types) to precisely locate overloaded methods.
         * * Javassist Identifiers Used Below:
         * - $0: Represents 'this' (the current target object instance).
         * - $1: Represents the first argument passed into the target method.
         */
        @InjectMethodInfo(
                targetType = StandardTarget.class,
                targetMethodName = "setName",
                targetMethodParamTypes = {String.class}
        )
        @Before
        @Override
        public String getInjectSourceCode(CtMethod ctMethod) {
            return "System.out.println(\"Original Name: \" + $0.name + \", New Name: \" + $1);";
        }
    }

    /**
     * Example of targeted injection at a specific location within a multi-line method.
     */
    public static class Standard3 implements Injectable {

        /*
         * @At: Targets a specific position within the method instead of the absolute start or end.
         * - line: Instructs the framework to perform line-number-based injection at the designated source code line.
         */
        @InjectMethodInfo(
                targetType = StandardTarget.class,
                targetMethodName = "calculateBirthYear",
                targetMethodParamTypes = {int.class}
        )
        @At(line = 20)
        @Override
        public String getInjectSourceCode(CtMethod ctMethod) {
            return "System.out.println(\"Hello World \" + $1);";
        }
    }
}