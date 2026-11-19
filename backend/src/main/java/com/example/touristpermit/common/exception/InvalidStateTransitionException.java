package com.example.touristpermit.common.exception;

/** Attempted lifecycle transition that the state machine does not allow (HTTP 409). */
public class InvalidStateTransitionException extends ConflictException {

    public InvalidStateTransitionException(String entity, Enum<?> from, Enum<?> to) {
        super("%s cannot transition from %s to %s".formatted(entity, from, to));
    }
}
