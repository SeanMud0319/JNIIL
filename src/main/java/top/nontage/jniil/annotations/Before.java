package top.nontage.jniil.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an injection point to be executed before the target instructions.
 * <p>
 * <b>Note:</b> This annotation is only available for {@link top.nontage.jniil.injector.FunctionalInjector}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Before {
}
