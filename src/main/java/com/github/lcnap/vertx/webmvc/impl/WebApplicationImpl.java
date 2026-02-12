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

package com.github.lcnap.vertx.webmvc.impl;


import com.github.lcnap.vertx.webmvc.ClientException;
import com.github.lcnap.vertx.webmvc.WebApplication;
import com.github.lcnap.vertx.webmvc.annotation.AnnotationScanner;
import com.github.lcnap.vertx.webmvc.handler.RequestIdHandler;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.common.template.TemplateEngine;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.LoggerFormat;
import io.vertx.ext.web.handler.LoggerHandler;
import io.vertx.ext.web.handler.StaticHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

public class WebApplicationImpl implements WebApplication {
    private final static Logger logger = LoggerFactory.getLogger(WebApplicationImpl.class);

    private final static String httpServerConfig = "http-server.json";

    private final static String DEFAULT_TEMPLATE_ENGINE = "io.vertx.ext.web.templ.freemarker.FreeMarkerTemplateEngine";

    public Class<?> appClass() {
        return appClass;
    }

    public Vertx vertx() {
        return vertx;
    }

    public HttpServer httpServer() {
        return httpServer;
    }

    public TemplateEngine engine() {
        return engine;
    }

    public Router rootRouter() {
        return rootRouter;
    }

    public HttpServerOptions serverOptions() {
        return serverOptions;
    }

    private final Class<?> appClass;

    private final Vertx vertx;

    private HttpServer httpServer;

    private TemplateEngine engine;

    private Router rootRouter;

    private HttpServerOptions serverOptions;

    private final AnnotationScanner annotationScanner;

    public WebApplicationImpl(Vertx vertx, Class<?> appClass) {
        this.vertx = vertx;
        this.appClass = appClass;

        this.annotationScanner = new AnnotationScanner(this);
    }

    public Future<HttpServer> run() throws RuntimeException {
        HttpServerOptions serverOptions = readConfigFile();

        httpServer = vertx.createHttpServer(serverOptions);

        rootRouter = Router.router(vertx);

        rootRouter.route().handler(RequestIdHandler.create());
        rootRouter.route().handler(LoggerHandler.create(LoggerFormat.SHORT));

        rootRouter.route().failureHandler(rc -> {
            logger.error("detect error.", rc.failure());
            int statusCode = 500;
            Throwable failure = rc.failure();
            if (failure instanceof ClientException || failure.getCause() instanceof ClientException) {
                statusCode = 400;
            }
            rc.response().setStatusCode(statusCode).end(rc.failure().getMessage());

        });

        rootRouter.route().handler(BodyHandler.create());
        rootRouter.route("/static/*").handler(StaticHandler.create("static"));

        this.annotationScanner.scanHttpHandler();

        Future<HttpServer> listen = httpServer.requestHandler(rootRouter).listen();
        listen.onFailure(f -> {
            logger.error("server listen failed.", f);
        });

        Runtime.getRuntime().addShutdownHook(new Thread(WebApplicationImpl.this::stop));

        return listen;
    }

    private HttpServerOptions readConfigFile() {
        JsonObject config = null;
        Buffer buffer;
        try {
            buffer = vertx.fileSystem().readFileBlocking(httpServerConfig);
            config = new JsonObject(buffer);
            logger.info(config.toString());
        } catch (Exception e) {
            logger.error("load config error.use default config.", e);
            serverOptions = new HttpServerOptions();
        }

        if (config == null) {
            serverOptions = new HttpServerOptions();
        } else {
            serverOptions = new HttpServerOptions(config);
        }

        String templateEngineClass = null;
        if (config != null) {
            templateEngineClass = config.getString("templateEngine");
        }
        initEngine(templateEngineClass);
        return serverOptions;
    }

    private void initEngine(String className) {
        if (className == null) {
            className = DEFAULT_TEMPLATE_ENGINE;
        }
        if (engine == null) {
            try {
                Class<?> aClass =
                        Thread.currentThread().getContextClassLoader()
                                .loadClass(className);

                Method create = aClass.getDeclaredMethod("create", Vertx.class);
                engine = (TemplateEngine) create.invoke(aClass, vertx);
            } catch (Exception e) {
                throw new RuntimeException("init engine failed.", e);
            }
        }
    }

    public TemplateEngine getTemplateEngine() {
        return engine;
    }


    @Override
    public void stop() {
        Future<Void> f = this.vertx.close();
        f.onFailure(e -> {
            logger.error("server stop failed.", e);
            System.exit(-1);
        }).await();

    }
}
