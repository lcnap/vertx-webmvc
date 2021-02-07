/*
 * Copyright 2021 lcnap
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

package pers.lcnap.vertx.webmvc.utils;


import pers.lcnap.vertx.webmvc.HttpHandler;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

public class Reflection {

    private final static HashSet<Class<?>> primitiveType = new HashSet<>();

    static {
        primitiveType.add(Boolean.class);
        primitiveType.add(Byte.class);
        primitiveType.add(Integer.class);
        primitiveType.add(Short.class);
        primitiveType.add(Double.class);
        primitiveType.add(Float.class);
        primitiveType.add(Long.class);
        primitiveType.add(String.class);

        primitiveType.add(boolean.class);
        primitiveType.add(byte.class);
        primitiveType.add(int.class);
        primitiveType.add(short.class);
        primitiveType.add(double.class);
        primitiveType.add(float.class);
        primitiveType.add(long.class);
    }

    public static Set<Class<?>> findHandlerClass(String pkg) throws Exception {
        String pkgPath = pkg.replace(".", "/");
        URL resource = Reflection.class.getClassLoader().getResource(pkgPath);
        Set<Class<?>> classSet = new HashSet<>();

        if (resource == null) {
            return classSet;
        }
        File file = new File(resource.getFile());
        if (!file.exists()) {
            return classSet;
        }


        File[] files = file.listFiles(
                pathname -> pathname.isDirectory() || pathname.getName().endsWith(".class")
        );

        if (files == null || files.length == 0) {
            return classSet;
        }


        for (File f : files) {
            if (f.isDirectory()) {
                Set<Class<?>> subDirClass = findHandlerClass(pkg + "." + f.getName());
                if (subDirClass.size() != 0) {
                    classSet.addAll(subDirClass);
                }
            } else {
                String className = f.getName().replace(".class", "");
                Class<?> clazz = Class.forName(pkg + "." + className);
                if (clazz != null) {
                    Method[] declaredMethods = clazz.getDeclaredMethods();
                    for (Method method : declaredMethods) {
                        HttpHandler annotation = method.getAnnotation(HttpHandler.class);
                        if (annotation != null) {
                            classSet.add(clazz);
                            break;
                        }
                    }

                }
            }
        }

        return classSet;
    }

    public static void main(String... agrs) throws Exception {
        Set<Class<?>> handlerClass = Reflection.findHandlerClass("pers.lcnap.vertxwebframework.test");


    }

    public static boolean isPrimitiveType(Class<?> type) {
        return primitiveType.contains(type);
    }

}
