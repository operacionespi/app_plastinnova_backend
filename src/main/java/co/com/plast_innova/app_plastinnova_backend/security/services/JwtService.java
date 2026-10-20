package co.com.plast_innova.app_plastinnova_backend.security.services;

import co.com.plast_innova.app_plastinnova_backend.security.CustomUserDetails;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    /** Emisión del token en milisegundos (ver esAnteriorAlCierre). */
    private static final String CLAIM_EMITIDO_MS = "emitido_ms";

    @Value("${app.security.jwt.secret}")
    private String secretKey;

    @Value("${app.security.jwt.expiration-ms}")
    private long jwtExpiration;

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        long ahora = System.currentTimeMillis();
        return Jwts.builder()
                .claims(extraClaims)
                .claim(CLAIM_EMITIDO_MS, ahora)
                .subject(userDetails.getUsername())
                .issuedAt(new Date(ahora))
                .expiration(new Date(ahora + jwtExpiration))
                .signWith(getSignInKey(), Jwts.SIG.HS256)
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token) && !esAnteriorAlCierre(token, userDetails);
    }

    /**
     * El administrador cerró las sesiones del usuario (restableció su contraseña o lo desactivó). "iat" solo tiene
     * segundos: se usa la emisión en milisegundos para que un token emitido justo después del cierre siga sirviendo.
     */
    private boolean esAnteriorAlCierre(String token, UserDetails userDetails) {
        if (!(userDetails instanceof CustomUserDetails detalles) || detalles.getUsuario().getSesionesValidasDesde() == null) {
            return false;
        }
        Instant cierre = detalles.getUsuario().getSesionesValidasDesde().toInstant(ZoneOffset.UTC);
        Number emitidoMs = extractClaim(token, c -> c.get(CLAIM_EMITIDO_MS, Number.class));
        Instant emitido = emitidoMs != null ? Instant.ofEpochMilli(emitidoMs.longValue())
                : extractClaim(token, Claims::getIssuedAt).toInstant();
        return emitido.isBefore(cierre);
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