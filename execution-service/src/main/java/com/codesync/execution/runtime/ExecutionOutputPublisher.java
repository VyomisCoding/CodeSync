package com.codesync.execution.runtime;

@FunctionalInterface
public interface ExecutionOutputPublisher {

    void publish(String stream, String chunk);
}
