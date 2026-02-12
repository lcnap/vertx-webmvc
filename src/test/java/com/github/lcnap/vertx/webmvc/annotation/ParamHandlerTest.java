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

import com.github.lcnap.vertx.webmvc.Param;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.time.LocalDate;

class ParamHandlerTest {
    final static Logger logger = LoggerFactory.getLogger(ParamHandlerTest.class);


    static class Exam {

        @Param(defaultValue = "10")
        private Integer id;

        @Param(required = false)
        private String name;

        @Param(size = 1)
        private String age;

        @Param(limit = {
                "深圳", "北京"
        })
        private String address;

        @Param(defaultValue = "2020-01-01")
        private LocalDate date;

        @Param(defaultValue = "YES")
        private ReflectionUtilsTest.Status status;

        @Param(max = 100)
        private long m;

        @Param(defaultValue = "1")
        private int i;

        @Param(defaultValue = "1")
        private String j;

        @Override
        public String toString() {
            return "Exam{" +
                    "id=" + id +
                    ", name='" + name + '\'' +
                    ", age='" + age + '\'' +
                    ", address='" + address + '\'' +
                    ", date=" + date +
                    ", status=" + status +
                    ", m=" + m +
                    ", i=" + i +
                    ", j='" + j + '\'' +
                    '}';
        }
    }


    @Test
    void handle() throws IllegalAccessException {
        Exam exam = new Exam();
        exam.name = "test";
        exam.age = "1";
        exam.address = "深圳";
        exam.m = 100;


        ParamHandler handler = new ParamHandler();

        for (Field field : exam.getClass().getDeclaredFields()) {
            var annotation = field.getAnnotation(Param.class);
            Object handle = handler.handle(annotation, field.getType(), field.get(exam));
            field.setAccessible(true);
            field.set(exam, handle);

        }


        logger.info(exam.toString());
    }
}