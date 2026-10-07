package com.controlhorario.auth;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Borra cada noche los refresh tokens caducados. Los revocados se conservan hasta que caducan:
 * así se sigue detectando la reutilización de un token ya rotado.
 */
@Component
public class RefreshTokenCleanup {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanup.class);

    private final RefreshTokenRepository refreshTokens;
    private final Clock clock;

    public RefreshTokenCleanup(RefreshTokenRepository refreshTokens, Clock clock) {
        this.refreshTokens = refreshTokens;
        this.clock = clock;
    }

    @Scheduled(cron = "0 17 4 * * *", zone = "Europe/Madrid")
    @Transactional
    public void deleteExpired() {
        int deleted = refreshTokens.deleteExpiredBefore(clock.instant());
        if (deleted > 0) {
            log.info("Borrados {} refresh tokens caducados", deleted);
        }
    }
}
