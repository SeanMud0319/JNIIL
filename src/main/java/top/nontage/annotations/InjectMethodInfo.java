package top.nontage.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface InjectMethodInfo {
    String targetTypeInternalName();
    String targetMethodName();
    //boolean isStaticMethod() default false;
    enum InjectionPoint {
        AFTER,
        AT,
        BEFORE
    }
    InjectionPoint injectionPoint();
    int atLine () default -1; // -1 means not specified, used only for AT injection point
}
