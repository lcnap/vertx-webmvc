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
