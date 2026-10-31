package com.rideplatform.trip.service.impl;

import com.rideplatform.trip.domain.entity.Trip;
import com.rideplatform.trip.domain.enums.TripStatus;
import com.rideplatform.trip.domain.valueobject.GeoLocation;
import com.rideplatform.trip.domain.valueobject.Money;
import com.rideplatform.trip.dto.CreateTripRequest;
import com.rideplatform.trip.dto.PageResponse;
import com.rideplatform.trip.dto.TripResponse;
import com.rideplatform.trip.dto.UpdateTripStatusRequest;
import com.rideplatform.trip.exception.TripNotFoundException;
import com.rideplatform.trip.repository.TripRepository;
import com.rideplatform.trip.service.TripService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripServiceImpl implements TripService {

    private static final BigDecimal BASE_FARE = BigDecimal.valueOf(2.50);
    private static final BigDecimal PER_KM_RATE = BigDecimal.valueOf(1.75);

    private final TripRepository tripRepository;

    @Override
    @Transactional
    public TripResponse createTrip(UUID riderId, CreateTripRequest request) {
        log.info("Creating new trip for rider ID: {}", riderId);

        GeoLocation pickup = new GeoLocation(request.getPickupLatitude(), request.getPickupLongitude());
        GeoLocation dropoff = new GeoLocation(request.getDropoffLatitude(), request.getDropoffLongitude());

        double distanceKm = pickup.distanceTo(dropoff);
        log.debug("Calculated trip distance: {} km", distanceKm);

        BigDecimal fareAmount = request.getFareEstimate();
        if (fareAmount == null || fareAmount.compareTo(BigDecimal.ZERO) <= 0) {
            fareAmount = BASE_FARE.add(PER_KM_RATE.multiply(BigDecimal.valueOf(distanceKm)))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        Money fareEstimate = new Money(fareAmount, request.getCurrency());

        Trip trip = Trip.builder()
                .riderId(riderId)
                .status(TripStatus.REQUESTED)
                .pickupLocation(pickup)
                .pickupAddress(request.getPickupAddress())
                .dropoffLocation(dropoff)
                .dropoffAddress(request.getDropoffAddress())
                .fareEstimate(fareEstimate)
                .build();

        Trip savedTrip = tripRepository.save(trip);
        log.info("Created trip with ID: {} in status: {}", savedTrip.getId(), savedTrip.getStatus());

        return TripResponse.fromEntity(savedTrip);
    }

    @Override
    @Transactional(readOnly = true)
    public TripResponse getTripById(UUID tripId) {
        log.debug("Fetching trip with ID: {}", tripId);
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException(tripId));
        return TripResponse.fromEntity(trip);
    }

    @Override
    @Transactional
    public TripResponse updateTripStatus(UUID tripId, UpdateTripStatusRequest request, String callerRole, String callerId) {
        log.info("Updating trip ID: {} to status: {} by caller role: {}", tripId, request.getTargetStatus(), callerRole);

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException(tripId));

        TripStatus target = request.getTargetStatus();

        switch (target) {
            case MATCHING -> trip.toMatching();
            case ACCEPTED -> trip.accept(request.getDriverId());
            case ARRIVING -> trip.arrive();
            case IN_PROGRESS -> trip.startTrip();
            case COMPLETED -> {
                trip.complete();
                // Set actual fare to fare estimate upon completion if not already set
                if (trip.getActualFare() == null) {
                    trip.setActualFare(trip.getFareEstimate());
                }
            }
            case CANCELLED -> {
                String reason = request.getCancellationReason() != null ? request.getCancellationReason() : "Cancelled by user";
                String cancelledBy = callerRole != null ? callerRole : "SYSTEM";
                trip.cancel(reason, cancelledBy);
            }
            default -> throw new IllegalArgumentException("Unsupported target status: " + target);
        }

        Trip updatedTrip = tripRepository.save(trip);
        log.info("Successfully updated trip ID: {} to status: {}", updatedTrip.getId(), updatedTrip.getStatus());

        return TripResponse.fromEntity(updatedTrip);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TripResponse> getTripsByRider(UUID riderId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Trip> tripPage = tripRepository.findByRiderIdOrderByCreatedAtDesc(riderId, pageRequest);
        return PageResponse.fromPage(tripPage.map(TripResponse::fromEntity));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TripResponse> getTripsByDriver(UUID driverId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Trip> tripPage = tripRepository.findByDriverIdOrderByCreatedAtDesc(driverId, pageRequest);
        return PageResponse.fromPage(tripPage.map(TripResponse::fromEntity));
    }
}
