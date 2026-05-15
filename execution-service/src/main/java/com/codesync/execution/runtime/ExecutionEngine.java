package com.codesync.execution.runtime;

import com.codesync.execution.entity.ExecutionJob;

import java.util.List;
import java.util.UUID;

public interface ExecutionEngine {

    ExecutionOutcome execute(ExecutionJob job, ExecutionOutputPublisher outputPublisher);

    boolean cancel(UUID jobId);

    List<String> getSupportedLanguages();

    String getLanguageVersion(String language);
}
