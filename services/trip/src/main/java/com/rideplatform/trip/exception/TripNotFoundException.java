package com.rideplatform.trip.exception;

import java.util.UUID;

public class TripNotFoundException extends RuntimeException {
    public TripNotFoundException(UUID tripId) {
        super("Trip not found with ID: " + tripId);
    }
}
