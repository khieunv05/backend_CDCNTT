package com.example.english_app_cdcntt.service.tx;

import com.example.english_app_cdcntt.config.JwtService;
import com.example.english_app_cdcntt.config.TokenType;
import com.example.english_app_cdcntt.dto.AuthResponse;
import com.example.english_app_cdcntt.entity.RefreshToken;
import com.example.english_app_cdcntt.entity.User;
import com.example.english_app_cdcntt.repository.RefreshTokenRepository;
import io.jsonwebtoken.JwtException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every refresh-token write of §5.2, one transaction per public method (bean named exactly as
 * the plan requires). Rotation takes {@code PESSIMISTIC_WRITE} on the presented token's row, so
 * two concurrent refreshes of the same token serialize: the winner rotates, the loser re-reads
 * a deleted row and ends INVALID → 401 (§5.2:243). Results are returned, never thrown, so the
 * EXPIRED deletion commits; the coordinating service throws only after this transaction ended.
 */
@Service
public class RefreshTokenTxService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final Clock clock;

    public RefreshTokenTxService(RefreshTokenRepository refreshTokenRepository, JwtService jwtService, Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.clock = clock;
    }

    /** Login's issue-and-store step (§5.2:235): the caller sees success only after commit. */
    @Transactional
    public AuthResponse issueSession(User user) {
        return issue(user);
    }

    /**
     * §5.2:238–242. Locked read → expiry decision (delete + EXPIRED, committed) → full
     * consistency check of the presented JWT against the stored row (signature, type=refresh,
     * sub/uid equal the row's user, JWT exp equals DB expiry) → rotate in the same transaction.
     */
    @Transactional
    public RefreshRotationResult attemptRotation(String presentedToken) {
        Optional<RefreshToken> row = refreshTokenRepository.findByTokenForUpdate(presentedToken);
        if (row.isEmpty()) {
            return new RefreshRotationResult.Invalid();
        }
        RefreshToken stored = row.get();
        if (stored.isExpired(Instant.now(clock))) {
            // Returned normally → the transaction commits the deletion before the 401 is thrown.
            refreshTokenRepository.delete(stored);
            return new RefreshRotationResult.Expired();
        }
        User user = stored.getUser();
        JwtService.JwtPayload payload;
        try {
            payload = jwtService.parse(presentedToken);
        } catch (JwtException e) {
            return new RefreshRotationResult.Invalid();
        }
        if (payload.type() != TokenType.REFRESH
                || !payload.username().equals(user.getUsername())
                || !payload.userId().equals(user.getId())
                || !payload.expiresAt().equals(stored.getExpiryDate())) {
            // Live row but tampered/stale presentation: no tokens issued, row untouched (§5.2:241).
            return new RefreshRotationResult.Invalid();
        }
        refreshTokenRepository.delete(stored);
        return new RefreshRotationResult.Rotated(issue(user));
    }

    /**
     * §5.2:237 (logout): delete the row with this exact token string inside a transaction;
     * a token that is no longer in the DB deletes nothing and still succeeds.
     */
    @Transactional
    public void revoke(String presentedToken) {
        refreshTokenRepository.deleteByTokenValue(presentedToken);
    }

    // Runs inside the caller's transaction — deliberately not proxied, §5.2:242 requires the
    // new refresh row to be stored in the SAME transaction that deleted the old one.
    private AuthResponse issue(User user) {
        JwtService.IssuedToken access = jwtService.issue(TokenType.ACCESS, user.getId(), user.getUsername());
        JwtService.IssuedToken refresh = jwtService.issue(TokenType.REFRESH, user.getId(), user.getUsername());
        refreshTokenRepository.save(RefreshToken.create(user, refresh.token(), refresh.expiresAt()));
        return new AuthResponse(access.token(), refresh.token(), jwtService.accessTokenExpiresInSeconds());
    }
}
