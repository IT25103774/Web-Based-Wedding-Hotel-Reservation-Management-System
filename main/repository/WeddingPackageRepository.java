package com.ceremonyconnect.bookingservice.repository;

import com.ceremonyconnect.bookingservice.model.WeddingPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WeddingPackageRepository extends JpaRepository<WeddingPackage, Long> {

    List<WeddingPackage> findByActiveTrue();
}
