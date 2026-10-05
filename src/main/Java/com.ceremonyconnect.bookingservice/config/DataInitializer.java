package com.ceremonyconnect.bookingservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Seeds the database with sample data on first run.
 * Idempotent – skips if data already exists.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final HallRepository hallRepository;
    private final WeddingPackageRepository packageRepository;
    private final ReservationRepository reservationRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        seedUsers();
        seedHalls();
        seedPackages();
        seedSampleBookingsAndPayments();
    }

    private void seedUsers() {
        if (userRepository.count() == 0) {
            log.info("Seeding default users…");
            save("Admin User",                 "admin@ceremony.com",      "admin123",    "0100000001", User.Role.ADMIN);
            save("Front Office Alice",         "frontoffice@ceremony.com","office123",   "0100000002", User.Role.FRONT_OFFICE_SUPERVISOR);
            save("Finance Bob",                "finance@ceremony.com",    "finance123",  "0100000003", User.Role.FINANCE_OFFICER);
            save("Coordinator Eve",            "coordinator@ceremony.com","coord123",    "0100000004", User.Role.EVENT_COORDINATOR);
            save("Feedback Executive",         "feedback@ceremony.com",   "feedback123", "0100000006", User.Role.CUSTOMER_RELATIONS_EXECUTIVE);
            save("Kasun Perera",               "customer@gmail.com",      "cust123",     "0771234567", User.Role.CUSTOMER);
        } else {
            // Automatically upgrade any placeholder hashes inserted by CeremonyConnectDB.sql
            updatePasswordIfPlaceholder("admin@ceremony.com", "admin123");
            updatePasswordIfPlaceholder("frontoffice@ceremony.com", "office123");
            updatePasswordIfPlaceholder("finance@ceremony.com", "finance123");
            updatePasswordIfPlaceholder("coordinator@ceremony.com", "coord123");
            updatePasswordIfPlaceholder("customer@gmail.com", "cust123");

            // Ensure feedback staff account exists
            if (userRepository.findByEmail("feedback@ceremony.com").isEmpty()) {
                save("Feedback Executive", "feedback@ceremony.com", "feedback123", "0100000006", User.Role.CUSTOMER_RELATIONS_EXECUTIVE);
            }
        }
    }

    private void updatePasswordIfPlaceholder(String email, String rawPassword) {
        userRepository.findByEmail(email).ifPresent(u -> {
            if (u.getPassword() != null && u.getPassword().contains("PLACEHOLDER")) {
                u.setPassword(passwordEncoder.encode(rawPassword));
                userRepository.save(u);
                log.info("Updated password hash for {}", email);
            }
        });
    }

    private void save(String name, String email, String pwd, String phone, User.Role role) {
        User u = new User();
        u.setFullName(name);
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode(pwd));
        u.setPhone(phone);
        u.setRole(role);
        userRepository.save(u);
    }

    private void seedHalls() {
        if (hallRepository.count() > 0) return;
        log.info("Seeding halls…");

        hall("Grand Ballroom",   "Our flagship 1,200 sq/m ballroom with crystal chandeliers.",
             1000, new BigDecimal("5000.00"), "Level 3, Main Wing",
             "Crystal chandeliers, Built-in stage, Bridal suite, Valet parking");

        hall("Garden Terrace",   "Open-air venue surrounded by tropical gardens.",
             350, new BigDecimal("2500.00"), "Outdoor, Ground Floor",
             "Natural greenery, Fairy-light canopy, Sunset views, Outdoor bar");

        hall("Azure Hall",       "Intimate modern hall with floor-to-ceiling windows.",
             200, new BigDecimal("1800.00"), "Level 2, East Wing",
             "Natural daylight, Built-in projector, Climate-controlled");

        hall("Royal Suite",      "Luxurious private suite for exclusive events.",
             80,  new BigDecimal("3200.00"), "Level 5, Tower A",
             "Butler service, Private terrace, Premium furniture");
    }

    private void hall(String name, String desc, int cap, BigDecimal price,
                      String loc, String amenities) {
        Hall h = new Hall();
        h.setName(name); h.setDescription(desc); h.setCapacity(cap);
        h.setPricePerDay(price); h.setLocation(loc); h.setAmenities(amenities);
        hallRepository.save(h);
    }

    private void seedPackages() {
        if (packageRepository.count() > 0) return;
        log.info("Seeding packages…");

        pkg("Silver Dream",
            "Elegant starter package for intimate gatherings.",
            new BigDecimal("3000.00"),
            new BigDecimal("35.00"),
            new BigDecimal("800.00"),
            "Flower arch, Basic lighting, Standard table setup");

        pkg("Golden Bliss",
            "Our most popular all-inclusive wedding package.",
            new BigDecimal("6500.00"),
            new BigDecimal("65.00"),
            new BigDecimal("1800.00"),
            "Premium floral arrangements, Custom lighting, Cocktail hour, Wedding cake");

        pkg("Platinum Royale",
            "The ultimate luxury experience — no detail is spared.",
            new BigDecimal("12000.00"),
            new BigDecimal("120.00"),
            new BigDecimal("3500.00"),
            "Full 7-course dinner, Live band, Photo booth, Fireworks, Honeymoon night stay");
    }

    private void pkg(String name, String desc, BigDecimal base,
                     BigDecimal catering, BigDecimal deco, String inclusions) {
        WeddingPackage p = new WeddingPackage();
        p.setName(name); p.setDescription(desc); p.setBasePrice(base);
        p.setCateringPricePerHead(catering); p.setDecorationPrice(deco);
        p.setInclusions(inclusions);
        packageRepository.save(p);
    }

    private void seedSampleBookingsAndPayments() {
        try {
            if (paymentRepository.count() > 0) return;

            User customer = userRepository.findByEmail("customer@gmail.com").orElse(null);
            User financeOfficer = userRepository.findByEmail("finance@ceremony.com").orElse(null);
            Hall hall = hallRepository.findAll().stream().findFirst().orElse(null);
            WeddingPackage pkg = packageRepository.findAll().stream().findFirst().orElse(null);

            if (customer == null || hall == null || pkg == null) return;

            // Ensure customer has sample reservation
            Reservation res = reservationRepository.findByCustomerId(customer.getId())
                .stream().findFirst().orElse(null);

            if (res == null) {
                res = new Reservation();
                res.setCustomer(customer);
                res.setHall(hall);
                res.setWeddingPackage(pkg);
                res.setReservationDate(LocalDate.now().plusMonths(2));
                res.setGuestCount(250);
                res.setCateringRequested(true);
                res.setDecorationRequested(true);
                res.setTotalCost(new BigDecimal("650000.00"));
                res.setStatus(Reservation.Status.APPROVED);
                res = reservationRepository.save(res);
            }

            // Seed sample payments matching the user's screenshots
            createPayment("CC-PAY-2026-001", res, customer, new BigDecimal("250000.00"),
                Payment.PaymentType.ADVANCE, "Commercial Bank", "BOC-TXN-984210",
                Payment.Status.VERIFIED, null, financeOfficer, LocalDateTime.now().minusDays(15));

            createPayment("CC-PAY-2026-003", res, customer, new BigDecimal("600000.00"),
                Payment.PaymentType.ADVANCE, "Commercial Bank", "TXN-882103",
                Payment.Status.VERIFIED, null, financeOfficer, LocalDateTime.now().minusDays(10));

            createPayment("CC-PAY-2026-004", res, customer, new BigDecimal("450000.00"),
                Payment.PaymentType.ADVANCE, "Commercial Bank", "TXN-910482",
                Payment.Status.REJECTED, "Deposit slip illegible. Please re-upload clear photo.", financeOfficer, LocalDateTime.now().minusDays(5));

            createPayment("CC-PAY-2026-005", res, customer, new BigDecimal("450000.00"),
                Payment.PaymentType.ADVANCE, "Commercial Bank", "TXN-910499",
                Payment.Status.VERIFIED, "Verified after re-upload", financeOfficer, LocalDateTime.now().minusDays(2));

            log.info("Seeded sample payments for Customer & Finance dashboards.");
        } catch (Exception e) {
            log.warn("Sample payment seed skipped: {}", e.getMessage());
        }
    }

    private void createPayment(String ref, Reservation res, User customer, BigDecimal amount,
                               Payment.PaymentType type, String bank, String txn,
                               Payment.Status status, String rejReason, User officer, LocalDateTime time) {
        Payment p = new Payment();
        p.setPaymentRef(ref);
        p.setReservation(res);
        p.setCustomer(customer);
        p.setAmount(amount);
        p.setPaymentType(type);
        p.setBankName(bank);
        p.setTransactionReference(txn);
        p.setStatus(status);
        p.setRejectionReason(rejReason);
        p.setVerifiedBy(officer);
        p.setSubmittedAt(time);
        p.setVerifiedAt(status == Payment.Status.VERIFIED ? time.plusHours(2) : null);
        paymentRepository.save(p);
    }
}
