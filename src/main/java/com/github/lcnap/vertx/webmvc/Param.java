/*
 * Copyright 2026 lcnap
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

package com.github.lcnap.vertx.webmvc;

import java.lang.annotation.*;

@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Param {
    //默认值为空串，代表非必填,基本类型将会初始化为类型零值
    String defaultValue() default "";

    //默认必填；为false或没有注解，则非必填
    boolean required() default true;

    //非空则以name为key提取参数
    String name() default "";

    //日期、字符串格式化
    String format() default "";

    //字符串或列表size最大值
    int size() default 0;

    //值范围
    String[] limit() default {};

    long max() default Long.MAX_VALUE;

    long min() default Long.MIN_VALUE;

    //描述
    String description() default "";

    //其他校验
    String rule() default "";
}
