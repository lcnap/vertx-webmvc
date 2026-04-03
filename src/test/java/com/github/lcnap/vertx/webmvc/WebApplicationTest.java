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

package com.github.lcnap.vertx.webmvc;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.Map;

@DisplayName("简单测试")
@ExtendWith(VertxExtension.class)
public class WebApplicationTest {
    final static Logger logger = LoggerFactory.getLogger(WebApplicationTest.class);

    public enum Status {
        YES("01"), NO("02");

        private final String code;

        Status(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    @HttpHandler(path = "/main")
    public static class WebApp {

        public static void main(String[] args) {
            WebApplication.run(Vertx.vertx(), WebApp.class);

        }

        @HttpHandler(path = "/hi", produce = "text/plain; charset=utf-8")
        public String hi(int msg) {
            return "hi " + msg;
        }

        @HttpHandler(path = "/conv")
        public String conv(Long msg) {
            return "hi " + msg;
        }


        @HttpHandler(path = "/jsonobject")
        public JsonObject jsonObject() {
            return new JsonObject().put("now", "2026-01-24").put("server", "vertx").put("x", "消息");
        }

        @HttpHandler(path = "/home", produce = "text/html;")
        public String home(RoutingContext routingContext) {
            routingContext.put("msg", "freemarker 中文");
            routingContext.put("code", "300");
            routingContext.put("title", "testcase");
            return "home";
        }

        @HttpHandler(path = "/msg", isBlocking = true)
        public Object object() throws InterruptedException {
            Msg msg = new Msg();
            Thread.sleep(3000);
            return msg;
        }

        @HttpHandler(path = "/bean")
        public Bean bean(Bean bean) {
            return bean;
        }

        static class Bean {
            public int code;
            @Param(size = 10)
            public String msg;
        }

        static class Msg {
            public String code = "xx";
            public String content = "xxx";
            public String date = "2026-01-24";
        }


        @HttpHandler(path = "/file")
        public void file(RoutingContext routingContext) {
            routingContext.response().sendFile("file");
        }


        public static class Exam {

            public String address() {
                return address;
            }

            public void setAddress(String address) {
                this.address = address;
            }

            @Param(defaultValue = "10")
            private Integer id;

            @Param(required = false)
            private String name;

            @Param(size = 1, defaultValue = "1")
            private String age;

            @Param(limit = {
                    "深圳", "北京", "GZ"
            })
            private String address;


            public LocalDate getDate() {
                return date;
            }

            public void setDate(LocalDate date) {
                this.date = date;
            }

            public String getAddress() {
                return address;
            }

            @Param(defaultValue = "2020-01-01")
            private LocalDate date;

            @Param(defaultValue = "YES")
            private Status status;

            @Param(max = 100)
            private long m;

            @Param(defaultValue = "1")
            private int i;

            @Param(defaultValue = "1", rule = "value.toInteger() > 10")
            private String j;

            public Integer getId() {
                return id;
            }

            public void setId(Integer id) {
                this.id = id;
            }

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public String getAge() {
                return age;
            }

            public void setAge(String age) {
                this.age = age;
            }


            public Status getStatus() {
                return status;
            }

            public void setStatus(Status status) {
                this.status = status;
            }

            public long getM() {
                return m;
            }

            public void setM(long m) {
                this.m = m;
            }

            public int getI() {
                return i;
            }

            public void setI(int i) {
                this.i = i;
            }

            public String getJ() {
                return j;
            }

            public void setJ(String j) {
                this.j = j;
            }
        }


        @HttpHandler(path = "/exam")
        public Object exam(Exam exam) {
            return exam;
        }

    }


    @BeforeEach
    public void startServer(Vertx vertx, VertxTestContext testContext) {
        Future<HttpServer> run = WebApplication.run(vertx, WebApplication.class);
        run.onSuccess(httpServer -> {
            testContext.completeNow();
        });

    }


    @Test
    public void run1(Vertx vertx, VertxTestContext testContext) {
        int port = 8081;
        String file = vertx.fileSystem().readFileBlocking("file").toString();
        Map<String, String> testCases = Map.of(
                "/main/hi?msg=2026", "hi 2026",
                "/main/jsonobject", new JsonObject().put("now", "2026-01-24").put("server", "vertx").put("x", "消息").toString(),
                "/main/msg", JsonObject.mapFrom(new WebApp.Msg()).toString(),
                //"/main/file", file,
                "/main/bean?code=12&msg=fd232", new JsonObject().put("code", "12").put("msg", "fd232").toString(),
                "/main/exam?address=GZ&name=12&age=2", new JsonObject().toString()

        );

        WebClient client = WebClient.create(vertx);

        testCases.forEach((k, v) -> {
            logger.info("testcase: path: {};want: {}", k, v);
            client.get(port, "localhost", k).send()
                    .onSuccess(resp -> {
                        testContext.verify(() -> {
                            if (resp.statusCode() >= 300) {
                                Assertions.fail(resp.statusMessage());
                            }
                            if (resp.headers().get("Content-Type").contains("json")) {
                                Assertions.assertEquals(v, resp.bodyAsJsonObject().toString());
                            } else {
                                Assertions.assertEquals(v, resp.bodyAsString());
                            }

                            testContext.completeNow();
                        });
                    })
                    .onFailure(testContext::failNow).await();//等待结果,避免测试方法提前结束
        });


    }

}