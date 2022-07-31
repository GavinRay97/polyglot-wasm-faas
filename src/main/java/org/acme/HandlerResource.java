package org.acme;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.vertx.web.Param;
import io.quarkus.vertx.web.Route;
import io.quarkus.vertx.web.RouteBase;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;
import net.lingala.zip4j.ZipFile;
import org.acme.entity.PolyglotFunctionBundle;
import org.acme.entity.PolyglotFunctionMetadata;
import org.acme.entity.PolyglotLanguage;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
@RouteBase(path = "/api/v1/handler")
public class HandlerResource {

    @Inject
    ObjectMapper objectMapper;
    ConcurrentHashMap<String, PolyglotFunctionBundle> bundles = new ConcurrentHashMap<>();

    @Route(path = "/:name", methods = Route.HttpMethod.GET, consumes = "application/json", produces = "application/json")
    void getHandler(@Param String name, RoutingContext ctx) throws IOException {
        PolyglotFunctionBundle bundle = bundles.get(name);

        if (bundle == null) {
            ctx.response().setStatusCode(500).send("{\"error\":\"Handler not found\"}");
            return;
        }


        if (bundle.metadata().language() == PolyglotLanguage.WASM) {
            ctx.request().body().onSuccess(body -> {
                String jsonInput = body.toString();
                try {
                    String jsonOutput = bundle.runWasmHandler(jsonInput);
                    ctx.response().end(jsonOutput);
                } catch (IOException e) {
                    ctx.response().setStatusCode(500).end("{\"error\":\"" + e.getMessage() + "\"}");
                }
            });
        } else {
            bundle.loadRequestHandler().handle(ctx);
        }
    }

    @Route(path = "/:name", methods = Route.HttpMethod.POST, consumes = "multipart/form-data", produces = "application/json")
    void uploadHandler(@Param String name, RoutingContext ctx) throws IOException {
        if (ctx.fileUploads().isEmpty()) {
            ctx.response().setStatusCode(500).send("{\"error\":\"No file uploaded\"}");
            return;
        }

        FileUpload fileUpload = ctx.fileUploads().get(0);
        if (fileUpload == null) {
            ctx.response().setStatusCode(500).send("{\"error\":\"No file uploaded\"}");
            return;
        }

        if (!fileUpload.fileName().endsWith(".zip")) {
            ctx.response().setStatusCode(500).send("{\"error\":\"File is not a zip file\"}");
            return;
        }

        // Extract zip file to temporary directory
        Path tempDirectory = Files.createTempDirectory("polyglot-faas-");
        Path outDir = Paths.get(tempDirectory.toString(), name);
        try (ZipFile zipFile = new ZipFile(fileUpload.uploadedFileName())) {
            zipFile.extractAll(outDir.toString());
        } catch (Exception e) {
            ctx.response().setStatusCode(500).end("{\"error\":\"" + e.getMessage() + "\"}");
            return;
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
