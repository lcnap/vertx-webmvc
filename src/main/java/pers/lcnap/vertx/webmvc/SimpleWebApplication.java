package pers.lcnap.vertx.webmvc;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import pers.lcnap.vertx.webmvc.impl.SimpleWebApplicationImpl;

public interface SimpleWebApplication {

    static HttpServer run(Vertx vertx, Class<?> appClass) throws RuntimeException {
        return new SimpleWebApplicationImpl(vertx, appClass).run();
    }
}
