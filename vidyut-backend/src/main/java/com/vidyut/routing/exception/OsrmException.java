package com.vidyut.routing.exception;

public class OsrmException extends RuntimeException {
    private final boolean locationOutsideCoverage;

    public OsrmException(String message) {
        this(message, null, false);
    }

    public OsrmException(String message, Throwable cause) {
        this(message, cause, false);
    }

    public OsrmException(String message, Throwable cause, boolean locationOutsideCoverage) {
        super(message, cause);
        this.locationOutsideCoverage = locationOutsideCoverage;
    }

    public boolean isLocationOutsideCoverage() {
        return locationOutsideCoverage;
    }
}
