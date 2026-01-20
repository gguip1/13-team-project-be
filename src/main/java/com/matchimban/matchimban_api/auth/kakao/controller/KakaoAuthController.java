package com.matchimban.matchimban_api.auth.kakao.controller;

import com.matchimban.matchimban_api.auth.kakao.dto.KakaoTokenResponse;
import com.matchimban.matchimban_api.auth.kakao.dto.KakaoUserInfo;
import com.matchimban.matchimban_api.auth.kakao.service.KakaoAuthService;
import com.matchimban.matchimban_api.global.error.ApiException;
import com.matchimban.matchimban_api.global.error.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "Kakao OAuth endpoints")
@RestController
@Slf4j
@RequestMapping("/api/v1/auth/kakao")
public class KakaoAuthController {

	private final KakaoAuthService kakaoAuthService;

	public KakaoAuthController(KakaoAuthService kakaoAuthService) {
		this.kakaoAuthService = kakaoAuthService;
	}

	@Operation(summary = "카카오 로그인 시작", description = "카카오 인가 페이지로 302 Redirect")
	@ApiResponses({
		@ApiResponse(responseCode = "302", description = "redirect_to_kakao_authorize"),
		@ApiResponse(
			responseCode = "500",
			description = "internal_server_error",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class))
		)
	})
	@GetMapping("/login")
	public ResponseEntity<Void> login() {
		String state = kakaoAuthService.issueState();
		String loginUrl = kakaoAuthService.buildAuthorizeUrl(state);

		HttpHeaders headers = new HttpHeaders();
		headers.setLocation(URI.create(loginUrl));
		return new ResponseEntity<>(headers, HttpStatus.FOUND);
	}


	@Operation(summary = "카카오 인가코드 콜백", description = "state 검증 후 302 Redirect")
	@ApiResponses({
		@ApiResponse(responseCode = "302", description = "login_success_redirect_to_..."),
		@ApiResponse(
			responseCode = "400",
			description = "invalid_request",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class))
		),
		@ApiResponse(
			responseCode = "401",
			description = "invalid_oauth_state",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class))
		),
		@ApiResponse(
			responseCode = "403",
			description = "oauth_access_denied",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class))
		),
		@ApiResponse(
			responseCode = "500",
			description = "internal_server_error",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class))
		)
	})
	@GetMapping("/auth-code")
	public ResponseEntity<Void> authCode(
		@Parameter(description = "OAuth error code")
		@RequestParam(required = false) String error,
		@Parameter(description = "OAuth error description")
		@RequestParam(name = "error_description", required = false) String errorDescription,
		@Parameter(description = "CSRF protection state")
		@RequestParam(required = false) String state,
		@Parameter(description = "Authorization code")
		@RequestParam(required = false) String code
	) {
		log.info(
			"Kakao auth-code callback. state={}, code={}, error={}, errorDescription={}",
			state,
			code,
			error,
			errorDescription
		);

		if (error != null) {
			String detail = errorDescription != null ? errorDescription : error;
			throw new ApiException(HttpStatus.FORBIDDEN, "oauth_access_denied", detail);
		}
		if (!kakaoAuthService.consumeState(state)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_oauth_state");
		}
		if (code == null || code.isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_request");
		}

		KakaoTokenResponse tokenResponse = kakaoAuthService.requestToken(code);
		KakaoUserInfo userInfo = kakaoAuthService.requestUserInfo(tokenResponse.accessToken());
		log.info("Kakao user info raw={}", userInfo.raw());
		log.info(
			"Kakao profile id={}, nickname={}, thumbnailImageUrl={}, profileImageUrl={}",
			userInfo.id(),
			userInfo.nickname(),
			userInfo.thumbnailImageUrl(),
			userInfo.profileImageUrl()
		);

		HttpHeaders headers = new HttpHeaders();
		headers.setLocation(URI.create("/"));
		return new ResponseEntity<>(headers, HttpStatus.FOUND);
	}
}
