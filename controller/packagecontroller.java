package com.ceremonyconnect.bookingservice.controller;

import com.ceremonyconnect.bookingservice.exception.ResourceNotFoundException;
import com.ceremonyconnect.bookingservice.model.WeddingPackage;
import com.ceremonyconnect.bookingservice.repository.WeddingPackageRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/packages")
@RequiredArgsConstructor
public class PackageController {

    private final WeddingPackageRepository packageRepository;

    /** GET /api/packages */
    @GetMapping
    public List<WeddingPackage> getAll() {
        return packageRepository.findByActiveTrue();
    }

    /** GET /api/packages/{id} */
    @GetMapping("/{id}")
    public WeddingPackage getById(@PathVariable Long id) {
        return packageRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Package not found: " + id));
    }

    /** POST /api/packages — admin or event coordinator */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'EVENT_COORDINATOR')")
    public ResponseEntity<WeddingPackage> create(@Valid @RequestBody WeddingPackage pkg) {
        return ResponseEntity.status(HttpStatus.CREATED).body(packageRepository.save(pkg));
    }

    /** PUT /api/packages/{id} */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EVENT_COORDINATOR')")
    public WeddingPackage update(@PathVariable Long id, @Valid @RequestBody WeddingPackage updated) {
        WeddingPackage existing = packageRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Package not found: " + id));
        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setBasePrice(updated.getBasePrice());
        existing.setCateringPricePerHead(updated.getCateringPricePerHead());
        existing.setDecorationPrice(updated.getDecorationPrice());
        existing.setInclusions(updated.getInclusions());
        existing.setActive(updated.isActive());
        return packageRepository.save(existing);
    }
}
