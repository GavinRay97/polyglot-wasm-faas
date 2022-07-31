package org.acme;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.File;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HandlerResourceTest {

    @Test
    @Order(1)
    void testUploadHandler() {
        given()
                .multiPart(new File("src/test/resources/bundles/rust-wasm/rust-wasm.zip"))
                .when().post("/api/v1/handler/rust-wasm")
                .then()
                .statusCode(200)
                .body("status", is("ok"))
                .body("message", is("Handler rust-wasm uploaded"));

        given()
                .multiPart(new File("src/test/resources/bundles/javascript/javascript-handler.zip"))
                .when().post("/api/v1/handler/javascript-example")
                .then()
                .statusCode(200)
                .body("status", is("ok"))
                .body("message", is("Handler javascript-example uploaded"));
    }

    @Test
    @Order(2)
    void testInvokeRustWasmHandler() {
        given()
                .contentType("application/json")
                .body("{\"name\":\"John\"}")
                .when().get("/api/v1/handler/rust-wasm")
                .then()
                .statusCode(200)
                .body("name_twice", is("John John"));
    }

    @Test
    @Order(3)
    void testInvokeJavaScriptHandler() {
        given()
                .contentType("application/json")
                .when().get("/api/v1/handler/javascript-example")
                .then()
                .statusCode(200)
                .body("msg", is("Hello from javascript-example"));
    }
}
