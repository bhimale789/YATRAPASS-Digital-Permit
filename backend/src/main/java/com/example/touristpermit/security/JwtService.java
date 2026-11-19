package com.example.touristpermit.security;

import com.example.touristpermit.config.AppProperties;
import com.example.touristpermit.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/** Issues and validates short-lived HS256 access tokens. */
@Service
public class JwtService {

    private static final int MIN_KEY_BYTES = 32;
    private static final String PURPOSE = "purpose";
    private static final String PURPOSE_MFA = "mfa";
    private static final java.time.Duration MFA_TOKEN_TTL = java.time.Duration.ofMinutes(5);

    private final SecretKey key;
    private final AppProperties.Jwt config;
    private final Clock clock;

    public JwtService(AppProperties properties, Clock clock) {
        this.config = properties.security().jwt();
        this.clock = clock;
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(config.secret());
        } catch (RuntimeException e) {
            throw new IllegalStateException("JWT_SECRET must be Base64 encoded");
        }
        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("JWT_SECRET must decode to at least " + MIN_KEY_BYTES + " bytes");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public String issueAccessToken(User user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(config.issuer())
                .subject(String.valueOf(user.getId()))
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(config.accessTokenTtl())))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public long accessTokenTtlSeconds() {
        return config.accessTokenTtl().toSeconds();
    }

    /** Short-lived token proving the password step of a two-factor login (not an access token). */
    public String issueMfaToken(User user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(config.issuer())
                .subject(String.valueOf(user.getId()))
                .claim(PURPOSE, PURPOSE_MFA)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(MFA_TOKEN_TTL)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Returns the user id if the token is an authentic, unexpired access token from this
     * issuer. Tokens issued for another purpose (e.g. the 2FA step) are rejected.
     */
    public Optional<Long> parseUserId(String token) {
        return parse(token).filter(c -> c.get(PURPOSE) == null).map(c -> Long.valueOf(c.getSubject()));
    }

    /** Returns the user id of a valid, unexpired two-factor login token. */
    public Optional<Long> parseMfaToken(String token) {
        return parse(token).filter(c -> PURPOSE_MFA.equals(c.get(PURPOSE))).map(c -> Long.valueOf(c.getSubject()));
    }

    private Optional<Claims> parse(String token) {
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(config.issuer())
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
