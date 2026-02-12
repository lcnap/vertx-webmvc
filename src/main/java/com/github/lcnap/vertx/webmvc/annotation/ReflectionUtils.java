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

import com.github.lcnap.vertx.webmvc.ClientException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

public class ReflectionUtils {

    public static <T> T convertToPrimitive(Class<T> type, String value) {
        if (type.equals(String.class)) {
            return (T) value;
        }

        Class<?> targetType = type;
        if (type.isPrimitive()) {
            targetType = switch (type.getName()) {
                case "int" -> Integer.class;
                case "float" -> Float.class;
                case "long" -> Long.class;
                case "boolean" -> Boolean.class;
                case "byte" -> Byte.class;
                case "double" -> Double.class;
                case "short" -> Short.class;
                case "char" -> Character.class;
                default -> targetType;
            };
        }

        try {
            Method valueOf = targetType.getMethod("valueOf", String.class);
            Object invoke = valueOf.invoke(null, value);
            return (T) invoke;
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            throw new ClientException("parse default value failed", e);
        }

    }

    public static <T> T convertToEnum(Class<T> type, String value) {
        return convertToPrimitive(type, value);
    }

    public static boolean isPrimitiveWrapper(Class<?> clazz) {
        if (clazz == null) {
            return false;
        }
        return clazz.equals(Integer.class)
                || clazz.equals(Float.class)
                || clazz.equals(Long.class)
                || clazz.equals(Boolean.class)
                || clazz.equals(Byte.class)
                || clazz.equals(Double.class)
                || clazz.equals(Short.class)
                || clazz.equals(Character.class);
    }

    public static boolean isPrimitive(Class<?> clazz) {
        return clazz.isPrimitive() || isPrimitiveWrapper(clazz) || clazz.equals(String.class);
    }

    public static boolean isCollection(Object obj) {
        if (obj == null) {
            return false;
        }
        return obj instanceof Collection || obj instanceof Map;
    }

    public static boolean isCollection(Class<?> clazz) {

        if (clazz == null) {
            return false;
        }
        if (clazz.isInterface()) {
            return false;
        }
        return Collection.class.isAssignableFrom(clazz)
                || Map.class.isAssignableFrom(clazz);
    }

    public static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd";
    public static final String DEFAULT_DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";


    public static boolean isDateType(Class<?> clazz) {
        if (clazz == null) {
            return false;
        }
        return Date.class.isAssignableFrom(clazz)
                || LocalDate.class.isAssignableFrom(clazz)
                || LocalDateTime.class.isAssignableFrom(clazz);
    }


    public static <T> T convertDate(Class<T> clazz, String value, String format) {
        if (format == null || format.isBlank()) {
            format = DEFAULT_DATETIME_FORMAT;
            if (clazz.equals(LocalDate.class)) {
                format = DEFAULT_DATE_FORMAT;
            }
        }

        if (clazz.equals(Date.class)) {
            try {
                return (T) new SimpleDateFormat(format).parse(value);
            } catch (ParseException e) {
                throw new ClientException("parse date failed.", e);
            }
        }
        if (clazz.equals(LocalDate.class)) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
            return (T) LocalDate.parse(value, formatter);
        }
        if (clazz.equals(LocalDateTime.class)) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
            return (T) LocalDateTime.parse(value, formatter);
        }

        throw new IllegalArgumentException("unknow type.");
    }

}
