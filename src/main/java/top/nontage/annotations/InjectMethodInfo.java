package top.nontage.annotations;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface InjectMethodInfo {
    String targetTypeInternalName();
    String targetMethodName();
    Class<?>[] appendClassLoader() default {};
    boolean after() default false;
    boolean before() default false;
    int atLine() default -1;
    String replaceCallClass() default "";
    String replaceCallMethod() default "";
}
