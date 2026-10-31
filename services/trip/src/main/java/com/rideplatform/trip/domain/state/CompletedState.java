package com.rideplatform.trip.domain.state;

import com.rideplatform.trip.domain.enums.TripStatus;

public class CompletedState implements TripState {

    @Override
    public TripStatus getStatus() {
        return TripStatus.COMPLETED;
    }
}
