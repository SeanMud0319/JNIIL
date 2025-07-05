package top.nontage.jniil.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface InjectMethodInfo {
    String targetTypeInternalName();
    String targetMethodName();
    String[] targetMethodParms() default {};
    Class<?>[] appendClassLoader() default {};
    String targetTypeThreadName() default "";
    String appendFileLoader() default "";
    String appendJarLoader() default "";
    boolean defaultLoader() default true;
}

