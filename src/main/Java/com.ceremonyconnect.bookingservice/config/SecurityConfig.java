package com.ceremonyconnect.bookingservice.config;

import com.ceremonyconnect.bookingservice.service.CustomUserDetailsService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * Custom entry point: returns 401 JSON instead of triggering browser Basic-Auth popup.
     * The absence of "WWW-Authenticate: Basic" header prevents the popup.
     */
    @Bean
    public AuthenticationEntryPoint jsonAuthEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                "{\"error\":\"Unauthorized\",\"message\":\"Authentication required. Please sign in.\",\"status\":401}"
            );
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .authorizeHttpRequests(auth -> auth
                // ── Public static resources (all HTML, CSS, JS, images) ──
                .requestMatchers(
                    "/", "/index.html", "/login.html", "/halls.html",
                    "/about.html", "/service.html", "/services.html",
                    "/contact.html", "/feedback.html", "/booking-now.html",
                    "/booking.html", "/customer-dashboard.html",
                    "/customer-profile.html", "/customer-payment.html",
                    "/finance.html", "/dashboard.html", "/staff.html",
                    "/style.css", "/dashboard.css", "/app.js", "/navbar.js",
                    "/customer-dashboard.js", "/dashboard.js", "/finance.js", "/staff.js",
                    "/favicon.ico", "/error"
                ).permitAll()
                .requestMatchers("/*.html", "/*.css", "/*.js").permitAll()
                .requestMatchers("/images/**", "/images/backgrounds/**", "/images/halls/**").permitAll()

                // ── Public API endpoints ──
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/halls/**", "/api/packages/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/feedback/recent").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/feedback/public").permitAll()

                // ── Profile management (all authenticated users) ──
                .requestMatchers("/api/users/profile").authenticated()

                // ── Admin temp password reset ──
                .requestMatchers("/api/users/*/temp-password").hasRole("ADMIN")

                // ── Customer & Staff Reservation endpoints ──
                .requestMatchers("/api/reservations/**", "/api/reservations").hasAnyRole(
                    "CUSTOMER", "FRONT_OFFICE_SUPERVISOR", "FINANCE_OFFICER",
                    "EVENT_COORDINATOR", "ADMIN")

                // ── Feedback: authenticated operations ──
                .requestMatchers("/api/feedback/**", "/api/feedback").hasAnyRole(
                    "CUSTOMER", "FRONT_OFFICE_SUPERVISOR", "FINANCE_OFFICER",
                    "EVENT_COORDINATOR", "CUSTOMER_RELATIONS_EXECUTIVE", "ADMIN")

                // ── Staff: front office & event coordinator ──
                .requestMatchers("/api/staff/**").hasAnyRole(
                    "FRONT_OFFICE_SUPERVISOR", "EVENT_COORDINATOR", "ADMIN")

                // ── Finance & Payment: customers upload & view summaries; finance/admin verify ──
                .requestMatchers("/api/finance/upload/**", "/api/payments/upload/**", "/api/payments/upload",
                                 "/api/payments/my-summary", "/api/payments/customer-summary",
                                 "/api/payments/my", "/api/payments/file/**")
                    .hasAnyRole("CUSTOMER", "FINANCE_OFFICER", "ADMIN")
                .requestMatchers("/api/finance/**", "/api/payments/**")
                    .hasAnyRole("FINANCE_OFFICER", "ADMIN")

                // ── Admin only ──
                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                // ── Everything else requires authentication ──
                .anyRequest().authenticated()
            )
            // Use custom JSON entry point — NO browser popup!
            .httpBasic(basic -> basic.authenticationEntryPoint(jsonAuthEntryPoint()));

        return http.build();
    }
}
