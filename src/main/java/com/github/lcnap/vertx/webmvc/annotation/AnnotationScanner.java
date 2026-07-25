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

import com.github.lcnap.vertx.webmvc.*;
import com.github.lcnap.vertx.webmvc.handler.ShareMdcBlockingHandlerWrapper;
import com.github.lcnap.vertx.webmvc.impl.WebApplicationImpl;
import com.github.lcnap.vertx.webmvc.utils.Reflection;
import com.github.lcnap.vertx.webmvc.utils.TypeConverter;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.common.template.TemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.stream.Collectors;

public class AnnotationScanner {
    private final static Logger logger = LoggerFactory.getLogger(AnnotationScanner.class);

    WebApplicationImpl application;

    private final static ParamHandler paramHandler = new ParamHandler();

    public AnnotationScanner(WebApplicationImpl application) {
        this.application = application;
    }


    //扫描业务类
    public void scanHttpHandler() {
        try {
            String pkg = this.application.appClass().getPackage().getName();
            Set<Class<?>> handlerClass =
                    Reflection.findHandler(pkg);

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
                            route.blockingHandler(new ShareMdcBlockingHandlerWrapper(handler));
                        } else {
                            route.handler(handler);
                        }


                    }
                }

                //类上的注解，只有path有效
                HttpHandler classHttpHandler = a.getAnnotation(HttpHandler.class);
                String path = classHttpHandler != null ? classHttpHandler.path() : "";
                this.application.rootRouter().route(path + "/*").subRouter(classRouter);
            }


        } catch (Exception e) {
            logger.error("scan handler failed.", e);
            this.application.vertx().close();
        }
    }

    private Handler<RoutingContext> proxyHandler(Class<?> a, Method method, HttpHandler annotation) throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        Parameter[] parameters = method.getParameters();

        Object o = application.getBean(a);

        Handler<RoutingContext> handler = rc -> {
            try {
                //before
                Object[] args = parseArgs(parameters, rc);
                //handler
                //checkArg(args);
                Object invoke = method.invoke(o, args);
                //after
                parseReturnValue(rc, invoke, annotation);
            } catch (ClientException | ServerException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("uncheck exception.", e);
            }

        };
        return handler;
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
            var path = parameter.getAnnotation(PathParam.class);
            if (path != null) {
                var key = !path.value().isBlank() ? path.value() : parameter.getName();
                var value = rc.pathParam(key);
                if (value == null) {
                    throw new ClientException("path parameter " + key + " is null.");
                }
                args.add(value);
                continue;
            }

            Class<?> type = parameter.getType();
            String name = parameter.getName();

            // 1、routingcontext 注入;已测试
            if (type.equals(RoutingContext.class)) {
                args.add(rc);
                continue;
            }

            // 2、vertx 注入；已测试
            if (type.equals(Vertx.class)) {
                args.add(rc.vertx());
                continue;
            }

            //3、参数注入与校验
            if (!Reflection.isPrimitiveType(type)) {
                //3.1、简单 bean 注入

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
                        if (fieldAnnotation != null) {
                            var defaultValue = paramHandler.handle(fieldAnnotation, field.getType(), field, fieldValue);
                            if (defaultValue != null) {
                                field.set(bean, defaultValue);
                            }
                        } else {
                            // throw new ClientException("request parameter: " + name + "." + field.getName() + " is null");
                        }


                    }
                    args.add(bean);
                    continue;
                } catch (RuntimeException | IllegalAccessException e) {
                    throw new ClientException("parse bean error. " + e.getMessage(), e.getCause());
                }
            }


            // 4、基本类型注入
            String valueStr = queryObject.getString(name);
            //缺失或空串
            if (valueStr == null || valueStr.isBlank()) {
                //看是否有默认值
                Param param = parameter.getAnnotation(Param.class);
                if (param != null) {
                    valueStr = param.defaultValue();
                } else {
                    //基本类型没有默认值，将初始化为0
                    valueStr = "";
                }

            }

            try {
                Object convert = TypeConverter.convert(type, valueStr);
                args.add(convert);

            } catch (UnsupportedOperationException | IllegalArgumentException e) {
                throw new ClientException("bad parameter.", e);
            } catch (ClientException e) {
                throw e;
            } catch (Exception e) {
                throw new ServerException("server error.", e);
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
                if (invoke instanceof JsonObject
                        || invoke instanceof JsonArray || invoke instanceof String) {
                    result = invoke.toString();
                } else if (invoke instanceof Map) {
                    result = new JsonObject((Map) invoke).toString();
                } else if (invoke instanceof Object[]) {
                    result = new JsonArray(Arrays.asList((Object[]) invoke)).toString();
                } else if (invoke instanceof List) {
                    result = new JsonArray((List) invoke).toString();
                } else {
                    // 尝试 bean
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
                    logger.error("render template error.", render.cause());
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
