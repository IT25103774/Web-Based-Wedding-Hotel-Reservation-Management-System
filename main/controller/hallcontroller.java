package com.ceremonyconnect.bookingservice.controller;

import com.ceremonyconnect.bookingservice.exception.ResourceNotFoundException;
import com.ceremonyconnect.bookingservice.model.Hall;
import com.ceremonyconnect.bookingservice.repository.HallRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/halls")
@RequiredArgsConstructor
public class HallController {

    private final HallRepository hallRepository;

    /** GET /api/halls — list all halls */
    @GetMapping
    public List<Hall> getAll() {
        return hallRepository.findAll();
    }

    /** GET /api/halls/{id} */
    @GetMapping("/{id}")
    public Hall getById(@PathVariable Long id) {
        return hallRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Hall not found: " + id));
    }

    /** GET /api/halls/available?date=2025-12-25 — availability calendar */
    @GetMapping("/available")
    public List<Hall> getAvailableOnDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return hallRepository.findAvailableHallsOnDate(date);
    }

    /** POST /api/halls — admin only */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Hall> create(@Valid @RequestBody Hall hall) {
        return ResponseEntity.status(HttpStatus.CREATED).body(hallRepository.save(hall));
    }

    /** PUT /api/halls/{id} — admin only */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Hall update(@PathVariable Long id, @Valid @RequestBody Hall updated) {
        Hall existing = hallRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Hall not found: " + id));
        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setCapacity(updated.getCapacity());
        existing.setPricePerDay(updated.getPricePerDay());
        existing.setLocation(updated.getLocation());
        existing.setAmenities(updated.getAmenities());
        existing.setAvailable(updated.isAvailable());
        return hallRepository.save(existing);
    }

    /** DELETE /api/halls/{id} — admin only */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        hallRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
