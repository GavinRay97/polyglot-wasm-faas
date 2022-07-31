package org.acme.entity;

import com.fasterxml.jackson.annotation.JsonValue;

public enum PolyglotLanguage {

    JAVASCRIPT("js"),
    PYTHON("python"),
    RUBY("ruby"),
    WASM("wasm");

    @JsonValue
    private final String identifier;

    private PolyglotLanguage(String identifier) {
        this.identifier = identifier;
    }

    public String getIdentifier() {
        return identifier;
    }
}
