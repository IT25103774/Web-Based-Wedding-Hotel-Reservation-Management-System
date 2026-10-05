package com.ceremonyconnect.bookingservice.repository;

import com.ceremonyconnect.bookingservice.model.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByCustomerId(Long customerId);

    List<Reservation> findByStatus(Reservation.Status status);

    /**
     * Conflict Detection: checks if a hall is already reserved on a given date
     * (excluding rejected/cancelled bookings).
     */
    boolean existsByHallIdAndReservationDateAndStatusNotIn(
        Long hallId,
        LocalDate date,
        List<Reservation.Status> excludedStatuses
    );

    /** All booked dates for a specific hall (for calendar rendering) */
    @Query("SELECT r.reservationDate FROM Reservation r " +
           "WHERE r.hall.id = :hallId " +
           "AND r.status NOT IN ('REJECTED', 'CANCELLED')")
    List<LocalDate> findBookedDatesByHallId(@Param("hallId") Long hallId);

    /** Revenue report: total confirmed income */
    @Query("SELECT COALESCE(SUM(r.totalCost), 0) FROM Reservation r WHERE r.status = 'CONFIRMED'")
    java.math.BigDecimal getTotalRevenue();

    /** Booking count by status */
    @Query("SELECT r.status, COUNT(r) FROM Reservation r GROUP BY r.status")
    List<Object[]> getBookingCountByStatus();
}
