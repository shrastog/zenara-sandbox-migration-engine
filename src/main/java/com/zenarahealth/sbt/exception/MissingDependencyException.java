package com.zenarahealth.sbt.exception;

public class MissingDependencyException extends RuntimeException {
    public MissingDependencyException(String message) {
        super(message);
    }
}
