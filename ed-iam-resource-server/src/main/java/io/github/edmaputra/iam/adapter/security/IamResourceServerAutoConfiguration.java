package io.github.edmaputra.iam.adapter.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import io.github.edmaputra.iam.adapter.security.audit.IamAuditProperties;
import io.github.edmaputra.iam.adapter.security.audit.SecurityAuditEventListener;
import io.github.edmaputra.iam.adapter.security.audit.SecurityAuditRecorder;
import io.github.edmaputra.iam.adapter.security.evaluator.IamSecurityEvaluator;
import io.github.edmaputra.iam.adapter.security.interceptor.RequirePermissionInterceptor;
import io.github.edmaputra.iam.adapter.security.jwt.JwtAuthenticationFilter;
import io.github.edmaputra.iam.adapter.security.jwt.JwtProperties;
import io.github.edmaputra.iam.adapter.security.jwt.JwtTokenProvider;
import io.github.edmaputra.iam.adapter.security.telemetry.DefaultIamTelemetry;
import io.github.edmaputra.iam.adapter.security.telemetry.IamTelemetry;
import io.github.edmaputra.iam.adapter.security.telemetry.IamTelemetryProperties;
import io.github.edmaputra.iam.application.port.out.EventPublisherPort;
import io.github.edmaputra.iam.application.port.out.TokenRevocationPort;
import io.github.edmaputra.iam.application.port.out.ValidatingEventPublisher;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;
import io.github.edmaputra.iam.domain.tenancy.TenantContextBridge;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Spring Boot auto-configuration for IAM Resource Server (JWT verification, ScopedValue security context,
 * TenantContextBridge, OpenTelemetry distributed tracing, native metrics, and audit event publication).
 *
 * @author edmaputra
 * @since 0.0.1
 */
@AutoConfiguration
@EnableConfigurationProperties({JwtProperties.class, IamTelemetryProperties.class, IamAuditProperties.class})
public class IamResourceServerAutoConfiguration {

