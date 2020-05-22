package pers.lcnap.vertx.webmvc;

import java.lang.annotation.*;

@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Param {

    // boolean request() default false;

    String defaultValue() default "";
}
