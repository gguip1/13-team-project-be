package com.matchimban.matchimban_api.global.dto;

public record ApiResponse<T>(
	String message,
	T data
) {
	public static <T> ApiResponse<T> of(String message, T data) {
		return new ApiResponse<>(message, data);
	}

	public static ApiResponse<Void> of(String message) {
		return new ApiResponse<>(message, null);
	}
}
