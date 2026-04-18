package io.homeey.gateway.common.spi;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Activate {
    String[] group() default {};

    int order() default 0;

    String[] conditionKeys() default {};

    String[] conditions() default {};

    boolean matchAll() default true;
}
