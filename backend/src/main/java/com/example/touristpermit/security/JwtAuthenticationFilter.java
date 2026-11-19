package com.example.touristpermit.security;

import com.example.touristpermit.common.response.ErrorResponseWriter;
import com.example.touristpermit.user.UserRepository;
import org.springframework.http.HttpStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates requests bearing a valid access token. The user is re-loaded from the
 * database so that disabled accounts and role changes take effect immediately and the
 * role is never taken from the token or client.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ErrorResponseWriter errorWriter;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository, ErrorResponseWriter errorWriter) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.errorWriter = errorWriter;
    }

    private static boolean allowedBeforePasswordChange(String path) {
        return path.equals("/api/v1/account") || path.equals("/api/v1/account/password")
                || path.startsWith("/api/v1/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            var authenticated = jwtService.parseUserId(header.substring(BEARER.length()).trim())
                    .flatMap(userRepository::findById)
                    .filter(u -> u.isEnabled());
            if (authenticated.isPresent() && authenticated.get().isMustChangePassword()
                    && !allowedBeforePasswordChange(request.getRequestURI())) {
                // Temporary passwords (set by an administrator) only allow changing the password.
                errorWriter.write(request, response, HttpStatus.FORBIDDEN, "You must change your password before continuing");
                return;
            }
            authenticated.ifPresent(user -> {
                        AuthenticatedUser principal = new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole());
                        var auth = new UsernamePasswordAuthenticationToken(principal, null,
                                List.of(new SimpleGrantedAuthority(user.getRole().authority())));
                        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    });
        }
        chain.doFilter(request, response);
    }
}
