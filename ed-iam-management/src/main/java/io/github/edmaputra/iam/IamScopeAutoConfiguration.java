package io.github.edmaputra.iam;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

import io.github.edmaputra.iam.adapter.persistence.adapter.ScopeNodeRepositoryAdapter;
import io.github.edmaputra.iam.adapter.persistence.repository.ScopeNodeJpaRepository;
import io.github.edmaputra.iam.application.port.in.ManageScopeUseCase;
import io.github.edmaputra.iam.application.port.out.EventPublisherPort;
import io.github.edmaputra.iam.application.service.ScopeHierarchyService;
import io.github.edmaputra.iam.application.service.ScopeSubtreeResolver;
import io.github.edmaputra.iam.domain.repository.ScopeNodeRepository;

/**
 * Spring Boot auto-configuration for IAM hierarchical scope tree persistence and services.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@AutoConfiguration
public class IamScopeAutoConfiguration {

	/**
	 * Registers the {@link ScopeNodeRepository} bean.
	 *
	 * @param repository the Spring Data JPA repository
	 * @return scope node repository adapter
	 */
	@Bean
	@ConditionalOnMissingBean
	public ScopeNodeRepository scopeNodeRepository(ScopeNodeJpaRepository repository) {
		return new ScopeNodeRepositoryAdapter(repository);
	}

	/**
	 * Registers the default {@link EventPublisherPort} bridging domain events to Spring's {@link ApplicationEventPublisher}.
	 *
	 * @param applicationEventPublisher the Spring application event publisher
	 * @return event publisher port adapter
	 */
	@Bean
	@ConditionalOnMissingBean
	public EventPublisherPort iamEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		return applicationEventPublisher::publishEvent;
	}

	/**
	 * Registers the {@link ManageScopeUseCase} bean.
	 *
	 * @param scopeNodeRepository the scope node repository
	 * @param eventPublisher      the domain event publisher port
	 * @return scope hierarchy management service
	 */
	@Bean
	@ConditionalOnMissingBean
	public ManageScopeUseCase manageScopeUseCase(
			ScopeNodeRepository scopeNodeRepository,
			EventPublisherPort eventPublisher) {
		return new ScopeHierarchyService(scopeNodeRepository, eventPublisher);
	}

	/**
	 * Registers the {@link ScopeSubtreeResolver} bean.
	 *
	 * @param scopeNodeRepository the scope node repository
	 * @return scope subtree resolver engine
	 */
	@Bean
	@ConditionalOnMissingBean
	public ScopeSubtreeResolver scopeSubtreeResolver(ScopeNodeRepository scopeNodeRepository) {
		return new ScopeSubtreeResolver(scopeNodeRepository);
	}
}
