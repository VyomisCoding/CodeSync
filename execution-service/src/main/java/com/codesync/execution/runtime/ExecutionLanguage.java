package com.codesync.execution.runtime;

import com.codesync.execution.exception.BadRequestException;

import java.util.Arrays;
import java.util.List;

public enum ExecutionLanguage {
    JAVA("java", List.of("java", "-version")),
    PYTHON("python", List.of("python3", "--version")),
    JAVASCRIPT("javascript", List.of("nodejs", "--version")),
    C("c", List.of("gcc", "--version")),
    CPP("cpp", List.of("g++", "--version"));

    private final String apiName;
    private final List<String> versionCommand;

    ExecutionLanguage(String apiName, List<String> versionCommand) {
        this.apiName = apiName;
        this.versionCommand = versionCommand;
    }

    public String apiName() {
        return apiName;
    }

    public List<String> versionCommand() {
        return versionCommand;
    }

    public static ExecutionLanguage from(String rawValue) {
        String normalized = rawValue == null ? "" : rawValue.trim().toLowerCase();
        return Arrays.stream(values())
                .filter(language -> language.apiName.equals(normalized)
                        || (language == PYTHON && "python3".equals(normalized))
                        || (language == JAVASCRIPT && ("node".equals(normalized) || "nodejs".equals(normalized)))
                        || (language == CPP && "c++".equals(normalized)))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Unsupported language: " + rawValue));
    }

    public static List<String> supportedNames() {
        return Arrays.stream(values())
                .map(ExecutionLanguage::apiName)
                .toList();
    }
}
