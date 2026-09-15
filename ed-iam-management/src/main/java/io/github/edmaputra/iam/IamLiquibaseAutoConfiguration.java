package io.github.edmaputra.iam;

import java.util.Arrays;
import javax.sql.DataSource;

import liquibase.integration.spring.SpringLiquibase;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jpa.autoconfigure.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot Auto-Configuration for Identity and Access Management (IAM) Liquibase migrations.
 * <p>
 * This configuration manages module-specific database migrations ({@code db.changelog-iam.json})
 * which create tables for users, roles, permissions, and scope hierarchies.
 * <p>
 * <b>Initialization and Dependency Coordination:</b>
 * <ul>
 *   <li><b>Inter-Module Migration Ordering:</b> Uses a {@link BeanFactoryPostProcessor} to dynamically
 *       declare that the {@code iamLiquibase} bean depends on the master {@code liquibase} bean. This ensures
 *       the core schema (e.g. {@code tenants}) is established before IAM tables with foreign key constraints
 *       are created.</li>
 *   <li><b>JPA Readiness:</b> Registers an {@link EntityManagerFactoryDependsOnPostProcessor} to guarantee
 *       Hibernate's {@link jakarta.persistence.EntityManagerFactory} is not initialized until the IAM
 *       migrations have completed.</li>
 * </ul>
 *
 * @author edmaputra
 * @since 0.0.1
 */
@AutoConfiguration
@ConditionalOnClass(SpringLiquibase.class)
@ConditionalOnProperty(prefix = "spring.liquibase", name = "enabled", matchIfMissing = true)
public class IamLiquibaseAutoConfiguration {

	/**
	 * Configures the IAM module {@link SpringLiquibase} runner.
	 *
	 * @param dataSource the shared application datasource
	 * @return configured IAM SpringLiquibase bean
	 */
	@Bean
	public SpringLiquibase iamLiquibase(DataSource dataSource) {
		SpringLiquibase liquibase = new SpringLiquibase();
		liquibase.setDataSource(dataSource);
		liquibase.setChangeLog("classpath:db/changelog/iam/db.changelog-iam.json");
		liquibase.setShouldRun(true);
		return liquibase;
	}

	/**
	 * Ensures JPA {@link jakarta.persistence.EntityManagerFactory} depends on {@code iamLiquibase}
	 * so Hibernate does not initialize or validate schema before IAM migrations complete.
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(EntityManagerFactoryDependsOnPostProcessor.class)
	static class IamLiquibaseEntityManagerDependencyConfiguration extends EntityManagerFactoryDependsOnPostProcessor {

		IamLiquibaseEntityManagerDependencyConfiguration() {
			super("iamLiquibase");
		}
	}

	/**
	 * Post-processes the bean factory to guarantee that {@code iamLiquibase} executes after the core
	 * {@code liquibase} master migration has finished creating foundational database tables.
	 */
	@Configuration(proxyBeanMethods = false)
	static class IamLiquibaseDependencyConfiguration implements BeanFactoryPostProcessor {

		@Override
		public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
			if (beanFactory.containsBeanDefinition("iamLiquibase") && beanFactory.containsBeanDefinition("liquibase")) {
				BeanDefinition bd = beanFactory.getBeanDefinition("iamLiquibase");
				String[] existingDependsOn = bd.getDependsOn();
				if (existingDependsOn == null || existingDependsOn.length == 0) {
					bd.setDependsOn("liquibase");
				}
				else {
					String[] combined = Arrays.copyOf(existingDependsOn, existingDependsOn.length + 1);
					combined[existingDependsOn.length] = "liquibase";
					bd.setDependsOn(combined);
				}
			}
		}
	}
}
