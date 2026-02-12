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

package com.github.lcnap.vertx.webmvc.annotation;

import com.github.lcnap.vertx.webmvc.AnnotationHandler;
import com.github.lcnap.vertx.webmvc.ClientException;
import com.github.lcnap.vertx.webmvc.Param;
import com.github.lcnap.vertx.webmvc.rule.RuleChecker;
import com.github.lcnap.vertx.webmvc.rule.RuleCheckerImpl;

import java.util.List;

public class ParamHandler implements AnnotationHandler<Object> {

    private RuleChecker<Object> ruleChecker = new RuleCheckerImpl<>();

    @Override
    public Object handle(Param annotation, Class<?> type, Object bean) {

        if (type.isArray()) {
            throw new RuntimeException("Array parameters are not supported");
        }


        //处理默认值
        if (bean == null) {
            if (!annotation.required()) {
                return null;
            }
            var defaultValue = annotation.defaultValue();
            if (defaultValue.isBlank()) {
                throw new RuntimeException("Required parameter is empty");
            }

            if (ReflectionUtils.isPrimitive(type)) {
                return ReflectionUtils.convertToPrimitive(type, defaultValue);
            }
            if (type.isEnum()) {
                return ReflectionUtils.convertToEnum(type, defaultValue);
            }
            if (ReflectionUtils.isDateType(type)) {
                return ReflectionUtils.convertDate(type, defaultValue, annotation.format());
            }
            throw new UnsupportedOperationException("Unsupported type");
        }

        //处理长度限制
        if (annotation.size() != 0) {
            if (bean instanceof CharSequence str) {
                if (str.length() > annotation.size()) {
                    throw new ClientException("exceeds the maximum size limit");
                }
            }
            if (bean instanceof List<?> list) {
                if (list.size() > annotation.size()) {
                    throw new ClientException("exceeds the maximum size limit");
                }
            }

            //throw new UnsupportedOperationException("Unsupported type");
        }

        //处理值域限制
        if (annotation.limit().length != 0) {
            if (bean instanceof CharSequence str) {

                for (String s : annotation.limit()) {
                    if (str.equals(s)) {
                        return bean;
                    }
                }
                throw new ClientException("unsupported values");
            }
        }

        //规则
        if (!annotation.rule().isBlank()) {
            if (!ruleChecker.exec(annotation.rule(), bean)) {
                throw new ClientException("rule check failed");
            }
        }

        if (bean instanceof Number) {
            if (annotation.max() < ((Number) bean).longValue() || annotation.min() > ((Number) bean).longValue()) {
                throw new ClientException("exceeds the value limit");
            }
        }

        return bean;
    }


}
