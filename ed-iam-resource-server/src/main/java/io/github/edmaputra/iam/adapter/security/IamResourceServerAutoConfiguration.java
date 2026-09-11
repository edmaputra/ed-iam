package io.github.edmaputra.iam.adapter.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import io.github.edmaputra.iam.adapter.security.evaluator.IamSecurityEvaluator;
import io.github.edmaputra.iam.adapter.security.interceptor.RequirePermissionInterceptor;
import io.github.edmaputra.iam.adapter.security.jwt.JwtAuthenticationFilter;
import io.github.edmaputra.iam.adapter.security.jwt.JwtProperties;
import io.github.edmaputra.iam.adapter.security.jwt.JwtTokenProvider;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;
import io.github.edmaputra.iam.domain.tenancy.TenantContextBridge;

/**
 * Spring Boot auto-configuration for IAM Resource Server (JWT verification, ScopedValue security context, and TenantContextBridge).
 *
 * @author edmaputra
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(JwtProperties.class)
public class IamResourceServerAutoConfiguration {

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
	 * @param jwtTokenProvider           the JWT token provider
	 * @param securityContextAccessor    the security context accessor
	 * @param tenantContextBridgeProvider the optional host tenant bridge provider
	 * @return new {@link JwtAuthenticationFilter}
	 */
	@Bean
	@ConditionalOnMissingBean
	public JwtAuthenticationFilter jwtAuthenticationFilter(
			JwtTokenProvider jwtTokenProvider,
			SecurityContextAccessor securityContextAccessor,
			ObjectProvider<TenantContextBridge> tenantContextBridgeProvider) {
		return new JwtAuthenticationFilter(jwtTokenProvider, securityContextAccessor, tenantContextBridgeProvider);
	}

	/**
	 * Registers the {@link RequirePermissionInterceptor} bean.
	 *
	 * @param currentActorProvider the current actor provider
	 * @return new {@link RequirePermissionInterceptor}
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = "iam.security.permissions", name = "enabled", havingValue = "true", matchIfMissing = true)
	public RequirePermissionInterceptor requirePermissionInterceptor(CurrentActorProvider currentActorProvider) {
		return new RequirePermissionInterceptor(currentActorProvider);
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
