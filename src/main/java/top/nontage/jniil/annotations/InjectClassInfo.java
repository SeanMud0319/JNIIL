package top.nontage.jniil.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface InjectClassInfo {
    String anchorClass() default "";

    Class<?> anchorClassType() default Object.class;

    String anchorThread() default "";
}
