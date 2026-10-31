package com.rideplatform.trip.domain.state;

import com.rideplatform.trip.domain.enums.TripStatus;

public class AcceptedState implements TripState {

    @Override
    public TripStatus getStatus() {
        return TripStatus.ACCEPTED;
    }

    @Override
    public void arrive(TripContext context) {
        context.transitionTo(new ArrivingState());
    }

    @Override
    public void cancel(TripContext context, String reason, String cancelledBy) {
        context.applyCancellation(reason, cancelledBy);
        context.transitionTo(new CancelledState());
    }
}
