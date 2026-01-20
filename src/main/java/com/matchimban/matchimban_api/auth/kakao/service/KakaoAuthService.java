package com.matchimban.matchimban_api.auth.kakao.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matchimban.matchimban_api.auth.kakao.dto.KakaoTokenResponse;
import com.matchimban.matchimban_api.auth.kakao.dto.KakaoUserInfo;
import com.matchimban.matchimban_api.global.error.ApiException;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class KakaoAuthService {
	private static final Duration STATE_TTL = Duration.ofMinutes(10);

	private final Map<String, Instant> stateStore = new ConcurrentHashMap<>();
	private final String authorizeUrl;
	private final String tokenUrl;
	private final String userInfoUrl;
	private final String clientId;
	private final String clientSecret;
	private final String redirectUri;
	private final RestTemplate restTemplate;
	private final ObjectMapper objectMapper;

	public KakaoAuthService(
		@Value("${kakao.oauth.authorize-url}") String authorizeUrl,
		@Value("${kakao.oauth.token-url}") String tokenUrl,
		@Value("${kakao.oauth.user-info-url}") String userInfoUrl,
		@Value("${kakao.oauth.client-id}") String clientId,
		@Value("${kakao.oauth.client-secret}") String clientSecret,
		@Value("${kakao.oauth.redirect-uri}") String redirectUri,
		RestTemplateBuilder restTemplateBuilder,
		ObjectMapper objectMapper
	) {
		this.authorizeUrl = authorizeUrl;
		this.tokenUrl = tokenUrl;
		this.userInfoUrl = userInfoUrl;
		this.clientId = clientId;
		this.clientSecret = clientSecret;
		this.redirectUri = redirectUri;
		this.restTemplate = restTemplateBuilder.build();
		this.objectMapper = objectMapper;
	}

	public String issueState() {
		String state = UUID.randomUUID().toString();
		stateStore.put(state, Instant.now());
		cleanupExpiredStates();
		return state;
	}

	public boolean consumeState(String state) {
		if (state == null || state.isBlank()) {
			return false;
		}
		Instant issuedAt = stateStore.remove(state);
		if (issuedAt == null) {
			return false;
		}
		return issuedAt.isAfter(Instant.now().minus(STATE_TTL));
	}

	public String buildAuthorizeUrl(String state) {
		validateOauthConfig();

		return UriComponentsBuilder.fromUriString(authorizeUrl)
			.queryParam("response_type", "code")
			.queryParam("client_id", clientId)
			.queryParam("redirect_uri", redirectUri)
			.queryParam("state", state)
			.toUriString();
	}

	public KakaoTokenResponse requestToken(String code) {
		validateOauthConfig();

		MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
		body.add("grant_type", "authorization_code");
		body.add("client_id", clientId);
		body.add("redirect_uri", redirectUri);
		body.add("code", code);
		if (clientSecret != null && !clientSecret.isBlank()) {
			body.add("client_secret", clientSecret);
		}

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

		HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
		try {
			ResponseEntity<KakaoTokenResponse> response = restTemplate.postForEntity(
				tokenUrl,
				request,
				KakaoTokenResponse.class
			);
			KakaoTokenResponse tokenResponse = response.getBody();
			if (tokenResponse == null || tokenResponse.accessToken() == null) {
				throw new ApiException(HttpStatus.BAD_GATEWAY, "kakao_token_request_failed");
			}
			return tokenResponse;
		} catch (RestClientResponseException ex) {
			throw new ApiException(
				HttpStatus.BAD_GATEWAY,
				"kakao_token_request_failed",
				ex.getResponseBodyAsString()
			);
		}
	}

	public KakaoUserInfo requestUserInfo(String accessToken) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(accessToken);
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		HttpEntity<Void> request = new HttpEntity<>(headers);

		try {
			ResponseEntity<String> response = restTemplate.exchange(
				userInfoUrl,
				HttpMethod.GET,
				request,
				String.class
			);
			String responseBody = response.getBody();
			if (responseBody == null || responseBody.isBlank()) {
				throw new ApiException(HttpStatus.BAD_GATEWAY, "kakao_userinfo_request_failed");
			}

			JsonNode root = objectMapper.readTree(responseBody);
			Long id = root.path("id").isNumber() ? root.path("id").asLong() : null;
			JsonNode profileNode = root.path("kakao_account").path("profile");
			String nickname = profileNode.path("nickname").asText(null);
			String thumbnailImageUrl = profileNode.path("thumbnail_image_url").asText(null);
			String profileImageUrl = profileNode.path("profile_image_url").asText(null);

			return new KakaoUserInfo(root, id, nickname, thumbnailImageUrl, profileImageUrl);
		} catch (RestClientResponseException ex) {
			throw new ApiException(
				HttpStatus.BAD_GATEWAY,
				"kakao_userinfo_request_failed",
				ex.getResponseBodyAsString()
			);
		} catch (IOException ex) {
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "internal_server_error", ex.getMessage());
		}
	}

	private void validateOauthConfig() {
		if (clientId == null || clientId.isBlank() || redirectUri == null || redirectUri.isBlank()) {
			throw new ApiException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"internal_server_error",
				"Missing Kakao OAuth configuration"
			);
		}
	}

	private void cleanupExpiredStates() {
		Instant cutoff = Instant.now().minus(STATE_TTL);
		stateStore.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
	}
}
