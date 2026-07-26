package com.ensemblu.axiom.jdbc.strike.router;

import com.ensemblu.axiom.api.Axiom;
import com.ensemblu.axiom.core.data_structure.list.PersistentList;
import com.ensemblu.axiom.core.data_structure.map.PersistentMap;
import com.ensemblu.axiom.core.foundation.Dop;
import com.ensemblu.axiom.core.validation.If;
import com.ensemblu.axiom.core.validation.Result;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Predicate;

public final class Router implements HttpHandler {
    private final String prefix;
    private final PersistentList<PersistentMap<String,Object>> entries ;

    private Router(String prefix, PersistentList<PersistentMap<String,Object>> entries) {
        String cleanPrefix = prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
        if (!cleanPrefix.isEmpty() && !cleanPrefix.startsWith("/")) {
            cleanPrefix = "/" + cleanPrefix;
        }
        this.prefix = cleanPrefix;
        this.entries = entries;
    }

    private Router(PersistentList<PersistentMap<String,Object>> entries) {
        this.prefix = "";
        this.entries = entries;
    }
    public static Router route() {
        return new Router(PersistentList.empty());
    }

    public static Router groupBy(final String prefix) {
        String cleanPrefix = prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
        if (!cleanPrefix.isEmpty() && !cleanPrefix.startsWith("/")) {
            cleanPrefix = "/" + cleanPrefix;
        }
        return new Router(cleanPrefix, PersistentList.empty());
    }

    @FunctionalInterface
    public interface HandlerFunction {
        // Input: The raw request (or byte stream)
        // Output: Your Result domain (Success/Failure)
        Result<PersistentMap<String, Object>> handle(String rawContent);
    }

    private Router on(MethodType method, String path, HandlerFunction handler) {
        Objects.requireNonNull(method, "method is null in 'on' of Router");


        If//
        .givenObject(path)//
        .isNonNull("path")//
        .andIsNot(String::isBlank,"path is blank")//
        .andOtherObjectIsNotNull(method,"method")//
        .andOtherObjectIsNotNull(handler,"handler")//
        .will()//
        .getResult()//
        .prependMethodNameToFailureMessage("private method 'on' of class 'Router':")//
        .getOrThrow();//


        final var cleanPath =  !path.startsWith("/") ? "/" + path :path ;

        final var routeEntry = Axiom.Data
                .<String, Object>emptyMap()
                .put("method", method.name())
                .put("path", prefix+cleanPath)
                .put("handler", handler);

        return new Router(prefix, entries.append(routeEntry));
    }

    private enum MethodType{
        GET,POST,PUT,DELETE,PATCH,HEAD;
    }


    // 2. The Full Verb Set (Zero bloat, just wrappers)
    public WithHandler GET(String path)    { return handler -> on(MethodType.GET, path, handler); }
    public WithHandler POST(String path)   { return handler ->  on(MethodType.POST, path, handler); }
    public WithHandler PUT(String path)    { return handler ->  on(MethodType.PUT, path, handler); }
    public WithHandler DELETE(String path) { return handler ->  on(MethodType.DELETE, path, handler); }
    public WithHandler PATCH(String path)  { return handler ->  on(MethodType.PATCH, path, handler); }
    public WithHandler HEAD(String path)   { return handler ->  on(MethodType.HEAD, path, handler); }

    public interface WithHandler{
        Router withHandler(HandlerFunction handler);
    }

    public interface WithPath{
        OnMethod withPath(String path);
    }

    public interface OnMethod {
        OnData onMethod(MethodType method);
    }

    public interface OnData{
        Result<PersistentMap<String, Object>> onData(String rawContent);
    }

    public WithPath build() {
        return path ->  //
                method -> //
                        data ->{//
            If//
            .givenObject(path)//
            .isNot( Objects::isNull ,"path is null in 'withPath' of Router")//
                  .andIsNot(String::isBlank,"path is blank in 'withPath' of Router")//
            .andIsNot(   _ ->     Objects.isNull(method) ,   "method is null in 'onMethod' of Router")//
            .andIsNot(     _ ->     Objects.isNull(data) ,   "data is null in 'onData' of Router")//
            .will()//
            .thenApprovedOrElseThrowException();

            final Predicate<PersistentMap<String, Object>> matchPath =  //
                    e -> e.targetKey("path").toStringVal().equals(path);

                            // Now you are comparing the enum name, which is safer
            final Predicate<PersistentMap<String, Object>> matchMethod =//
                                    e -> e.targetKey("method").toStringVal()
                                            .equalsIgnoreCase(method.name());
            return entries//
                    .findFirst(matchPath.and(matchMethod))//
                    .map(e ->//
                            ((HandlerFunction)e.get("handler")).handle(data))//
                    .getOrElse(Axiom.Check.failure(new RuntimeException("Route not found")));//
        };
    }


    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if(entries.isEmpty())
            return;

        // 1. Extract the raw input
        final var rawContent = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        // 2. Dispatch via the API (your Router)
        final var result = this.build()//
                .withPath(exchange.getRequestURI().getPath())//
                .onMethod(Router.MethodType.valueOf(exchange.getRequestMethod())) //
                .onData(rawContent);

        // 3. Translate Result to Response
        if (result.isSuccess()) {
            sendResponse(exchange, 200, Dop.toJson(result.getOrThrow()));
        } else {
            sendResponse(exchange, 404, "Route not found or logic failed");
        }
    }

    private void sendResponse(HttpExchange exchange, int status, String response) throws IOException {
        final var bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
    }

}