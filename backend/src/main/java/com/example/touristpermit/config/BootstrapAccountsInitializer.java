package com.example.touristpermit.config;

import com.example.touristpermit.auth.AuthService;
import com.example.touristpermit.user.Role;
import com.example.touristpermit.user.User;
import com.example.touristpermit.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates staff accounts from SEED_* environment variables when they do not exist yet.
 * There is no hardcoded default password: if the variables are unset nothing is created.
 * Admin/officer accounts cannot be created through public registration.
 */
@Component
public class BootstrapAccountsInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAccountsInitializer.class);
    private static final int MIN_STAFF_PASSWORD_LENGTH = 12;

    private final AppProperties properties;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public BootstrapAccountsInitializer(AppProperties properties, UserRepository users, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.Seed seed = properties.seed();
        if (seed == null) {
            return;
        }
        createIfMissing(seed.adminEmail(), seed.adminPassword(), Role.ADMIN);
        createIfMissing(seed.officerEmail(), seed.officerPassword(), Role.OFFICER);
    }

    private void createIfMissing(String email, String password, Role role) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return;
        }
        if (password.length() < MIN_STAFF_PASSWORD_LENGTH) {
            throw new IllegalStateException("Seed " + role + " password must be at least "
                    + MIN_STAFF_PASSWORD_LENGTH + " characters");
        }
        String normalized = AuthService.normalizeEmail(email);
        if (users.existsByEmail(normalized)) {
            return;
        }
        User user = users.save(new User(normalized, passwordEncoder.encode(password), role));
        log.info("Created bootstrap {} account userId={}", role, user.getId());
    }
}
