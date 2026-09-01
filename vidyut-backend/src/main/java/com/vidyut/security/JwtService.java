package com.vidyut.security;

import com.vidyut.account.entity.AccessMode;
import com.vidyut.account.entity.Account;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpiration;

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Long extractAccountId(String token) {
        return extractClaim(token, claims -> claims.get("accountId", Long.class));
    }

    public AccessMode extractMode(String token) {
        return AccessMode.valueOf(extractClaim(token, claims -> claims.get("mode", String.class)));
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateModeToken(Account account, AccessMode mode) {
        if (!account.allows(mode)) {
            throw new IllegalArgumentException("Cannot issue a token for an unauthorized mode");
        }
        Map<String, Object> claims = Map.of(
                "accountId", account.getId(),
                "mode", mode.name(),
                "role", mode.role().name()
        );
        return Jwts.builder()
                .claims(claims)
                .id(UUID.randomUUID().toString())
                .subject(account.getEmail())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSignInKey())
                .compact();
    }

    public boolean isTokenValid(String token, Account account) {
        final String username = extractUsername(token);
        final Long accountId = extractAccountId(token);
        final AccessMode mode = extractMode(token);
        return username.equalsIgnoreCase(account.getEmail())
                && accountId.equals(account.getId())
                && account.isEnabled()
                && account.allows(mode)
                && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
