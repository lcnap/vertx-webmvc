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

import com.github.lcnap.vertx.webmvc.utils.TypeConverter;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ReflectionUtilsTest {
    final static Logger logger = LoggerFactory.getLogger(ReflectionUtilsTest.class);


    @Test
    void convertToPrimitive() {
        Float f = ReflectionUtils.convertToPrimitive(Float.class, "3.14");
        assertEquals(3.14f, f, 0);

        int i = ReflectionUtils.convertToPrimitive(int.class, "100");
        assertEquals(100, i);

        String s = ReflectionUtils.convertToPrimitive(String.class, "hello");
        assertEquals("hello", s);


    }

    @Test
    void convertDate() {
        Date o = ReflectionUtils.convertDate(Date.class, "2026-01-01 12:12:12", "");
        assertNotNull(o);


        LocalDate oo = ReflectionUtils.convertDate(LocalDate.class, "2026-01-01", "");
        assertNotNull(oo);

        var ooo = ReflectionUtils.convertDate(LocalDateTime.class, "2026-01-01 01:01:02", "");
        assertNotNull(ooo);

        logger.info("{} {} {}", o, oo, ooo);

    }

    enum Status {
        YES("01"), NO("02");

        private final String code;

        Status(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    @Test
    void convertToEnum() {
        Status yes = ReflectionUtils.convertToEnum(Status.class, "YES");
        assertEquals(Status.YES, yes);
        Status no = ReflectionUtils.convertToEnum(Status.class, "NO");
        assertEquals(Status.NO, no);
    }


    @Test
    void convert() {
        Class<?> type = Long.class;
        Object convert = TypeConverter.convert(type, "12");
        logger.info("{} {}", convert, convert);
    }
}