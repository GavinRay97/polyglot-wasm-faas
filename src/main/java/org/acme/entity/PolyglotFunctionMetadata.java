package org.acme.entity;


public record PolyglotFunctionMetadata(
        PolyglotLanguage language,
        String entrypointFile) {
}
