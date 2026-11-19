package com.example.touristpermit.support;

import com.example.touristpermit.security.JwtService;
import com.example.touristpermit.tourist.TouristProfile;
import com.example.touristpermit.tourist.TouristProfileRepository;
import com.example.touristpermit.user.Role;
import com.example.touristpermit.user.User;
import com.example.touristpermit.user.UserRepository;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.UUID;

/** Creates users directly in the database and issues access tokens for them. */
@TestComponent
public class TestUsers {

    public static final String PASSWORD = "Passw0rd-for-tests";

    public record TestUser(User user, TouristProfile profile, String token) {
        public String bearer() {
            return "Bearer " + token;
        }
    }

    private final UserRepository users;
    private final TouristProfileRepository profiles;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final TransactionTemplate tx;

    public TestUsers(UserRepository users, TouristProfileRepository profiles, PasswordEncoder encoder, JwtService jwt,
                     TransactionTemplate tx) {
        this.users = users;
        this.profiles = profiles;
        this.encoder = encoder;
        this.jwt = jwt;
        this.tx = tx;
    }

    public static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
    }

    public TestUser staff(Role role) {
        User user = users.save(new User(uniqueEmail(role.name().toLowerCase()), encoder.encode(PASSWORD), role));
        return new TestUser(user, null, jwt.issueAccessToken(user));
    }

    public TestUser admin() {
        return staff(Role.ADMIN);
    }

    public TestUser officer() {
        return staff(Role.OFFICER);
    }

    /** Tourist with a complete profile (eligible to apply for permits). */
    public TestUser tourist() {
        return tx.execute(status -> {
            User user = users.save(new User(uniqueEmail("tourist"), encoder.encode(PASSWORD), Role.TOURIST));
            TouristProfile p = new TouristProfile(user, "Test", "Tourist");
            p.setPassportNumber("P" + UUID.randomUUID().toString().replace("-", "").substring(0, 9).toUpperCase());
            p.setNationality("Testland");
            p.setDateOfBirth(LocalDate.of(1990, 1, 1));
            p.setPhone("+10000000000");
            profiles.save(p);
            return new TestUser(user, p, jwt.issueAccessToken(user));
        });
    }

    /** Tourist who has only registered (profile incomplete). */
    public TestUser incompleteTourist() {
        return tx.execute(status -> {
            User user = users.save(new User(uniqueEmail("tourist"), encoder.encode(PASSWORD), Role.TOURIST));
            TouristProfile p = profiles.save(new TouristProfile(user, "New", "Tourist"));
            return new TestUser(user, p, jwt.issueAccessToken(user));
        });
    }
}
