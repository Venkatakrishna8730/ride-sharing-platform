package com.rideplatform.trip.domain.entity;

import com.rideplatform.trip.domain.enums.TripStatus;
import com.rideplatform.trip.domain.state.TripContext;
import com.rideplatform.trip.domain.state.TripState;
import com.rideplatform.trip.domain.state.TripStateFactory;
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
public class Trip implements TripContext {

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

    // State Pattern Domain Methods
    public void toMatching() {
        TripState state = TripStateFactory.getState(this.status);
        state.toMatching(this);
    }

    public void accept(UUID driverId) {
        TripState state = TripStateFactory.getState(this.status);
        state.accept(this, driverId);
    }

    public void arrive() {
        TripState state = TripStateFactory.getState(this.status);
        state.arrive(this);
    }

    public void startTrip() {
        TripState state = TripStateFactory.getState(this.status);
        state.startTrip(this);
    }

    public void complete() {
        TripState state = TripStateFactory.getState(this.status);
        state.complete(this);
    }

    public void cancel(String reason, String cancelledBy) {
        TripState state = TripStateFactory.getState(this.status);
        state.cancel(this, reason, cancelledBy);
    }

    // TripContext callbacks
    @Override
    public void transitionTo(TripState nextState) {
        this.status = nextState.getStatus();
    }

    @Override
    public void assignDriver(UUID driverId) {
        this.driverId = driverId;
    }

    @Override
    public void applyCancellation(String reason, String cancelledBy) {
        this.cancellationReason = reason;
        this.cancelledBy = cancelledBy;
    }
}
