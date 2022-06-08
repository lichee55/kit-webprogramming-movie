package com.kit.kumovie.config.jwt;

import com.kit.kumovie.service.AuthService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.security.Key;
import java.util.Base64;
import java.util.Date;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    @Value("${external.jwt.secret}")
    private String secretKey;

    private final AuthService authService;

    private final Long tokenExpirationTime = 1000L * 60 * 30;
    private final Long refreshTokenExpirationTime = 1000L * 60 * 60 * 24;

    @PostConstruct
    protected void init() {
        secretKey = Base64.getEncoder().encodeToString(secretKey.getBytes());
    }

    private Key getSignKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes());
    }

    public String createToken(String userPk, List<String> roles) { // 최초 로그인시 사용
        Claims claims = Jwts.claims().setSubject(userPk);
        claims.put("roles", roles);
        Date now = new Date();
        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + tokenExpirationTime))
                .signWith(getSignKey())
                .compact();
    }

    public String createToken(String refreshToken) { // refresh token 을 이용해서 새로운 access token 을 발급한다.
        Jws<Claims> claimsJws = Jwts.parserBuilder().setSigningKey(getSignKey()).build().parseClaimsJws(refreshToken);
        Claims claims = claimsJws.getBody();
        Date now = new Date();
        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + tokenExpirationTime))
                .signWith(getSignKey())
                .compact();
    }

    public String createRefreshToken(String userPk, List<String> roles) {
        Claims claims = Jwts.claims().setSubject(userPk);
        claims.put("roles", roles);
        Date now = new Date();
        String compact = Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + refreshTokenExpirationTime))
                .signWith(getSignKey())
                .compact();
        authService.updateRefreshToken(userPk, compact);
        return compact;
    }

    public String getUserPkFromToken(String token) {
        return Jwts.parserBuilder().setSigningKey(getSignKey()).build().parseClaimsJws(token).getBody().getSubject();
    }

    public String getUserPkFromRefreshToken(String token) {
        return Jwts.parserBuilder().setSigningKey(getSignKey()).build().parseClaimsJws(token).getBody().getSubject();
    }

    public Authentication getAuthentication(String token) {
        UserDetails userDetails = authService.loadUserByUsername(getUserPkFromToken(token));
        return new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
    }

    public String resolveToken(HttpServletRequest req) {
        return req.getHeader("X-AUTH-TOKEN");
    }

    public String resolveRefreshToken(HttpServletRequest req) {
        return req.getHeader("X-REFRESH-TOKEN");
    }

    public Boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(getSignKey()).build().parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Boolean validateRefreshToken(String refreshToken) {
        try {
            Jws<Claims> claimsJws = Jwts.parserBuilder().setSigningKey(getSignKey()).build().parseClaimsJws(refreshToken);
            String userPk = claimsJws.getBody().getSubject();
            return authService.validateRefreshToken(userPk, refreshToken);
        } catch (Exception e) {
            return false;
        }
    }
}