	/**
	 * Registers the default {@link EventPublisherPort} bridging domain events to Spring's {@link ApplicationEventPublisher}.
	 *
	 * @param applicationEventPublisher the Spring application event publisher
	 * @return event publisher port adapter
	 */
	@Bean
	@ConditionalOnMissingBean
	public EventPublisherPort iamEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		return ValidatingEventPublisher.of(applicationEventPublisher::publishEvent);
	}

	/**
	 * Registers the {@link SecurityAuditRecorder} facade coordinating metrics and validated audit event publication.
	 *
	 * @param telemetryProvider      optional telemetry provider
	 * @param eventPublisherProvider optional event publisher provider
	 * @return security audit recorder
	 */
	@Bean
	@ConditionalOnMissingBean
	public SecurityAuditRecorder securityAuditRecorder(
			ObjectProvider<IamTelemetry> telemetryProvider,
			ObjectProvider<EventPublisherPort> eventPublisherProvider) {
		return new SecurityAuditRecorder(telemetryProvider.getIfAvailable(), eventPublisherProvider.getIfAvailable());
	}

	/**
	 * Registers the {@link IamTelemetry} bean for OpenTelemetry distributed tracing and native Micrometer metrics.
	 *
	 * @param meterRegistryProvider optional Micrometer meter registry
	 * @param tracerProvider        optional OpenTelemetry tracer
	 * @param properties            telemetry configuration properties
	 * @return IAM telemetry recorder
	 */
	@Bean
	@ConditionalOnMissingBean
	public IamTelemetry iamTelemetry(
			ObjectProvider<MeterRegistry> meterRegistryProvider,
			ObjectProvider<Tracer> tracerProvider,
			IamTelemetryProperties properties) {
		return new DefaultIamTelemetry(meterRegistryProvider.getIfAvailable(), tracerProvider.getIfAvailable(), properties);
	}

	/**
	 * Registers the {@link SecurityAuditEventListener} bean for structured security audit logging via SLF4J.
	 *
	 * @param properties audit configuration properties
	 * @return security audit event listener
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = "iam.audit", name = "logging-enabled", havingValue = "true", matchIfMissing = true)
	public SecurityAuditEventListener securityAuditEventListener(IamAuditProperties properties) {
		return new SecurityAuditEventListener(properties);
	}

	/**
	 * Registers the {@link JwtTokenProvider} bean.
	 *
	 * @param properties the JWT configuration properties
	 * @return new {@link JwtTokenProvider}
	 */
	@Bean
	@ConditionalOnMissingBean
	public JwtTokenProvider jwtTokenProvider(JwtProperties properties) {
		return new JwtTokenProvider(properties);
	}

	/**
	 * Registers the {@link SecurityContextAccessor} bean.
	 *
	 * @return new {@link SecurityContextAccessor}
	 */
	@Bean
	@ConditionalOnMissingBean
	public SecurityContextAccessor securityContextAccessor() {
		return new SecurityContextAccessor();
	}

	/**
	 * Registers the {@link JwtAuthenticationFilter} bean.
	 *
	 * @param jwtTokenProvider             the JWT token provider
	 * @param securityContextAccessor      the security context accessor
	 * @param tenantContextBridgeProvider  the optional host tenant bridge provider
	 * @param tokenRevocationPortProvider  the optional token revocation port provider
	 * @param telemetryProvider            the optional telemetry provider
	 * @param eventPublisherProvider       the optional event publisher provider
	 * @return new {@link JwtAuthenticationFilter}
	 */
	@Bean
	@ConditionalOnMissingBean
	public JwtAuthenticationFilter jwtAuthenticationFilter(
			JwtTokenProvider jwtTokenProvider,
			SecurityContextAccessor securityContextAccessor,
			ObjectProvider<TenantContextBridge> tenantContextBridgeProvider,
			ObjectProvider<TokenRevocationPort> tokenRevocationPortProvider,
			ObjectProvider<IamTelemetry> telemetryProvider,
			ObjectProvider<EventPublisherPort> eventPublisherProvider) {
		return new JwtAuthenticationFilter(
				jwtTokenProvider,
				securityContextAccessor,
				tenantContextBridgeProvider,
				tokenRevocationPortProvider,
				telemetryProvider,
				eventPublisherProvider);
	}


	/**
	 * Registers the {@link RequirePermissionInterceptor} bean.
	 *
	 * @param currentActorProvider   the current actor provider
	 * @param telemetryProvider      the optional telemetry provider
	 * @param eventPublisherProvider the optional event publisher provider
	 * @return new {@link RequirePermissionInterceptor}
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = "iam.security.permissions", name = "enabled", havingValue = "true", matchIfMissing = true)
	public RequirePermissionInterceptor requirePermissionInterceptor(
			CurrentActorProvider currentActorProvider,
			ObjectProvider<IamTelemetry> telemetryProvider,
			ObjectProvider<EventPublisherPort> eventPublisherProvider) {
		return new RequirePermissionInterceptor(currentActorProvider, telemetryProvider, eventPublisherProvider);
	}

	/**
	 * Registers the {@link WebMvcConfigurer} adding the {@link RequirePermissionInterceptor}.
	 *
	 * @param interceptor the require permission interceptor
	 * @return new {@link WebMvcConfigurer}
	 */
	@Bean
	@ConditionalOnMissingBean(name = "requirePermissionWebMvcConfigurer")
	@ConditionalOnProperty(prefix = "iam.security.permissions", name = "enabled", havingValue = "true", matchIfMissing = true)
	public WebMvcConfigurer requirePermissionWebMvcConfigurer(RequirePermissionInterceptor interceptor) {
		return new WebMvcConfigurer() {
			@Override
			public void addInterceptors(InterceptorRegistry registry) {
				registry.addInterceptor(interceptor);
			}
		};
	}

	/**
	 * Registers the {@link IamSecurityEvaluator} Spring Security SpEL evaluator bean named {@code "iam"}.
	 *
	 * @param currentActorProvider the current actor provider
	 * @return new {@link IamSecurityEvaluator}
	 */
	@Bean("iam")
	@ConditionalOnMissingBean(name = "iam")
	public IamSecurityEvaluator iamSecurityEvaluator(CurrentActorProvider currentActorProvider) {
		return new IamSecurityEvaluator(currentActorProvider);
	}
}
