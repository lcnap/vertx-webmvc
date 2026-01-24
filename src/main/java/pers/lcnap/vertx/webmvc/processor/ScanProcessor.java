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

package pers.lcnap.vertx.webmvc.processor;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.common.template.TemplateEngine;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pers.lcnap.vertx.webmvc.*;
import pers.lcnap.vertx.webmvc.impl.SimpleWebApplicationImpl;
import pers.lcnap.vertx.webmvc.utils.Reflection;

import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;

public class ScanProcessor {
    private final static Logger logger = LoggerFactory.getLogger(ScanProcessor.class);

    SimpleWebApplicationImpl application;

    public ScanProcessor(SimpleWebApplicationImpl application) {
        this.application = application;
    }

    //扫描业务类
    public void scanHttpHandler() {
        try {
            String pkg = this.application.appClass().getPackage().getName();
            Set<Class<?>> handlerClass =
                    Reflection.findHandlerClass(pkg);

            for (Class<?> a : handlerClass) {

                Router classRouter = Router.router(this.application.vertx());

                Method[] methods = a.getDeclaredMethods();
                for (Method method : methods) {
                    HttpHandler annotation = method.getAnnotation(HttpHandler.class);
                    if (annotation != null) {
                        //todo 解析方法所需参数。
                        Handler<RoutingContext> handler = proxyHandler(a, method, annotation);

                        String path = annotation.path();
                        HttpMethod[] httpMethods = annotation.method();
                        Route route;

                        //方法路由
                        if (httpMethods.length != 1) {
                            route = classRouter.route(path);
                        } else {
                            route = classRouter.route(
                                    io.vertx.core.http.HttpMethod.valueOf(String.valueOf(httpMethods[0])),
                                    path);
                        }


                        if (annotation.isBlocking()) {
                            route.blockingHandler(handler);
                        } else {
                            route.handler(handler);
                        }


                    }
                }

                //类上的注解，只有path有效
                HttpHandler classHttpHandler = a.getAnnotation(HttpHandler.class);
                String path = classHttpHandler != null ? classHttpHandler.path() : "/*";
                this.application.rootRouter().route(path + "/*").subRouter(classRouter);
            }


        } catch (Exception e) {
            logger.error("scan handler failed.", e.getCause());
            this.application.vertx().close();
        }
    }

    private @NonNull Handler<RoutingContext> proxyHandler(Class<?> a, Method method, HttpHandler annotation) throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        Parameter[] parameters = method.getParameters();

        Constructor<?> declaredConstructor = a.getDeclaredConstructor();
        Object o = declaredConstructor.newInstance();

