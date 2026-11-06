package com.tradesim.tradesim.service;

import com.tradesim.tradesim.domain.Role;
import com.tradesim.tradesim.domain.Session;
import com.tradesim.tradesim.domain.User;
import com.tradesim.tradesim.dto.LoginRequest;
import com.tradesim.tradesim.dto.RegisterRequest;
import com.tradesim.tradesim.exception.DuplicateUsernameException;
import com.tradesim.tradesim.exception.UnauthorizedException;
import com.tradesim.tradesim.repository.SessionRepository;
import com.tradesim.tradesim.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {

    private static final String BAD_CREDENTIALS = "Invalid username or password";

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final WalletService walletService;
    private final Duration sessionTtl;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserRepository userRepository,
                       SessionRepository sessionRepository,
                       PasswordEncoder passwordEncoder,
                       WalletService walletService,
                       @Value("${tradesim.session.ttl-hours:8}") long sessionTtlHours) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.walletService = walletService;
        this.sessionTtl = Duration.ofHours(sessionTtlHours);
    }

    @Transactional
    public User register(RegisterRequest request) {
        User trader = createUser(request.username(), request.password(), Role.TRADER);
        walletService.createInitialWallet(trader.id());
        return trader;
    }

    public User createUser(String rawUsername, String rawPassword, Role role) {
        String username = rawUsername.trim();
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateUsernameException(username);
        }
        String passwordHash = passwordEncoder.encode(rawPassword);

        User newUser = new User(null, username, passwordHash, role, Instant.now());
        return userRepository.save(newUser);
    }

    /**
     * TODO 2 (Core Login Logic):
     * 1. Find user via userRepository.findByUsername(request.username()), or throw new UnauthorizedException(BAD_CREDENTIALS).
     * 2. Verify password via passwordEncoder.matches(request.password(), user.passwordHash()).
     *    If false, throw new UnauthorizedException(BAD_CREDENTIALS) (same message so attackers cannot guess usernames).
     * 3. Create Instant now = Instant.now(), then new Session(newToken(), user.id(), now, now.plus(sessionTtl)).
     * 4. Save via sessionRepository.save(session) and return session.
     */
    public Session login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new UnauthorizedException(BAD_CREDENTIALS));

        if(!passwordEncoder.matches(request.password(), user.passwordHash())){
            throw new UnauthorizedException(BAD_CREDENTIALS);
        }

        Instant now = Instant.now();
        Session s = new Session(newToken(), user.id(), now, now.plus(sessionTtl));
        sessionRepository.save(s);

        return s;
    }

    /**
     * TODO 3 (Core Token Verification Logic):
     * 1. Find session via sessionRepository.findByToken(token), or throw new UnauthorizedException("Invalid or expired session").
     * 2. Check session.isExpired(Instant.now()). If expired: call sessionRepository.deleteByToken(token)
     *    and throw new UnauthorizedException("Invalid or expired session").
     * 3. Load and return the user via userRepository.findById(session.userId()),
     *    or throw new UnauthorizedException("Invalid or expired session").
     */
    public User authenticate(String token) {
        Session s = sessionRepository.findByToken(token).orElseThrow(() -> new UnauthorizedException("Invalid or expired session"));
        if(s.isExpired(Instant.now())){
            sessionRepository.deleteByToken(token);
            throw new UnauthorizedException("Invalid or expired session");
        }
        User user = userRepository.findById(s.userId()).orElseThrow(() -> new UnauthorizedException("Invalid or expired session"));
        return user;
    }

    public void logout(String token) {
        sessionRepository.deleteByToken(token);
    }

    public User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UnauthorizedException("User no longer exists"));
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

