package top.nontage.jniil.annotations;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface InjectMethodInfo {
    String targetTypeInternalName();
    String targetMethodName();
    String[] targetMethodParms() default {};
    Class<?>[] appendClassLoader() default {};
}

