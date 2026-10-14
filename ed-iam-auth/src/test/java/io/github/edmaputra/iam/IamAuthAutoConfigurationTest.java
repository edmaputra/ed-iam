package io.github.edmaputra.iam;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.edmaputra.iam.adapter.rest.MfaController;
import io.github.edmaputra.iam.adapter.rest.MfaOperationsDelegate;
import io.github.edmaputra.iam.application.port.in.AuthenticateUserUseCase;
import io.github.edmaputra.iam.application.port.in.ManageMagicLinkUseCase;
import io.github.edmaputra.iam.application.port.in.ManageMfaUseCase;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link IamAuthAutoConfiguration} verifying conditional registration
 * of MFA controller and operations delegate.
 *
 * @author edmaputra
 * @since 0.11.0
 */
class IamAuthAutoConfigurationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(IamAuthAutoConfiguration.class))
			.withUserConfiguration(MockInfrastructureConfig.class);

	@Test
	@DisplayName("Should register MfaOperationsDelegate but NOT MfaController when matchIfMissing is false")
	void shouldNotRegisterMfaControllerByDefault() {
		contextRunner.run(context -> {
			assertThat(context).hasSingleBean(MfaOperationsDelegate.class);
			assertThat(context).doesNotHaveBean(MfaController.class);
		});
	}

	@Test
	@DisplayName("Should register MfaController when iam.auth.mfa.endpoints.enabled=true")
	void shouldRegisterMfaControllerWhenEnabled() {
		contextRunner
				.withPropertyValues("iam.auth.mfa.endpoints.enabled=true")
				.run(context -> {
					assertThat(context).hasSingleBean(MfaOperationsDelegate.class);
					assertThat(context).hasSingleBean(MfaController.class);
				});
	}

	@Test
	@DisplayName("Should NOT register MfaController when iam.auth.mfa.endpoints.enabled=false")
	void shouldNotRegisterMfaControllerWhenExplicitlyDisabled() {
		contextRunner
				.withPropertyValues("iam.auth.mfa.endpoints.enabled=false")
				.run(context -> {
					assertThat(context).hasSingleBean(MfaOperationsDelegate.class);
					assertThat(context).doesNotHaveBean(MfaController.class);
				});
	}

	@Test
	@DisplayName("Should register MagicLinkOperationsDelegate but NOT MagicLinkController when matchIfMissing is false")
	void shouldNotRegisterMagicLinkControllerByDefault() {
		contextRunner.run(context -> {
			assertThat(context).hasSingleBean(io.github.edmaputra.iam.adapter.rest.MagicLinkOperationsDelegate.class);
			assertThat(context).doesNotHaveBean(io.github.edmaputra.iam.adapter.rest.MagicLinkController.class);
		});
	}

	@Test
	@DisplayName("Should register MagicLinkController when iam.auth.magic-link.endpoints.enabled=true")
	void shouldRegisterMagicLinkControllerWhenEnabled() {
		contextRunner
				.withPropertyValues("iam.auth.magic-link.endpoints.enabled=true")
				.run(context -> {
					assertThat(context).hasSingleBean(io.github.edmaputra.iam.adapter.rest.MagicLinkOperationsDelegate.class);
					assertThat(context).hasSingleBean(io.github.edmaputra.iam.adapter.rest.MagicLinkController.class);
				});
	}

	@Test
	@DisplayName("Should NOT register MagicLinkController when iam.auth.magic-link.endpoints.enabled=false")
	void shouldNotRegisterMagicLinkControllerWhenExplicitlyDisabled() {
		contextRunner
				.withPropertyValues("iam.auth.magic-link.endpoints.enabled=false")
				.run(context -> {
					assertThat(context).hasSingleBean(io.github.edmaputra.iam.adapter.rest.MagicLinkOperationsDelegate.class);
					assertThat(context).doesNotHaveBean(io.github.edmaputra.iam.adapter.rest.MagicLinkController.class);
				});
	}

	@Configuration(proxyBeanMethods = false)
	static class MockInfrastructureConfig {

		@Bean
		UserRepository userRepository() {
			return mock(UserRepository.class);
		}

		@Bean
		CurrentActorProvider currentActorProvider() {
			return mock(CurrentActorProvider.class);
		}

		@Bean
		AuthenticateUserUseCase authenticateUserUseCase() {
			return mock(AuthenticateUserUseCase.class);
		}

		@Bean
		ManageMagicLinkUseCase manageMagicLinkUseCase() {
			return mock(ManageMagicLinkUseCase.class);
		}

		@Bean
		ManageMfaUseCase manageMfaUseCase() {
			return mock(ManageMfaUseCase.class);
		}

		@Bean
		io.github.edmaputra.iam.application.service.FederatedIdentityService federatedIdentityService() {
			return mock(io.github.edmaputra.iam.application.service.FederatedIdentityService.class);
		}

		@Bean
		io.github.edmaputra.iam.application.service.EffectiveAccessResolver effectiveAccessResolver() {
			return mock(io.github.edmaputra.iam.application.service.EffectiveAccessResolver.class);
		}

		@Bean
		io.github.edmaputra.iam.application.service.UserTokenService userTokenService() {
			return mock(io.github.edmaputra.iam.application.service.UserTokenService.class);
		}

		@Bean
		io.github.edmaputra.iam.application.service.AuthSessionService authSessionService() {
			return mock(io.github.edmaputra.iam.application.service.AuthSessionService.class);
		}

		@Bean
		io.github.edmaputra.iam.application.service.CredentialAuthService credentialAuthService() {
			return mock(io.github.edmaputra.iam.application.service.CredentialAuthService.class);
		}
	}
}
