package org.acme.entity;

import io.vertx.ext.web.RoutingContext;

@FunctionalInterface
public interface PolyglotRequestHandler {
    void handle(RoutingContext request);
}