        Handler<RoutingContext> handler = rc -> {
            try {
                //before
                Object[] args = parseArgs(parameters, rc);
                //handler
                checkArg(args);
                Object invoke = method.invoke(o, args);
                //after
                parseReturnValue(rc, invoke, annotation);
            } catch (ClientException e) {
                throw e;
            } catch (ServerException e) {
                logger.error(e.getMessage());
                throw e;
            } catch (Exception e) {
                logger.error(e.getMessage());
                throw new RuntimeException(e);
            }

        };
        return handler;
    }

    void checkArg(Object[] args) {
        for (Object o : args) {
            if (o != null) {
                return;
            }
        }
        throw new ClientException("request parameter.");
    }

    static Object[] parseArgs(Parameter[] parameters, RoutingContext rc) throws RuntimeException {
        List<Object> args = new LinkedList<>();

        // 不支持多值。
        MultiMap params = rc.request().params();

        JsonObject queryObject = new JsonObject();
        params.entries().forEach(entry -> queryObject.put(entry.getKey(), entry.getValue()));

        String header = rc.request().getHeader("Content-Type");

        if (header != null && header.contains("json")) {
            JsonObject bodyAsJson = rc.body().asJsonObject();
            if (!bodyAsJson.isEmpty()) {
                queryObject.mergeIn(bodyAsJson);
            }
        }


        for (Parameter parameter : parameters) {
            Class<?> type = parameter.getType();
            String name = parameter.getName();

            if (type.equals(RoutingContext.class)) {
                args.add(rc);
                continue;
            }

            if (!Reflection.isPrimitiveType(type)) {
                //简单 bean

                try {
                    Field[] declaredFields = type.getDeclaredFields();

                    // 过滤多余的字段
                    Map<String, Object> beanMap = queryObject.stream().filter(e -> {
                        for (Field field : declaredFields) {
                            if (e.getKey().equals(field.getName()))
                                return true;
                        }
                        return false;
                    }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                    JsonObject beanJson = new JsonObject(beanMap);
                    Object bean = beanJson.mapTo(type);

                    for (Field field : declaredFields) {
                        Param fieldAnnotation = field.getAnnotation(Param.class);
                        field.setAccessible(true);
                        Object fieldValue = field.get(bean);
                        if (fieldValue == null) {
                            if (fieldAnnotation != null) {
                                field.set(bean, fieldAnnotation.defaultValue());
                            } else {
                                throw new ClientException("request parameter. " + name + "." + field.getName() + " null");
                            }
                        }


                    }
                    args.add(bean);
                    continue;
                } catch (RuntimeException | IllegalAccessException e) {
                    throw new ClientException("parse bean error." + e.getMessage());
                }
            }


            //  基本类型
            String value = queryObject.getString(name);
            if (value == null) {
                //看是否有默认值
                Param param = parameter.getAnnotation(Param.class);
                if (param != null) {
                    value = param.defaultValue();
                } else {
                    throw new ClientException("request parameter " + name + "is not allowed to be null.");
                }
            }

            try {
                if (type.equals(String.class)) {
                    args.add(value);
                } else if (type.equals(Integer.class) || type.equals(int.class)) {
                    args.add(value.isEmpty() ? null : Integer.parseInt(value));
                } else if (type.equals(Short.class) || type.equals(short.class)) {
                    args.add(value.isEmpty() ? null : Short.parseShort(value));
                } else if (type.equals(Long.class) || type.equals(long.class)) {
                    args.add(value.isEmpty() ? null : Long.parseLong(value));
                } else if (type.equals(Double.class) || type.equals(double.class)) {
                    args.add(value.isEmpty() ? null : Double.parseDouble(value));
                } else if (type.equals(Float.class) || type.equals(float.class)) {
                    args.add(value.isEmpty() ? null : Float.parseFloat(value));
                } else if (type.equals(Boolean.class) || type.equals(boolean.class)) {
                    args.add(value.isEmpty() ? null : Boolean.getBoolean(value));
                } else if (type.equals(Byte.class) || type.equals(byte.class)) {
                    args.add(value.isEmpty() ? null : Byte.parseByte(value));
                } else {
                    throw new ClientException("unsupported type:" + type.getName() + ":" + name + ":" + value);
                }
            } catch (NumberFormatException e) {
                throw new ClientException("bad parameter.");
            } catch (ClientException e) {
                throw e;
            } catch (Exception e) {
                throw new ServerException("system error");
            }


        }
        return args.toArray();
    }

    void parseReturnValue(RoutingContext rc, Object invoke, HttpHandler annotation) throws RuntimeException {
        //在方法内处理完毕
        if (rc.response().ended())
            return;
        //同上
        if (invoke == null) {
            rc.addBodyEndHandler(be -> {
                if (!rc.response().ended())
                    rc.response().end();
            });
        } else {
            // 根据注解，处理返回类型。
            String result = invoke.toString();
            if (annotation.produce().contains("application/json")) {
                if (invoke instanceof JsonObject || invoke instanceof String) {
                    result = invoke.toString();
                } else if (invoke instanceof Map) {
                    result = new JsonObject((Map) invoke).toString();
                } else if (invoke instanceof Object[]) {
                    result = new JsonArray(Arrays.asList((Object[]) invoke)).toString();
                } else if (invoke instanceof List) {
                    result = new JsonArray((List) invoke).toString();
                } else {
                    // 尝试不支持类型
                    try {
                        result = JsonObject.mapFrom(invoke).toString();
                    } catch (Exception e) {
                        throw new ServerException(e.getMessage());
                    }

                }
                rc.response().putHeader("content-type", annotation.produce()).end(result);

            } else if (annotation.produce().contains("text/html")) {
                //html
                TemplateEngine engine = this.application.getTemplateEngine();
                if (engine == null) {
                    renderPlain(rc, result);
                    return;
                }
                Future<Buffer> render = engine.render(rc.data(), "templates/" + result);
                if (render.failed()) {
                    logger.error("render template error. {}", render.cause().getMessage());
                    rc.response().setStatusCode(500).end(render.cause().getMessage());
                } else {
                    rc.response().putHeader("content-type", annotation.produce()).end(render.result());
                }

            } else {
                //默认 按 text/plain 处理
                renderPlain(rc, result);
            }


        }
    }

    private void renderPlain(RoutingContext rc, String body) {
        rc.response().putHeader("content-type", "text/plain; charset=utf-8;").end(body);
    }
}
