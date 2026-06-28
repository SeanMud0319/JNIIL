package top.nontage.jniil.interfaces;

import javassist.CtMethod;
import org.objectweb.asm.Opcodes;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents an injectable unit used by the JNIIL injection framework.
 * <p>
 * An {@code Injectable} defines both:
 * <ul>
 *     <li><b>How</b> code should be injected (via {@link #getInjectSourceCode(CtMethod)})</li>
 *     <li><b>Where</b> the injection should occur (via target metadata methods)</li>
 * </ul>
 *
 * <h2>Injection Source</h2>
 * <p>
 * The method {@link #getInjectSourceCode(CtMethod)} is the <b>core contract</b> of this interface
 * and <b>must be implemented</b>. It is responsible for returning the source code (or code fragment)
 * that will be injected into the target method.
 * <p>
 * The provided {@link CtMethod} parameter represents the resolved target method and allows
 * implementations to inspect method signatures, return types, parameters, and other bytecode-level
 * information before generating injection logic.
 *
 * <h2>Target Metadata</h2>
 * <p>
 * All other methods in this interface provide <b>default metadata definitions</b> describing
 * the injection target, such as:
 * <ul>
 *     <li>Target class or internal name</li>
 *     <li>Target method name and parameter types</li>
 *     <li>ClassLoader and Thread resolution behavior</li>
 * </ul>
 *
 * <p>
 * These metadata methods are primarily used when the injection target is defined
 * <b>programmatically</b>, i.e. when no {@code @InjectMethodInfo} annotation is present.
 * <p>
 * When {@code @InjectMethodInfo} is used on the injection method, its values will
 * override the corresponding defaults defined here.
 *
 * <h2>Design Notes</h2>
 * <ul>
 *     <li>{@link #getInjectSourceCode(CtMethod)} has no default implementation to ensure
 *     compile-time enforcement of injection behavior.</li>
 *     <li>All metadata methods provide safe defaults to avoid {@code null} checks
 *     and simplify framework-level processing.</li>
 * </ul>
 *
 * @see javassist.CtMethod
 * @see top.nontage.jniil.annotations.InjectMethodInfo
 */
public interface Injectable {
    String getInjectSourceCode(CtMethod ctMethod);

    default String targetTypeInternalName() {
        return "";
    }

    default Class<?> targetType() {
        return null;
    }

    default String targetMethodName() {
        return "";
    }

    default String[] targetMethodParams() {
        return new String[0];
    }

    default Class<?>[] targetMethodParamTypes() {
        return new Class<?>[0];
    }

    default String targetTypeThreadName() {
        return "";
    }

    default Class<?>[] appendClassLoader() {
        return new Class<?>[0];
    }

    default String[] appendFileLoader() {
        return new String[]{};
    }

    default String[] appendJarLoader() {
        return new String[]{};
    }

    default Map<String, byte[]> appendByteLoader() {
        return new HashMap<>();
    }

    default boolean defaultLoader() {
        return true;
    }
}
