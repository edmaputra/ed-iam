package io.github.edmaputra.iam.adapter.rest;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
 * Unit tests for {@link MagicLinkOperationsDelegate}.
 *
 * @author edmaputra
 * @since 0.11.0
 */
class MagicLinkOperationsDelegateTest {

	private ManageMagicLinkUseCase manageMagicLinkUseCase;
	private MagicLinkOperationsDelegate delegate;

	@BeforeEach
	void setUp() {
		manageMagicLinkUseCase = mock(ManageMagicLinkUseCase.class);
		delegate = new MagicLinkOperationsDelegate(manageMagicLinkUseCase);
	}

	@Test
	@DisplayName("Should enforce non-null ManageMagicLinkUseCase in constructor")
	void shouldEnforceNonNullDependency() {
		assertThatThrownBy(() -> new MagicLinkOperationsDelegate(null))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	@DisplayName("Should request magic link with header tenant ID string")
	void shouldRequestMagicLinkWithHeaderTenant() {
		UUID tenantId = UUID.randomUUID();
		MagicLinkSendRequest request = new MagicLinkSendRequest("user@clinic.org", null, "https://app/verify");
		MagicLinkRequestResponse expected = MagicLinkRequestResponse.of("Magic link dispatched");

		when(manageMagicLinkUseCase.requestMagicLink(any(MagicLinkRequestCommand.class))).thenReturn(expected);

		MagicLinkRequestResponse actual = delegate.requestMagicLink(request, tenantId.toString());

		assertThat(actual).isSameAs(expected);
		verify(manageMagicLinkUseCase).requestMagicLink(new MagicLinkRequestCommand("user@clinic.org", new io.github.edmaputra.iam.domain.tenancy.TenantId(tenantId), "https://app/verify"));
	}

	@Test
	@DisplayName("Should request magic link with explicit tenant UUID")
	void shouldRequestMagicLinkWithExplicitTenantId() {
		UUID tenantId = UUID.randomUUID();
		MagicLinkSendRequest request = new MagicLinkSendRequest("user@clinic.org", null, "https://app/verify");
		MagicLinkRequestResponse expected = MagicLinkRequestResponse.of("Dispatched");

		when(manageMagicLinkUseCase.requestMagicLink(any(MagicLinkRequestCommand.class))).thenReturn(expected);

		MagicLinkRequestResponse actual = delegate.requestMagicLink(request, tenantId);

		assertThat(actual).isSameAs(expected);
		verify(manageMagicLinkUseCase).requestMagicLink(new MagicLinkRequestCommand("user@clinic.org", new io.github.edmaputra.iam.domain.tenancy.TenantId(tenantId), "https://app/verify"));
	}

	@Test
	@DisplayName("Should request magic link with pre-constructed command")
	void shouldRequestMagicLinkWithCommand() {
		MagicLinkRequestCommand command = new MagicLinkRequestCommand("user@clinic.org", null, null);
		MagicLinkRequestResponse expected = MagicLinkRequestResponse.of("Dispatched");

		when(manageMagicLinkUseCase.requestMagicLink(command)).thenReturn(expected);

		MagicLinkRequestResponse actual = delegate.requestMagicLink(command);

		assertThat(actual).isSameAs(expected);
		verify(manageMagicLinkUseCase).requestMagicLink(command);
	}

	@Test
	@DisplayName("Should verify magic link request DTO with HttpServletRequest metadata")
	void shouldVerifyMagicLinkWithHttpRequest() {
		TokenResponse expected = new TokenResponse("access", "refresh", "Bearer", 3600L, mock(UserProfileResponse.class));
		when(manageMagicLinkUseCase.verifyMagicLink(any(MagicLinkVerifyCommand.class))).thenReturn(expected);

		HttpServletRequest httpRequest = mock(HttpServletRequest.class);
		when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("198.51.100.22");
		when(httpRequest.getHeader("User-Agent")).thenReturn("Mozilla/5.0");

		MagicLinkVerifyRequest request = new MagicLinkVerifyRequest("token-abc");
		TokenResponse actual = delegate.verifyMagicLink(request, httpRequest);

		assertThat(actual).isSameAs(expected);
		verify(manageMagicLinkUseCase).verifyMagicLink(new MagicLinkVerifyCommand("token-abc", "198.51.100.22", "Mozilla/5.0"));
	}

	@Test
	@DisplayName("Should verify magic link request DTO with explicit client metadata")
	void shouldVerifyMagicLinkWithExplicitMetadata() {
		TokenResponse expected = new TokenResponse("access", "refresh", "Bearer", 3600L, mock(UserProfileResponse.class));
		when(manageMagicLinkUseCase.verifyMagicLink(any(MagicLinkVerifyCommand.class))).thenReturn(expected);

		MagicLinkVerifyRequest request = new MagicLinkVerifyRequest("token-abc");
		TokenResponse actual = delegate.verifyMagicLink(request, "10.0.0.1", "curl/7.68.0");

		assertThat(actual).isSameAs(expected);
		verify(manageMagicLinkUseCase).verifyMagicLink(new MagicLinkVerifyCommand("token-abc", "10.0.0.1", "curl/7.68.0"));
	}

	@Test
	@DisplayName("Should verify magic link raw token string with HttpServletRequest metadata")
	void shouldVerifyRawTokenWithHttpRequest() {
		TokenResponse expected = new TokenResponse("access", "refresh", "Bearer", 3600L, mock(UserProfileResponse.class));
		when(manageMagicLinkUseCase.verifyMagicLink(any(MagicLinkVerifyCommand.class))).thenReturn(expected);

		HttpServletRequest httpRequest = mock(HttpServletRequest.class);
		when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("198.51.100.33");
		when(httpRequest.getHeader("User-Agent")).thenReturn("Safari/16.0");

		TokenResponse actual = delegate.verifyMagicLink("raw-token-xyz", httpRequest);

		assertThat(actual).isSameAs(expected);
		verify(manageMagicLinkUseCase).verifyMagicLink(new MagicLinkVerifyCommand("raw-token-xyz", "198.51.100.33", "Safari/16.0"));
	}

	@Test
	@DisplayName("Should verify magic link raw token string with explicit metadata")
	void shouldVerifyRawTokenWithExplicitMetadata() {
		TokenResponse expected = new TokenResponse("access", "refresh", "Bearer", 3600L, mock(UserProfileResponse.class));
		when(manageMagicLinkUseCase.verifyMagicLink(any(MagicLinkVerifyCommand.class))).thenReturn(expected);

		TokenResponse actual = delegate.verifyMagicLink("raw-token-xyz", "10.1.2.3", "PostmanRuntime");

		assertThat(actual).isSameAs(expected);
		verify(manageMagicLinkUseCase).verifyMagicLink(new MagicLinkVerifyCommand("raw-token-xyz", "10.1.2.3", "PostmanRuntime"));
	}

	@Test
	@DisplayName("Should verify magic link with pre-constructed command")
	void shouldVerifyWithCommand() {
		MagicLinkVerifyCommand command = new MagicLinkVerifyCommand("cmd-token", "1.2.3.4", "agent");
		TokenResponse expected = new TokenResponse("access", "refresh", "Bearer", 3600L, mock(UserProfileResponse.class));
		when(manageMagicLinkUseCase.verifyMagicLink(command)).thenReturn(expected);

		TokenResponse actual = delegate.verifyMagicLink(command);

		assertThat(actual).isSameAs(expected);
		verify(manageMagicLinkUseCase).verifyMagicLink(command);
	}
}
