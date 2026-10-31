package com.rideplatform.trip.repository;

import com.rideplatform.trip.domain.entity.Trip;
import com.rideplatform.trip.domain.enums.TripStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TripRepository extends JpaRepository<Trip, UUID> {
    Page<Trip> findByRiderIdOrderByCreatedAtDesc(UUID riderId, Pageable pageable);
    Page<Trip> findByDriverIdOrderByCreatedAtDesc(UUID driverId, Pageable pageable);
    List<Trip> findByStatus(TripStatus status);
}
