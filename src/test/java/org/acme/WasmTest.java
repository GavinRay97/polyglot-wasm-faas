package org.acme;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;


class WasmTest {

    @Test
    void test() throws IOException {
        String wasmBundlePath = "src/test/resources/bundles/rust-wasm/hello_wasm.wasm";
        byte[] binary = Files.readAllBytes(Paths.get(wasmBundlePath));

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ByteArrayInputStream inputStream = new ByteArrayInputStream("{\"name\":\"John\"}".getBytes());

        Context.Builder contextBuilder = Context.newBuilder("wasm")
                .allowExperimentalOptions(true)
                .option("wasm.Builtins", "wasi_snapshot_preview1,env:emscripten")
                .out(outputStream)
                .in(inputStream)
                .err(System.err);

        Source.Builder sourceBuilder = Source.newBuilder("wasm", ByteSequence.create(binary), "example");
        Source source = sourceBuilder.build();

        try (Context context = contextBuilder.build()) {
            context.eval(source);

            Value main = context.getBindings("wasm").getMember("main");
            Value handler = main.getMember("handler");
            if (handler.isNull()) {
                throw new RuntimeException("WASM handler is null, list of exported functions: " + main.getMemberKeys());
            }

            handler.executeVoid();
            System.out.println("Output from WASM stdout: " + outputStream);
        }
    }

}
