package pers.lcnap.vertx.websupport;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import pers.lcnap.vertx.websupport.impl.SimpleWebApplicationImpl;

public interface SimpleWebApplication {

    static HttpServer run(Vertx vertx, Class<?> appClass) throws RuntimeException {
        return new SimpleWebApplicationImpl(vertx, appClass).run();
    }
}
