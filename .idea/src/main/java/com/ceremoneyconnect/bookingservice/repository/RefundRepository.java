package com.ceremoneyconnect.bookingservice.repository;

import com.ceremonyconnect.bookingservice.model.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {

    List<Refund> findByStatusOrderByRequestedAtDesc(Refund.Status status);

    List<Refund> findAllByOrderByRequestedAtDesc();

    List<Refund> findByReservationId(Long reservationId);
}
