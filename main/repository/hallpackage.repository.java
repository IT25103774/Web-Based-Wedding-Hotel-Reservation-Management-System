package com.ceremonyconnect.bookingservice.repository;

import com.ceremonyconnect.bookingservice.model.Hall;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface HallRepository extends JpaRepository<Hall, Long> {

    List<Hall> findByAvailableTrue();

    /**
     * Returns halls that have NO confirmed/approved/pending reservation on the given date.
     * Used for the real-time availability calendar.
     */
    @Query("""
        SELECT h FROM Hall h
        WHERE h.available = true
          AND h.id NOT IN (
              SELECT r.hall.id FROM Reservation r
              WHERE r.reservationDate = :date
                AND r.status NOT IN ('REJECTED', 'CANCELLED')
          )
        """)
    List<Hall> findAvailableHallsOnDate(@Param("date") LocalDate date);
}
