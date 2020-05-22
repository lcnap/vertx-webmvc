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

import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;

public class SimpleWebApplicationImpl implements SimpleWebApplication {
    private final static Logger logger = LoggerFactory.getLogger(SimpleWebApplicationImpl.class);

    private String httpServerConfig = "http-server.json";

    private final String DEFAULT_TEMPLATEENGINE = "io.vertx.ext.web.templ.freemarker.FreeMarkerTemplateEngine";

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
        JsonObject config = null;
        Buffer buffer = null;
        try {
            buffer = vertx.fileSystem().readFileBlocking(httpServerConfig);
            config = new JsonObject(buffer);
            logger.info(config.toString());
        }catch (Exception e){
            logger.info(e.getMessage());
            serverOptions = new HttpServerOptions();
        }

        if (config == null) {
            serverOptions = new HttpServerOptions();
        } else {
            serverOptions = new HttpServerOptions(config);
        }



        String templateEngineClass = DEFAULT_TEMPLATEENGINE;
        if (!(config.getString("templateEngine") == null)){
            templateEngineClass = config.getString("templateEngine");
        }
        initEngine(templateEngineClass);



        httpServer = vertx.createHttpServer(serverOptions);

        rootRouter = Router.router(vertx);
        rootRouter.route().handler(LoggerHandler.create(LoggerFormat.TINY));
        rootRouter.route().failureHandler(rc -> {
            logger.error(rc.failure().getMessage());
            if (rc.failure() instanceof ClientException) {
                rc.response().setStatusCode(400).end(rc.failure().getMessage());
            } else {
                rc.response().setStatusCode(500).end(rc.failure().getMessage());
            }

        });
        rootRouter.route().handler(BodyHandler.create());


        rootRouter.route("/static/*").handler(StaticHandler.create("static"));



        scanHttpHandler();


        HttpServer server = httpServer.requestHandler(rootRouter).listen();

        logger.info("server start.");
        return server;
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
            } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
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
                        Route route ;
                        if (httpMethods.length != 1) {
                            route = classRouter.route(path);
                        } else {
                            route = classRouter.route(httpMethods[0], path);
                        }
                        if(annotation.consumes().contains("text")){
                            route = route.consumes("text/*");
                        } else if (annotation.consumes().contains("json")){
                            route = route.consumes("*/json");
                        }

                        if(annotation.isBlocking()){
                            route.blockingHandler(handler);
                        }else {
                            route.handler(handler);
                        }


                    }
                }
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



        //todo 不支持多值。
        MultiMap params = rc.request().params();
        JsonObject queryObject = new JsonObject();
        params.entries().forEach(entry -> {
            queryObject.put(entry.getKey(), entry.getValue());
        });

        for (Parameter parameter : parameters) {
            Class<?> type = parameter.getType();
            String name = parameter.getName();

            if (type.equals(RoutingContext.class)) {
                args.add(rc);
                continue;
            }

            if (!Reflection.isPrimitiveType(type)) {
                //bean

                try {
                    Field[] declaredFields = type.getDeclaredFields();

                    JsonObject allArgsJson = queryObject;
                    // 过滤多余的字段
                    Map<String, Object> beanMap = allArgsJson.stream().filter(e -> {
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
                Param param = parameter.getAnnotation(Param.class);
                if (param == null /*|| param.request()*/) {
                    throw new ClientException("request parameter. " + name + " null");
                } else {
                    value = param.defaultValue();
                }
            }


            try {
                if (type.equals(String.class)) {
                    args.add(value);
                } else if (type.equals(Integer.class)) {
                    args.add(value.isEmpty() ? 0 : Integer.parseInt(value));
                } else if (type.equals(Short.class)) {
                    args.add(value.isEmpty() ? 0 : Short.parseShort(value));
                } else if (type.equals(Long.class)) {
                    args.add(value.isEmpty() ? 0L : Long.parseLong(value));
                } else if (type.equals(Double.class)) {
                    args.add(value.isEmpty() ? 0L : Double.parseDouble(value));
                } else if (type.equals(Float.class)) {
                    args.add(value.isEmpty() ? 0f : Float.parseFloat(value));
                } else {
                    // 完善
                    throw new RuntimeException("unsupported type." + type.getName());
                }
            } catch (RuntimeException e) {
                throw new ClientException("parse parameters error." + e.getMessage());
            }

        }
        return args.toArray();
    }

    void parseReturnValue(RoutingContext rc, Object invoke, HttpHandler annotation) throws RuntimeException {
        if (rc.response().ended())
            return;
        if (invoke == null) {
            rc.addBodyEndHandler(be -> {
                if (!rc.response().ended())
                    rc.response().end();
            });
        } else {
            // 根据注解，处理返回类型。
            String result = invoke.toString();
            if (annotation.contentType().contains("application/json")) {
                if (invoke instanceof JsonObject || invoke instanceof String) {
                    result = invoke.toString();
                } else if (invoke instanceof Map) {
                    result = new JsonObject((Map) invoke).toString();
                } else if (invoke instanceof Object[]) {
                    result = new JsonArray(Arrays.asList((Object[]) invoke)).toString();
                } else if (invoke instanceof List) {
                    result = new JsonArray((List) invoke).toString();
                } else {
                    //
                    result = JsonObject.mapFrom(invoke).toString();
                }
                rc.response().putHeader("content-type", annotation.contentType()).end(result);

            } else if (annotation.contentType().contains("text/html") && engine != null) {
                //html
                TemplateEngine engine = getTemplateEngine();
                if (engine == null){
                    throw new RuntimeException("Template engine null.");
                }
                engine.render(rc.data(), "templates/" + result, ar -> {
                    if (ar.succeeded()) {
                        rc.response().putHeader("content-type", annotation.contentType()).end(ar.result());
                    } else {
                        logger.error("render template error. {}", ar.cause().getMessage());
                        rc.response().setStatusCode(500).end(ar.cause().getMessage());
                    }
                });

            } else {
                //text/plain
                rc.response().putHeader("content-type", "text/plain; charset=utf-8;").end(result);
            }


        }
    }

}
