package com.zenarahealth.sbt.exception;

public class DependencyCycleException extends RuntimeException {
    public DependencyCycleException(String message) {
        super(message);
    }
}
