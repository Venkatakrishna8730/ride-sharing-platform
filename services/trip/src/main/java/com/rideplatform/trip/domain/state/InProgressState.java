package com.rideplatform.trip.domain.state;

import com.rideplatform.trip.domain.enums.TripStatus;

public class InProgressState implements TripState {

    @Override
    public TripStatus getStatus() {
        return TripStatus.IN_PROGRESS;
    }

    @Override
    public void complete(TripContext context) {
        context.transitionTo(new CompletedState());
    }
}
