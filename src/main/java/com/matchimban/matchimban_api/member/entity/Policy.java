package com.matchimban.matchimban_api.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "policy")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Policy {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "policy_seq")
	@SequenceGenerator(name = "policy_seq", sequenceName = "policy_seq", allocationSize = 1)
	private Long id;

	@Column(name = "terms_content", columnDefinition = "TEXT", nullable = false)
	private String termsContent;

	@Column(name = "terms_version", length = 10, nullable = false)
	private String termsVersion;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "is_required", nullable = false)
	private boolean isRequired;
}
