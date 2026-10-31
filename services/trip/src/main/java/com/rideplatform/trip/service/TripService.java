package com.rideplatform.trip.service;

import com.rideplatform.trip.dto.CreateTripRequest;
import com.rideplatform.trip.dto.PageResponse;
import com.rideplatform.trip.dto.TripResponse;
import com.rideplatform.trip.dto.UpdateTripStatusRequest;

import java.util.UUID;

public interface TripService {
    TripResponse createTrip(UUID riderId, CreateTripRequest request);
    TripResponse getTripById(UUID tripId);
    TripResponse updateTripStatus(UUID tripId, UpdateTripStatusRequest request, String callerRole, String callerId);
    PageResponse<TripResponse> getTripsByRider(UUID riderId, int page, int size);
    PageResponse<TripResponse> getTripsByDriver(UUID driverId, int page, int size);
}
