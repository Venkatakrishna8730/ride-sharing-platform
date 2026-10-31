package com.rideplatform.trip.domain.state;

import com.rideplatform.trip.domain.enums.TripStatus;
import com.rideplatform.trip.exception.InvalidStateTransitionException;

import java.util.UUID;

public interface TripState {

    TripStatus getStatus();

    default void toMatching(TripContext context) {
        throw new InvalidStateTransitionException(getStatus(), "MATCHING");
    }

    default void accept(TripContext context, UUID driverId) {
        throw new InvalidStateTransitionException(getStatus(), "ACCEPT");
    }

    default void arrive(TripContext context) {
        throw new InvalidStateTransitionException(getStatus(), "ARRIVE");
    }

    default void startTrip(TripContext context) {
        throw new InvalidStateTransitionException(getStatus(), "START_TRIP");
    }

    default void complete(TripContext context) {
        throw new InvalidStateTransitionException(getStatus(), "COMPLETE");
    }

    default void cancel(TripContext context, String reason, String cancelledBy) {
        throw new InvalidStateTransitionException(getStatus(), "CANCEL");
    }
}
