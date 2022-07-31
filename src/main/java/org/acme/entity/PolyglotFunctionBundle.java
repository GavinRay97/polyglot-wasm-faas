package org.acme.entity;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public record PolyglotFunctionBundle(
        String name,
        PolyglotFunctionMetadata metadata,
        Path directory) {

    public PolyglotRequestHandler loadRequestHandler() throws IOException {
        File entrypointFile = directory.resolve(metadata.entrypointFile()).toFile();

        Context context = Context.newBuilder()
                .allowAllAccess(true)
                .currentWorkingDirectory(directory.toAbsolutePath())
                .build();

        String languageId = metadata.language().getIdentifier();
        Source source = Source.newBuilder(languageId, entrypointFile).build();

        return context.eval(source).as(PolyglotRequestHandler.class);
    }

    // Runs a WASM handler that has implemented the WASI interface by using STDIN and STDOUT as input and output with JSON data.
    public String runWasmHandler(String jsonInput) throws IOException {
        Path wasmFile = directory().resolve(metadata().entrypointFile());

        if (!Files.exists(wasmFile)) {
            throw new IllegalArgumentException("WASM file does not exist: " + wasmFile);
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ByteArrayInputStream inputStream = new ByteArrayInputStream(jsonInput.getBytes());

        Context.Builder contextBuilder = Context.newBuilder("wasm")
                .allowExperimentalOptions(true)
                .option("wasm.Builtins", "wasi_snapshot_preview1,env:emscripten")
                .out(outputStream)
                .in(inputStream)
                .err(System.err);

        byte[] binary = Files.readAllBytes(wasmFile);
        Source source = Source.newBuilder("wasm", ByteSequence.create(binary), name).build();

        try (Context context = contextBuilder.build()) {
            context.eval(source);

            Value main = context.getBindings("wasm").getMember("main");
            Value handler = main.getMember("handler");

            if (handler.isNull()) {
                throw new IllegalStateException("No exported function 'handler' found in WASM module; the list of exported functions is: " +
                                                Arrays.toString(main.getMemberKeys().toArray()));
            }

            // Results indirectly written to STDOUT stream, which is defined in enclosing Polyglot Context
            handler.executeVoid();
        }

        return outputStream.toString();
    }

}
