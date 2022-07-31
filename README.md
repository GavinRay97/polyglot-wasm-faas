# Build-your-own (shitty) AWS Lambda/OpenFaaS with Quarkus + GraalVM

This project is an example of how you can use GraalVM's `Polyglot` functionality to deploy API handlers from WASM modules or scripting languages while the application is live.

> Disclaimer: This project is not production-ready. In particular, there are several optimizations which can be made in regards to GraalVM evaluation by defining a shared `Engine`, better caching of `Source` values, etc.

By the end of this 5-minute read, we'll have an app that can deploy both the below Rust function (as WASM), and the below JavaScript function to an API endpoint:
- Rust: https://github.com/GavinRay97/polyglot-wasm-faas/blob/2760f118753b29e51e1b25a7a2d9cbc6808f30b5/src/test/resources/rust-wasm-samples/hello-wasm/src/lib.rs#L17-L25
- JavaScript: https://github.com/GavinRay97/polyglot-wasm-faas/blob/2760f118753b29e51e1b25a7a2d9cbc6808f30b5/src/test/resources/bundles/javascript/handler.js#L1-L4

## How it works

At the end, what we'd like to end up with is:

1. A way for users to package up some source code along with a bit of metadata
2. Have that code be deployable to a user-defined API endpoint, e.g. `/myhandler`

A simple design for this would be to have a bundle of the following:
- Source code files
- A metadata file, declaring the language of the code, and the entrypoint file for the program

You might imagine something like:
```json
{
  "language": "js",
  "entrypointFile": "main.js"
}
```
```js
// main.js
function handler(ctx) {
    ctx.response().end("Hello world")
}
// Return handler as last line of code so a reference to function is passed
handler
```

These files could be zipped up into a "bundle", and uploaded to the server for the server to deploy.

To solve this problem, we can start by defining data models. We want an entity to represent the above-mentioned metadata about a handler, and we want an entity to glue together that metadata with the physical files and location on-disk.

```java
enum PolyglotLanguage {
    JAVASCRIPT("js"),
    PYTHON("python"),
    RUBY("ruby"),
    WASM("wasm")
}

record PolyglotFunctionMetadata(
        PolyglotLanguage language,
        String entrypointFile) {
}

record PolyglotFunctionBundle(
        String name,
        PolyglotFunctionMetadata metadata,
        Path directory) {
    
}
```

The next thing we need is an API handler that can consume a `.zip` file upload and extract the contents + map them into these entities.
That looks something like:

```java
@ApplicationScoped
@RouteBase(path = "/api/v1/handler")
public class HandlerResource {

    ConcurrentHashMap<String, PolyglotFunctionBundle> bundles = new ConcurrentHashMap<>();

    @Route(path = "/:name", methods = Route.HttpMethod.POST, consumes = "multipart/form-data")
    void uploadHandler(@Param String name, RoutingContext ctx) {
        // Error-handling omitted
        FileUpload fileUpload = ctx.fileUploads().get(0);

        // Extract zip file to temporary directory
        Path tempDirectory = Files.createTempDirectory("polyglot-faas-");
        Path outDir = Paths.get(tempDirectory.toString(), name);
        try (ZipFile zipFile = new ZipFile(fileUpload.uploadedFileName())) {
            zipFile.extractAll(outDir.toString());
        }

        // Load handler metadata
        File metadataFile = outDir.resolve("metadata.json").toFile();
        PolyglotFunctionMetadata metadata = objectMapper.readValue(metadataFile, PolyglotFunctionMetadata.class);

        // Create handler bundle
        PolyglotFunctionBundle bundle = new PolyglotFunctionBundle(name, metadata, outDir);

        // Store handler bundle
        bundles.put(name, bundle);
        ctx.response().end("{\"status\":\"ok\", \"message\":\"Handler " + name + " uploaded\"}");
    }
}
```

The last thing we need is an API endpoint that allows invoking the uploaded handlers. This is where it gets a little bit trickier.

At a high level, there are two "strategies" for executing user code. One for scripting languages, and one for WASM modules.

This is due to how different the execution environments and exposed API's are for those targets. Passing values to-and-from WASM is much more difficult than with traditional Truffle guest languages.

In WASM, we use stdin and stdout as proxies for arguments. This requires compiling against WASI, otherwise these aren't accessible.

I won't paste the entirety of the underlying code here, but if you're curious check `PolyglotFunctionBundle.java` for `runWasmHandler()` and `loadRequestHandler()`:

```java
class HandlerResource {

    @Route(path = "/:name", methods = Route.HttpMethod.GET, consumes = "application/json", produces = "application/json")
    void getHandler(@Param String name, RoutingContext ctx) {
        PolyglotFunctionBundle bundle = bundles.get(name);
        // Error handling omitted to simplify code example
        if (bundle.metadata().language() == PolyglotLanguage.WASM) {
            ctx.request().body().onSuccess(body -> {
                String jsonInput = body.toString();
                try {
                    String jsonOutput = bundle.runWasmHandler(jsonInput);
                    ctx.response().end(jsonOutput);
                }
            });
        } else {
            bundle.loadRequestHandler().handle(ctx);
        }
    }
}
```

## Test code to show the rough idea

The following tests show the overall upload -> invoke flow:

```java
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
```
