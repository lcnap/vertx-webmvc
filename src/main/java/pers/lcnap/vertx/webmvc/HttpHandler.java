package pers.lcnap.vertx.webmvc;

import io.vertx.core.http.HttpMethod;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface HttpHandler {

    String path();

    HttpMethod[] method() default {HttpMethod.GET, HttpMethod.POST};

    String contentType() default "application/json; charset=utf-8";

    String consumes() default "text/html";

    boolean isBlocking() default false;

}
