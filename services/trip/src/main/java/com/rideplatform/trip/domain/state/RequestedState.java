package com.rideplatform.trip.domain.state;

import com.rideplatform.trip.domain.enums.TripStatus;

public class RequestedState implements TripState {

    @Override
    public TripStatus getStatus() {
        return TripStatus.REQUESTED;
    }

    @Override
    public void toMatching(TripContext context) {
        context.transitionTo(new MatchingState());
    }

    @Override
    public void cancel(TripContext context, String reason, String cancelledBy) {
        context.applyCancellation(reason, cancelledBy);
        context.transitionTo(new CancelledState());
    }
}
