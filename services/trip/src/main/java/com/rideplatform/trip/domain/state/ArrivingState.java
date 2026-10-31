package com.rideplatform.trip.domain.state;

import com.rideplatform.trip.domain.enums.TripStatus;

public class ArrivingState implements TripState {

    @Override
    public TripStatus getStatus() {
        return TripStatus.ARRIVING;
    }

    @Override
    public void startTrip(TripContext context) {
        context.transitionTo(new InProgressState());
    }

    @Override
    public void cancel(TripContext context, String reason, String cancelledBy) {
        context.applyCancellation(reason, cancelledBy);
        context.transitionTo(new CancelledState());
    }
}
