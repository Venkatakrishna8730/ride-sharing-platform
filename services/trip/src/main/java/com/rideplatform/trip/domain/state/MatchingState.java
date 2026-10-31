package com.rideplatform.trip.domain.state;

import com.rideplatform.trip.domain.enums.TripStatus;

import java.util.UUID;

public class MatchingState implements TripState {

    @Override
    public TripStatus getStatus() {
        return TripStatus.MATCHING;
    }

    @Override
    public void accept(TripContext context, UUID driverId) {
        if (driverId == null) {
            throw new IllegalArgumentException("Driver ID is required to accept a trip");
        }
        context.assignDriver(driverId);
        context.transitionTo(new AcceptedState());
    }

    @Override
    public void cancel(TripContext context, String reason, String cancelledBy) {
        context.applyCancellation(reason, cancelledBy);
        context.transitionTo(new CancelledState());
    }
}
