package io.github.edmaputra.iam.adapter.rest;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import io.github.edmaputra.iam.adapter.rest.dto.MagicLinkSendRequest;
import io.github.edmaputra.iam.adapter.rest.dto.MagicLinkVerifyRequest;
import io.github.edmaputra.iam.application.model.MagicLinkRequestResponse;
import io.github.edmaputra.iam.application.model.TokenResponse;
import io.github.edmaputra.iam.application.model.UserProfileResponse;
import io.github.edmaputra.iam.application.port.in.MagicLinkRequestCommand;
import io.github.edmaputra.iam.application.port.in.MagicLinkVerifyCommand;
import io.github.edmaputra.iam.application.port.in.ManageMagicLinkUseCase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MagicLinkController}.
 *
 * @author edmaputra
 * @since 0.7.0
 */
class MagicLinkControllerTest {

	private ManageMagicLinkUseCase manageMagicLinkUseCase;
	private MagicLinkController controller;

	@BeforeEach
	void setUp() {
		manageMagicLinkUseCase = mock(ManageMagicLinkUseCase.class);
		controller = new MagicLinkController(manageMagicLinkUseCase);
	}

	@Test
	@DisplayName("Should enforce non-null ManageMagicLinkUseCase in constructor")
	void shouldEnforceNonNullDependency() {
		assertThatThrownBy(() -> new MagicLinkController(null))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	@DisplayName("Should request magic link and return response")
	void shouldRequestMagicLink() {
		MagicLinkSendRequest request = new MagicLinkSendRequest("user@example.com", null, "https://app/welcome");
		MagicLinkRequestResponse expected = MagicLinkRequestResponse.of("Sent");

		when(manageMagicLinkUseCase.requestMagicLink(any(MagicLinkRequestCommand.class))).thenReturn(expected);

		ResponseEntity<MagicLinkRequestResponse> response = controller.requestMagicLink(null, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isSameAs(expected);
		verify(manageMagicLinkUseCase).requestMagicLink(any(MagicLinkRequestCommand.class));
	}

	@Test
	@DisplayName("Should verify magic link via POST")
	void shouldVerifyMagicLinkPost() {
		MagicLinkVerifyRequest request = new MagicLinkVerifyRequest("token123");
		TokenResponse expected = new TokenResponse("access", "refresh", "Bearer", 3600L, mock(UserProfileResponse.class));

		when(manageMagicLinkUseCase.verifyMagicLink(any(MagicLinkVerifyCommand.class))).thenReturn(expected);

		ResponseEntity<TokenResponse> response = controller.verifyMagicLinkPost(request, null);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isSameAs(expected);
		verify(manageMagicLinkUseCase).verifyMagicLink(any(MagicLinkVerifyCommand.class));
	}

	@Test
	@DisplayName("Should verify magic link via GET")
	void shouldVerifyMagicLinkGet() {
		TokenResponse expected = new TokenResponse("access", "refresh", "Bearer", 3600L, mock(UserProfileResponse.class));

		when(manageMagicLinkUseCase.verifyMagicLink(any(MagicLinkVerifyCommand.class))).thenReturn(expected);

		ResponseEntity<TokenResponse> response = controller.verifyMagicLinkGet("token123", null);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isSameAs(expected);
		verify(manageMagicLinkUseCase).verifyMagicLink(any(MagicLinkVerifyCommand.class));
	}
}
