package com.ceremoneyconnect.bookingservice.repository;

import com.ceremonyconnect.bookingservice.model.PaymentSlip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentSlipRepository extends JpaRepository<PaymentSlip, Long> {

    Optional<PaymentSlip> findByReservationId(Long reservationId);

    List<PaymentSlip> findByVerificationStatus(PaymentSlip.VerificationStatus status);
}
