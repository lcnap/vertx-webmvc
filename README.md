# vertx-webmvc

#### 描述
一个简单、用于练手的使vertx-web支持 SpringMVC 风格的脚手架，或工具类。

#### 启动
直接通过接口启动即可。appClass 要在最顶层。
```
   SimpleWebApplication.run(Vertx vertx, Class<?> appClass);
```

#### 业务代码
```
    @HttpHandler(path = "/hi",contentType = "text/plain; charset=utf-8")
    public String hi(@Param String msg){
        return "hi " + msg;
    }

    @HttpHandler(path = "/home",contentType = "text/html;")
    public String home(RoutingContext routingContext){
        routingContext.put("msg","freemarker 中文");
        return "home";
    }

    @HttpHandler(path = "/jsonobject")
    public JsonObject jsonObject(){
        return new JsonObject().put("now", LocalDateTime.now().toString())
                               .put("server","vertx")
                               .put("x","消息");
    }
```

#### 配置
默认配置文件是 **resources/http-server.json** 
只加了，  **"templateEngine": "io.vertx.ext.web.templ.freemarker.FreeMarkerTemplateEngine"**  这个自定义配置项，
其他值参考vertx文档。
默认模板引擎是，freemarker。
其他配置项，参考vertx的 `HttpServerOptions`

#### 注意
编译的时候，记得加 -parameters