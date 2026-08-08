package top.nontage.jniil.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method to completely overwrite the target method's body.
 * <p>
 * <b>Note:</b> This annotation is only available for
 * {@link top.nontage.jniil.injector.StandardMethodInjector} and
 * {@link top.nontage.jniil.injector.functional.FunctionalInjector}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Overwrite {
}