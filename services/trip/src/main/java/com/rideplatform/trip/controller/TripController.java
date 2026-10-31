package com.rideplatform.trip.controller;

import com.rideplatform.trip.dto.CreateTripRequest;
import com.rideplatform.trip.dto.PageResponse;
import com.rideplatform.trip.dto.TripResponse;
import com.rideplatform.trip.dto.UpdateTripStatusRequest;
import com.rideplatform.trip.service.TripService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    @PostMapping
    public ResponseEntity<TripResponse> createTrip(
            @RequestHeader(value = "X-User-Id", required = true) String userIdHeader,
            @Valid @RequestBody CreateTripRequest request) {
        UUID riderId = UUID.fromString(userIdHeader);
        TripResponse response = tripService.createTrip(riderId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TripResponse> getTripById(@PathVariable UUID id) {
        TripResponse response = tripService.getTripById(id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<TripResponse> updateTripStatus(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Role", required = false, defaultValue = "RIDER") String callerRole,
            @RequestHeader(value = "X-User-Id", required = false) String callerId,
            @Valid @RequestBody UpdateTripStatusRequest request) {
        TripResponse response = tripService.updateTripStatus(id, request, callerRole, callerId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/rider")
    public ResponseEntity<PageResponse<TripResponse>> getRiderTrips(
            @RequestHeader(value = "X-User-Id", required = true) String userIdHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        UUID riderId = UUID.fromString(userIdHeader);
        PageResponse<TripResponse> response = tripService.getTripsByRider(riderId, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/driver")
    public ResponseEntity<PageResponse<TripResponse>> getDriverTrips(
            @RequestHeader(value = "X-User-Id", required = true) String userIdHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        UUID driverId = UUID.fromString(userIdHeader);
        PageResponse<TripResponse> response = tripService.getTripsByDriver(driverId, page, size);
        return ResponseEntity.ok(response);
    }
}
