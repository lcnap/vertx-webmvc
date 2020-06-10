/*
 * Copyright 2020 lcnap
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package pers.lcnap.vertx.webmvc;

import io.vertx.core.http.HttpMethod;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface HttpHandler {

    String path();

    HttpMethod[] method() default {HttpMethod.GET, HttpMethod.POST};

    String produce() default "application/json; charset=utf-8";

    /*String consumes() default "text/html";*/

    boolean isBlocking() default false;

}
