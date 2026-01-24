# vertx-webmvc

#### 描述

基于vertx-web的SpringMVC风格的框架。

#### 使用

```xml
<dependency>
    <groupId>com.github.lcnap</groupId>
    <artifactId>vertx-webmvc</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

#### 启动
直接通过接口启动即可。appClass 要在最顶层。
```
    SimpleWebApplication.run(Vertx vertx, Class<?> appClass);
```

#### 业务代码样例

详细参考 `test/.../SimpleWebApplicationTest`

```java
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
框架新增:

```
{
  "templateEngine": "io.vertx.ext.web.templ.freemarker.FreeMarkerTemplateEngine"
}
```

默认模板引擎是:freemarker。
其他配置项，参考vertx的 `HttpServerOptions`

#### 注意
编译的时候，记得加 -parameters。

#### 更新说明

2026-01-24 更新依赖Vertx5.0.7版本。调整项目结构。