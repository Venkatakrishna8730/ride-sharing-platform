package com.rideplatform.trip.domain.entity;

import com.rideplatform.trip.domain.enums.TripStatus;
import com.rideplatform.trip.domain.valueobject.GeoLocation;
import com.rideplatform.trip.domain.valueobject.Money;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trips")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Trip {

    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "rider_id", nullable = false)
    private UUID riderId;

    @Column(name = "driver_id")
    private UUID driverId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TripStatus status = TripStatus.REQUESTED;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "latitude", column = @Column(name = "pickup_latitude", nullable = false)),
        @AttributeOverride(name = "longitude", column = @Column(name = "pickup_longitude", nullable = false))
    })
    private GeoLocation pickupLocation;

    @Column(name = "pickup_address")
    private String pickupAddress;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "latitude", column = @Column(name = "dropoff_latitude", nullable = false)),
        @AttributeOverride(name = "longitude", column = @Column(name = "dropoff_longitude", nullable = false))
    })
    private GeoLocation dropoffLocation;

    @Column(name = "dropoff_address")
    private String dropoffAddress;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "fare_estimate", nullable = false)),
        @AttributeOverride(name = "currency", column = @Column(name = "currency", nullable = false))
    })
    private Money fareEstimate;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "actual_fare")),
        @AttributeOverride(name = "currency", column = @Column(name = "currency", insertable = false, updatable = false))
    })
    private Money actualFare;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Column(name = "cancelled_by")
    private String cancelledBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.status == null) {
            this.status = TripStatus.REQUESTED;
        }
    }

    // Direct State Transition Domain Methods
    public void toMatching() {
        this.status.validateTransition(TripStatus.MATCHING);
        this.status = TripStatus.MATCHING;
    }

    public void accept(UUID driverId) {
        if (driverId == null) {
            throw new IllegalArgumentException("Driver ID is required to accept a trip");
        }
        this.status.validateTransition(TripStatus.ACCEPTED);
        this.driverId = driverId;
        this.status = TripStatus.ACCEPTED;
    }

    public void arrive() {
        this.status.validateTransition(TripStatus.ARRIVING);
        this.status = TripStatus.ARRIVING;
    }

    public void startTrip() {
        this.status.validateTransition(TripStatus.IN_PROGRESS);
        this.status = TripStatus.IN_PROGRESS;
    }

    public void complete() {
        this.status.validateTransition(TripStatus.COMPLETED);
        if (this.actualFare == null) {
            this.actualFare = this.fareEstimate;
        }
        this.status = TripStatus.COMPLETED;
    }

    public void cancel(String reason, String cancelledBy) {
        this.status.validateTransition(TripStatus.CANCELLED);
        this.cancellationReason = reason != null ? reason : "Cancelled by user";
        this.cancelledBy = cancelledBy != null ? cancelledBy : "SYSTEM";
        this.status = TripStatus.CANCELLED;
    }
}
