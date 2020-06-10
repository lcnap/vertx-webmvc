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

package pers.lcnap.vertx.webmvc.impl;


import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.common.template.TemplateEngine;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.LoggerFormat;
import io.vertx.ext.web.handler.LoggerHandler;
import io.vertx.ext.web.handler.StaticHandler;
import pers.lcnap.vertx.webmvc.ClientException;
import pers.lcnap.vertx.webmvc.HttpHandler;
import pers.lcnap.vertx.webmvc.Param;
import pers.lcnap.vertx.webmvc.SimpleWebApplication;
import pers.lcnap.vertx.webmvc.utils.Reflection;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.stream.Collectors;

public class SimpleWebApplicationImpl implements SimpleWebApplication {
    private final static Logger logger = LoggerFactory.getLogger(SimpleWebApplicationImpl.class);

    private String httpServerConfig = "http-server.json";

    private final String DEFAULT_TEMPLATE_ENGINE = "io.vertx.ext.web.templ.freemarker.FreeMarkerTemplateEngine";

    private Class<?> appClass;

    private Vertx vertx;

    private HttpServer httpServer;

    private TemplateEngine engine;

    private Router rootRouter;

    private HttpServerOptions serverOptions;

    public SimpleWebApplicationImpl(Vertx vertx, Class<?> appClass) {
        this.vertx = vertx;
        this.appClass = appClass;
    }

    public HttpServer run() throws RuntimeException {
        HttpServerOptions serverOptions = readConfigFile();

        httpServer = vertx.createHttpServer(serverOptions);

        rootRouter = Router.router(vertx);

        rootRouter.route().handler(LoggerHandler.create(LoggerFormat.TINY));

        rootRouter.route().failureHandler(rc -> {
            logger.error(rc.failure().getMessage());
            int statusCode = 500;
            if (rc.failure() instanceof ClientException) {
                statusCode = 400;
            }
            rc.response().setStatusCode(statusCode).end(rc.failure().getMessage());

        });

        rootRouter.route().handler(BodyHandler.create());
        rootRouter.route("/static/*").handler(StaticHandler.create("static"));

        scanHttpHandler();

        HttpServer server = httpServer.requestHandler(rootRouter).listen();
        logger.info("server start.");

        return server;
    }

    private HttpServerOptions readConfigFile() {
        JsonObject config = null;
        Buffer buffer;
        try {
            buffer = vertx.fileSystem().readFileBlocking(httpServerConfig);
            config = new JsonObject(buffer);
            logger.info(config.toString());
        } catch (Exception e) {
            logger.info(e.getMessage());
            serverOptions = new HttpServerOptions();
        }

        if (config == null) {
            serverOptions = new HttpServerOptions();
        } else {
            serverOptions = new HttpServerOptions(config);
        }


        String templateEngineClass = DEFAULT_TEMPLATE_ENGINE;
        if (!(config.getString("templateEngine") == null)){
            templateEngineClass = config.getString("templateEngine");
        }
        initEngine(templateEngineClass);
        return serverOptions;
    }

    private void initEngine(String className) {
        if (className == null) {
            return;
        }
        if (engine == null) {
            try {
                Class<?> aClass =
                        Thread.currentThread().getContextClassLoader()
                                .loadClass(className);

                Method create = aClass.getDeclaredMethod("create", Vertx.class);
                engine = (TemplateEngine) create.invoke(aClass, vertx);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private TemplateEngine getTemplateEngine() {
        return engine;
    }

    //扫描业务类
    private void scanHttpHandler() {
        try {
            String pkg = appClass.getPackage().getName();
            Set<Class<?>> handlerClass =
                    Reflection.findHandlerClass(pkg);

            for (Class<?> a : handlerClass) {

                Router classRouter = Router.router(vertx);

                Method[] methods = a.getDeclaredMethods();
                for (Method method : methods) {
                    HttpHandler annotation = method.getAnnotation(HttpHandler.class);
                    if (annotation != null) {
                        //todo 解析方法所需参数。
                        Parameter[] parameters = method.getParameters();

                        Constructor<?> declaredConstructor = a.getDeclaredConstructor();
                        Object o = declaredConstructor.newInstance();

                        Handler<RoutingContext> handler = rc -> {
                            try {
                                //before
                                Object[] args = parseArgs(parameters, rc);
                                //handler
                                Object invoke = method.invoke(o, args);
                                //after
                                parseReturnValue(rc, invoke, annotation);
                            } catch (Exception e) {
                                throw new RuntimeException(e.getCause());
                            }

                        };

                        String path = annotation.path();
                        HttpMethod[] httpMethods = annotation.method();
                        Route route;

                        //方法路由
                        if (httpMethods.length != 1) {
                            route = classRouter.route(path);
                        } else {
                            route = classRouter.route(httpMethods[0], path);
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
                String path = classHttpHandler != null ? classHttpHandler.path() : "/";
                rootRouter.mountSubRouter(path, classRouter);
            }


        } catch (Exception e) {
            logger.error("scan handler failed.", e.getCause());
            vertx.close();
        }
    }

    Object[] parseArgs(Parameter[] parameters, RoutingContext rc) throws RuntimeException {
        List<Object> args = new LinkedList<>();

        // 不支持多值。
        MultiMap params = rc.request().params();

        JsonObject queryObject = new JsonObject();
        params.entries().forEach(entry -> {
            queryObject.put(entry.getKey(), entry.getValue());
        });
        String header = rc.request().getHeader("Content-Type");
        if (header != null && header.contains("json") && rc.getBody().length() != 0) {
            JsonObject bodyAsJson = rc.getBodyAsJson();
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


            //  简单类型
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


            // 不支持 基本类型
            try {

                if (type.equals(String.class)) {
                    args.add(value);
                } else if (type.equals(Integer.class)) {
                    args.add(value.isEmpty() ? null : Integer.parseInt(value));
                } else if (type.equals(Short.class)) {
                    args.add(value.isEmpty() ? null : Short.parseShort(value));
                } else if (type.equals(Long.class)) {
                    args.add(value.isEmpty() ? null : Long.parseLong(value));
                } else if (type.equals(Double.class)) {
                    args.add(value.isEmpty() ? null : Double.parseDouble(value));
                } else if (type.equals(Float.class)) {
                    args.add(value.isEmpty() ? null : Float.parseFloat(value));
                } else if (type.equals(Boolean.class)) {
                    args.add(value.isEmpty() ? null : Boolean.getBoolean(value));
                } else if (type.equals(Byte.class)) {
                    args.add(value.isEmpty() ? null : Byte.parseByte(value));
                } else {
                    throw new RuntimeException("unsupported type." + type.getName());
                }
            } catch (RuntimeException e) {
                throw new ClientException("parse parameters error." + e.getMessage());
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
                    result = JsonObject.mapFrom(invoke).toString();

                }
                rc.response().putHeader("content-type", annotation.produce()).end(result);

            } else if (annotation.produce().contains("text/html") && engine != null) {
                //html
                TemplateEngine engine = getTemplateEngine();
                engine.render(rc.data(), "templates/" + result, ar -> {
                    if (ar.succeeded()) {
                        rc.response().putHeader("content-type", annotation.produce()).end(ar.result());
                    } else {
                        logger.error("render template error. {}", ar.cause().getMessage());
                        rc.response().setStatusCode(500).end(ar.cause().getMessage());
                    }
                });

            } else {
                //默认 按 text/plain 处理
                rc.response().putHeader("content-type", "text/plain; charset=utf-8;").end(result);
            }


        }
    }

}
