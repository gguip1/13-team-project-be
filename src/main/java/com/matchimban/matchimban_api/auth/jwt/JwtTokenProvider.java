package com.matchimban.matchimban_api.auth.jwt;

import com.matchimban.matchimban_api.member.entity.Member;
import com.matchimban.matchimban_api.member.entity.enums.MemberStatus;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component // DI를 위해서 붙임
public class JwtTokenProvider {

	private static final int MIN_SECRET_BYTES = 32; //최소 secret 길이 기준

	private final JwtProperties properties;
	private final SecretKey key;

	public JwtTokenProvider(JwtProperties properties) {
		this.properties = properties;
		validateProperties(properties); // 설정이 잘못되면 즉시 에러를 뱉음
		this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
	}

	public String createAccessToken(Member member) { // 액세스 토큰 발급
		// 만료시간 설정
        Instant now = Instant.now();
		Instant expiresAt = now.plus(Duration.ofMinutes(properties.accessTokenExpireMinutes()));

		return Jwts.builder()
			.subject(String.valueOf(member.getId())) //member id.
			.issuer(properties.issuer())
			.issuedAt(Date.from(now))
			.expiration(Date.from(expiresAt))
			.claim("status", member.getStatus().name())
			.signWith(key)
			.compact();
	}

	public ResponseCookie createAccessTokenCookie(String token) { //jwt를 쿠키로 만들기
		return ResponseCookie.from(properties.cookieName(), token)
			.httpOnly(true)
			.secure(properties.cookieSecure())
			.sameSite(properties.cookieSameSite())
			.path("/")
			.maxAge(Duration.ofMinutes(properties.accessTokenExpireMinutes()))
			.build();
	}

	public Optional<Authentication> getAuthentication(String token) { //토큰 → Authentication 만들기
		return parseToken(token)
			.map(principal -> new UsernamePasswordAuthenticationToken(principal, null, List.of()));
	}

	private Optional<MemberPrincipal> parseToken(String token) { //토큰 검증
		try {
			Claims claims = Jwts.parser()
				.requireIssuer(properties.issuer())
				.verifyWith(key) // 검증에 쓸 키 지정
				.build()
				.parseSignedClaims(token)// 서명 검증
				.getPayload();

			String subject = claims.getSubject();
			String statusValue = claims.get("status", String.class);
			if (subject == null || statusValue == null) {
				return Optional.empty();
			}

			MemberStatus status = MemberStatus.valueOf(statusValue);
			return Optional.of(new MemberPrincipal(Long.valueOf(subject), status));
		} catch (JwtException | IllegalArgumentException ex) {
			return Optional.empty();
		}
	}

	private void validateProperties(JwtProperties properties) {
		if (properties.secret() == null || properties.secret().isBlank()) {
			throw new IllegalStateException("JWT secret is missing");
		}
		if (properties.secret().getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
			throw new IllegalStateException("JWT secret must be at least 32 bytes");
		}
		if (properties.accessTokenExpireMinutes() <= 0) {
			throw new IllegalStateException("JWT access token expiration must be positive");
		}
		if (properties.issuer() == null || properties.issuer().isBlank()) {
			throw new IllegalStateException("JWT issuer is missing");
		}
		if (properties.cookieName() == null || properties.cookieName().isBlank()) {
			throw new IllegalStateException("JWT cookie name is missing");
		}
		if (properties.cookieSameSite() == null || properties.cookieSameSite().isBlank()) {
			throw new IllegalStateException("JWT cookie sameSite is missing");
		}
	}
}
