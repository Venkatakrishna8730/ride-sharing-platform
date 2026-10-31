package com.rideplatform.trip.exception;

import com.rideplatform.trip.domain.enums.TripStatus;
import lombok.Getter;

@Getter
public class InvalidStateTransitionException extends RuntimeException {

    private final TripStatus currentStatus;
    private final String attemptedAction;

    public InvalidStateTransitionException(TripStatus currentStatus, String attemptedAction) {
        super(String.format("Invalid state transition: Cannot perform action '%s' when trip is in state '%s'",
                attemptedAction, currentStatus));
        this.currentStatus = currentStatus;
        this.attemptedAction = attemptedAction;
    }
}
