package com.deportlink.deportlink.security.config;

import com.deportlink.deportlink.model.entity.UserEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Base64;
import java.util.Date;
import java.util.function.Function;


@Component
public class JwtUtil {

    private final Key secretKey;
    private final long expirationMs;

    public JwtUtil(
            @Value("${jwt.secret}") String secretKey,
            @Value("${jwt.expiration}") long expirationMs
    ) {
        byte[] keyBytes = Base64.getDecoder().decode(secretKey);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
    }

    public String generateToken(UserEntity userEntity) {

        return Jwts.builder()
                .setSubject(userEntity.getEmail()) // Guarda el email dentro del token
                .claim("id", userEntity.getId())
                .claim("role", "ROLE_" + userEntity.getRole().name())
                .setIssuedAt(new Date(System.currentTimeMillis())) // Fecha de creación
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs)) // Vence en 10 horas
                .signWith(this.secretKey) // Lo firma con la clave secreta del servidor
                .compact(); // Lo transforma en el texto largo "eyJhbGci..."
    }

    // Extraer el email (username) del token
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    // Verificar si el token ya venció (devuelve true o false)
    public boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    //EL LIMPIADOR: Agarra el texto "Bearer eyJhbG..." y te devuelve solo el "eyJhbG..." limpio
    public String resolveToken(String bearerToken) {
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7); // Corta los primeros 7 caracteres ("Bearer ")
        }
        return null;
    }

    // EXTRAER EL ID: Saca el ID numérico del usuario
    public Long extractUserId(String token) {
        return extractClaim(token, claims -> claims.get("id", Long.class));
    }

    // EXTRAER EL ROL: Saca el rol (Ej: ROLE_ADMIN)
    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    // C. El extractor central (El que abre el paquete usando la llave secreta)
    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = Jwts.parserBuilder()
                .setSigningKey(this.secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claimsResolver.apply(claims);
    }
}
