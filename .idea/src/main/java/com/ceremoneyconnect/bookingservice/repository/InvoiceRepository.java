package com.ceremoneyconnect.bookingservice.repository;

import com.ceremonyconnect.bookingservice.model.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByReservationId(Long reservationId);

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);
}
