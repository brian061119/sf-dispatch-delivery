package com.wedelivery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class GeminiDeliveryClientTest {
    ObjectMapper mapper=new ObjectMapper();
    @Test void missingKeyHasActionableError() {
        GeminiDeliveryClient client=new GeminiDeliveryClient(mapper,"","test-model","http://localhost");
        assertThatThrownBy(()->client.extract("send",List.of())).hasMessageContaining("GEMINI_API_KEY");
    }
    @Test void sendsSchemaAndReadsProviderResponseWithoutRealCredentials() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/models/test-model:generateContent", exchange->{
            String request=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
            assertThat(request).contains("responseSchema","systemInstruction","history", "TRUSTED PRODUCT KNOWLEDGE", "Two carrier types", "Cancellation is allowed only before", "Questions about cancellation");
            assertThat(exchange.getRequestHeaders().getFirst("x-goog-api-key")).isEqualTo("fake-test-key");
            byte[] body="{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"intent\\\":\\\"DRAFT\\\",\\\"weightKg\\\":2}\"}]}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
        });server.start();
        try {
            GeminiDeliveryClient client=new GeminiDeliveryClient(mapper,"fake-test-key","test-model","http://127.0.0.1:"+server.getAddress().getPort());
            assertThat(client.extract("send 2kg",List.of()).path("weightKg").asInt()).isEqualTo(2);
        } finally { server.stop(0); }
    }
    @Test void quotaErrorsNeverLeakKeyOrProviderBody() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{byte[] body="secret provider response fake-test-key".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(429,body.length);exchange.getResponseBody().write(body);exchange.close();});server.start();
        try {
            GeminiDeliveryClient client=new GeminiDeliveryClient(mapper,"fake-test-key","test-model","http://127.0.0.1:"+server.getAddress().getPort());
            assertThatThrownBy(()->client.extract("hello",List.of())).hasMessageContaining("quota").hasMessageNotContaining("fake-test-key").hasMessageNotContaining("secret");
        } finally {server.stop(0);}
    }
    @Test void retriesTransient503AndRecovers() throws Exception {
        java.util.concurrent.atomic.AtomicInteger count=new java.util.concurrent.atomic.AtomicInteger();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/", exchange->{
            int attempt=count.incrementAndGet();
            byte[] body=(attempt==1 ? "busy" : "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"intent\\\":\\\"QUOTE\\\"}\"}]}}]}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(attempt==1?503:200,body.length);exchange.getResponseBody().write(body);exchange.close();
        });server.start();
        try {
            GeminiDeliveryClient client=new GeminiDeliveryClient(mapper,"fake-test-key","test-model","http://127.0.0.1:"+server.getAddress().getPort());
            assertThat(client.extract("quote",List.of()).path("intent").asText()).isEqualTo("QUOTE");
            assertThat(count.get()).isEqualTo(2);
        } finally {server.stop(0);}
    }
    @Test void persistent503StopsAfterThreeAttempts() throws Exception {
        java.util.concurrent.atomic.AtomicInteger count=new java.util.concurrent.atomic.AtomicInteger();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{count.incrementAndGet();byte[] body="busy".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(503,body.length);exchange.getResponseBody().write(body);exchange.close();});server.start();
        try {
            GeminiDeliveryClient client=new GeminiDeliveryClient(mapper,"fake-test-key","test-model","http://127.0.0.1:"+server.getAddress().getPort());
            assertThatThrownBy(()->client.extract("quote",List.of())).hasMessageContaining("temporarily unavailable");
            assertThat(count.get()).isEqualTo(3);
        } finally {server.stop(0);}
    }

}
