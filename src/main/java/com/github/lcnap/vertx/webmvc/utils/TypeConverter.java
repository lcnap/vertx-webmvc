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

package com.github.lcnap.vertx.webmvc.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 类型转换工具类 string -> Number/string/boolean
 */
public class TypeConverter {
    // 定义类型与转换逻辑的映射关系
    private static final Map<Class<?>, TypeHandler<?>> TYPE_HANDLER_MAP = new HashMap<>();

    // 初始化各类型的转换规则
    static {
        // 字符串类型
        registerHandler(String.class, String.class, value -> value);
        // 整数类型（int/Integer）
        registerHandler(int.class, Integer.class, value -> parseNumber(value, 0, Integer::parseInt));
        // 短整型（short/Short）
        registerHandler(short.class, Short.class, value -> parseNumber(value, (short) 0, Short::parseShort));
        // 长整型（long/Long）
        registerHandler(long.class, Long.class, value -> parseNumber(value, 0L, Long::parseLong));
        // 双精度（double/Double）
        registerHandler(double.class, Double.class, value -> parseNumber(value, 0.0D, Double::parseDouble));
        // 单精度（float/Float）
        registerHandler(float.class, Float.class, value -> parseNumber(value, 0.0F, Float::parseFloat));
        // 布尔类型（boolean/Boolean）
        registerHandler(boolean.class, Boolean.class, value -> {
            if (value == null || value.isBlank()) {
                return false;
            }
            return Boolean.parseBoolean(value);
        });
        // 字节类型（byte/Byte）
        registerHandler(byte.class, Byte.class, value -> parseNumber(value, (byte) 0, Byte::parseByte));
    }

    /**
     * 注册类型转换处理器（支持基础类型+包装类）
     */
    private static <T> void registerHandler(Class<?> primitiveType, Class<T> wrapperType, Function<String, T> converter) {
        TYPE_HANDLER_MAP.put(primitiveType, new TypeHandler<>(converter));
        TYPE_HANDLER_MAP.put(wrapperType, new TypeHandler<>(converter));
    }

    /**
     * 通用数值转换方法（抽离重复逻辑）
     */
    public static <T extends Number> T parseNumber(String value, T defaultValue, Function<String, T> parser) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return parser.apply(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(value, e);
        }
    }

    /**
     *
     */
    public static Object convert(Class<?> type, String value) {

        if (type == null) {
            throw new IllegalArgumentException("type is null");
        }

        TypeHandler<?> handler = TYPE_HANDLER_MAP.get(type);
        if (handler == null) {
            throw new UnsupportedOperationException("unsupported type: " + type);
        }

        return handler.converter().apply(value);
    }

    private record TypeHandler<T>(Function<String, T> converter) {
    }


}
