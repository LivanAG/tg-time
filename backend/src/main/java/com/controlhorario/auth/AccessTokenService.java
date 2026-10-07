package com.controlhorario.auth;

import java.time.Instant;
import java.util.UUID;

import com.controlhorario.common.config.AppProperties;
import com.controlhorario.common.security.JwtConfig;
import com.controlhorario.user.User;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Emite access tokens JWT HS256: sub (id de usuario), role, iat, exp (= iat + 15 min) y jti aleatorio. */
@Service
public class AccessTokenService {

    private final JwtEncoder encoder;
    private final AppProperties properties;

    public AccessTokenService(JwtEncoder encoder, AppProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    public String issue(User user, Instant issuedAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getId().toString())
                .claim(JwtConfig.ROLE_CLAIM, user.getRole().name())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(properties.accessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** Segundos de vida del access token ({@code expiresIn} de la respuesta). */
    public long expiresInSeconds() {
        return properties.accessTokenTtl().toSeconds();
    }
}
