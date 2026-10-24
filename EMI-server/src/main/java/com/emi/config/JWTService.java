package com.emi.config;

import com.emi.config.properties.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class JWTService {


    private SecretKey secretKey;
    private final JwtProperties jwtProperties;

    public JWTService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @PostConstruct
    private void init() {
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public boolean isValid(String token) {
        try {
            validateToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Date getExpirationDate(String token) {
        try {
            return getAllClaims(token).getExpiration();
        } catch (ExpiredJwtException e) {
            return e.getClaims().getExpiration();
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isExpired(String token) {
        return getAllClaims(token)
                .getExpiration()
                .before(new Date());
    }

    public boolean hasRole(String token, String role) {
        return role.equals(
                getClaim(token, "role", String.class));
    }

    public String getRole(String token) {
        return  getClaim(token, "role", String.class);
    }

    public String extractAccessToken(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        // 1. Try Authorization header (Standard Bearer token or direct token)
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null) {
            if (authHeader.startsWith("Bearer ")) {
                return authHeader.substring(7).trim();
            }
            if (!authHeader.trim().isEmpty()) {
                return authHeader.trim();
            }
        }

        // 3. Try cookies (AuthToken, token, jwt)
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            String[] cookieNames = { "AuthToken", "token", "jwt","AccessToken"};
            for (String name : cookieNames) {
                for (Cookie cookie : cookies) {
                    if (name.equals(cookie.getName()) && cookie.getValue() != null
                            && !cookie.getValue().trim().isEmpty()) {
                        return cookie.getValue().trim();
                    }
                }
            }
        }

        return null;
    }

    public String extractToken(HttpServletRequest request,String token) {
        if (request == null) {
            return null;
        }

        // 3. Try cookies (AuthToken, token, jwt)
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            String[] cookieNames = {token};
            for (String name : cookieNames) {
                for (Cookie cookie : cookies) {
                    if (name.equals(cookie.getName()) && cookie.getValue() != null
                            && !cookie.getValue().trim().isEmpty()) {
                        return cookie.getValue().trim();
                    }
                }
            }
        }
        return null;
    }

    public String generateToken(String userId, String role) {
        return generateToken(userId, role, jwtProperties.getJwt().getExpirationMs());
    }

    public String generateToken(String userId, String role, long expireTimeMS) {
        return generateToken(userId, role, Long.valueOf(expireTimeMS));
    }

    public String generateToken(String userId, String role, Long expireTimeMS) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        return generateToken(userId, claims, expireTimeMS);
    }

    public String generateToken(String userId, long expireTimeMS) {
        return generateToken(userId, Long.valueOf(expireTimeMS));
    }

    public String generateToken(String userId, Long expireTimeMS) {
        Map<String, Object> claims = new HashMap<>();
//        claims.put("role", role);
        return generateToken(userId, claims, expireTimeMS);
    }

    /**
     *
     * @param userId
     * @param claims
     * @param expireTimeMS in MS
     * @return
     */
    public String generateToken(String userId, Map<String, Object> claims, Long expireTimeMS) {
        long duration = expireTimeMS != null ? expireTimeMS : 2592000000L;
        return Jwts.builder()
                .claims(claims)
                .subject(userId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + duration))
                .signWith(secretKey)
                .compact();
    }

    public Claims getAllClaims(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getUserID(String token) throws JwtException {
        return getAllClaims(token).getSubject();
    }

    /**
     * Retrieves a specific claim from a JWT token.
     *
     * <p>
     * Example:
     * </p>
     *
     * <pre>
     * String role = jwtService.getClaim(token, "role", String.class);
     * Long userId = jwtService.getClaim(token, "userId", Long.class);
     * </pre>
     *
     * @param token     the JWT token to parse and validate
     * @param claimName the name of the claim to retrieve
     * @param clazz     the expected type of the claim value
     * @param <T>       the return type of the claim
     * @return the claim value converted to the specified type
     * @throws JwtException if the token is invalid, expired, malformed,
     *                      or the signature verification fails
     */
    public <T> T getClaim(String token, String claimName, Class<T> clazz)
            throws JwtException {

        return getAllClaims(token)
                .get(claimName, clazz);
    }

    public void validateToken(String token) throws JwtException {
        Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token);
    }

}