package com.ceremoneyconnect.bookingservice.repository;

import com.ceremonyconnect.bookingservice.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByReservationIdOrderBySubmittedAtDesc(Long reservationId);

    List<Payment> findByCustomerIdOrderBySubmittedAtDesc(Long customerId);

    List<Payment> findByStatusOrderBySubmittedAtDesc(Payment.Status status);

    List<Payment> findAllByOrderBySubmittedAtDesc();

    Optional<Payment> findByPaymentRef(String paymentRef);

    long countByStatus(Payment.Status status);
}
