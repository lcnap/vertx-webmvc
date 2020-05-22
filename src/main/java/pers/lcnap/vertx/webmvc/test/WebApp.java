package pers.lcnap.vertx.webmvc.test;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import pers.lcnap.vertx.webmvc.HttpHandler;
import pers.lcnap.vertx.webmvc.Param;
import pers.lcnap.vertx.webmvc.SimpleWebApplication;

import java.time.LocalDateTime;

@HttpHandler(path = "/main")
public class WebApp {

    public static void main(String[] args) {
        SimpleWebApplication.run(Vertx.vertx(),WebApp.class);
    }

    @HttpHandler(path = "/hi",contentType = "text/plain; charset=utf-8")
    public String hi(@Param String msg){
        return "hi " + msg;
    }


    @HttpHandler(path = "/jsonobject")
    public JsonObject jsonObject(){
        return new JsonObject().put("now", LocalDateTime.now().toString()).put("server","vertx").put("x","消息");
    }

    @HttpHandler(path = "/home",contentType = "text/html;")
    public String home(RoutingContext routingContext){
        routingContext.put("msg","freemarker 中文");
        return "home";
    }

    @HttpHandler(path = "/msg",isBlocking = true)
    public Object object() throws InterruptedException {
        Msg msg = new Msg();
        Thread.sleep(5000);
        return msg;
    }

    static class Msg {
        public String code = "xx";
        public String content = "xxx";
        public String date = LocalDateTime.now().toString();
    }

    @HttpHandler(path = "/file")
    public void file(RoutingContext routingContext){
        routingContext.response().sendFile("file.exe");

        /*routingContext.vertx().fileSystem().readFile("",ar -> {
            if (ar.succeeded()){
                routingContext.response().end(ar.result());
            } else {
                routingContext.response().end(ar.cause().getMessage());
            }
        });*/
    }
}
