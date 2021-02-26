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

package pers.lcnap.vertx.webmvc;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

@DisplayName("简单测试")
public class SimpleWebApplicationTest {
    @HttpHandler(path = "/main")
    public static class WebApp {

        public static void main(String[] args) {
            SimpleWebApplication.run(Vertx.vertx(), pers.lcnap.vertx.webmvc.test.WebApp.class);
        }
        @HttpHandler(path = "/hi", produce = "text/plain; charset=utf-8")
        public String hi(@Param String msg) {
            return "hi " + msg;
        }


        @HttpHandler(path = "/jsonobject")
        public JsonObject jsonObject() {
            return new JsonObject().put("now", LocalDateTime.now().toString()).put("server", "vertx").put("x", "消息");
        }

        @HttpHandler(path = "/home", produce = "text/html;")
        public String home(RoutingContext routingContext) {
            routingContext.put("msg", "freemarker 中文");
            return "home";
        }

        @HttpHandler(path = "/msg", isBlocking = true)
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
        public void file(RoutingContext routingContext) {
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


    @Test
    public void run1() {
        Future<HttpServer> run = SimpleWebApplication.run(Vertx.vertx(), SimpleWebApplication.class);
    }

    @Test
    public void run2() {
        SimpleWebApplication.run(Vertx.vertx(), WebApp.class);
    }

}