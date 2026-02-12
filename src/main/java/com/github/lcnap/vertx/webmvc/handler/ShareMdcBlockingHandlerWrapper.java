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

package com.github.lcnap.vertx.webmvc.handler;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.MDC;

import java.util.Map;

/**
 * 在异步线程和阻塞线程池之间分享MDC
 */
public class ShareMdcBlockingHandlerWrapper implements Handler<RoutingContext> {
    private Handler<RoutingContext> innerHandler;

    public ShareMdcBlockingHandlerWrapper(Handler<RoutingContext> innerHandler) {
        this.innerHandler = innerHandler;
    }

    public static ShareMdcBlockingHandlerWrapper create(Handler<RoutingContext> innerHandler) {
        return new ShareMdcBlockingHandlerWrapper(innerHandler);
    }

    @Override
    public void handle(RoutingContext rc) {
        Map<String, String> mdc = rc.get("mdc");
        MDC.setContextMap(mdc);
        innerHandler.handle(rc);
        MDC.clear();
    }

}
