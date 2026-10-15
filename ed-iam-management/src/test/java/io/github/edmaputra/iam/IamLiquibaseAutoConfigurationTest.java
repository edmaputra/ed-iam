package io.github.edmaputra.iam;

import javax.sql.DataSource;

import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for IAM Liquibase auto-configuration and dependency coordination.
 *
 * @author edmaputra
 * @since 0.0.1
 */
class IamLiquibaseAutoConfigurationTest {

	@Test
	@DisplayName("Should configure SpringLiquibase bean pointing to IAM changelog")
	void shouldConfigureIamLiquibase() {
		DataSource mockDataSource = Mockito.mock(DataSource.class);
		IamLiquibaseAutoConfiguration configuration = new IamLiquibaseAutoConfiguration();

		SpringLiquibase liquibase = configuration.iamLiquibase(mockDataSource);

		assertThat(liquibase).isNotNull();
		assertThat(liquibase.getDataSource()).isEqualTo(mockDataSource);
		assertThat(liquibase.getChangeLog()).isEqualTo("classpath:db/changelog/iam/db.changelog-iam.json");
	}

	@Test
	@DisplayName("Should wire dependsOn dependency ordering when both beans exist")
	void shouldConfigureDependencyOrderingWhenBothBeansExist() {
		org.springframework.beans.factory.support.DefaultListableBeanFactory beanFactory =
				new org.springframework.beans.factory.support.DefaultListableBeanFactory();

		org.springframework.beans.factory.support.RootBeanDefinition iamLiquibaseBd =
				new org.springframework.beans.factory.support.RootBeanDefinition(SpringLiquibase.class);
		org.springframework.beans.factory.support.RootBeanDefinition masterLiquibaseBd =
				new org.springframework.beans.factory.support.RootBeanDefinition(SpringLiquibase.class);

		beanFactory.registerBeanDefinition("iamLiquibase", iamLiquibaseBd);
		beanFactory.registerBeanDefinition("liquibase", masterLiquibaseBd);

		IamLiquibaseAutoConfiguration.IamLiquibaseDependencyConfiguration postProcessor =
				new IamLiquibaseAutoConfiguration.IamLiquibaseDependencyConfiguration();

		postProcessor.postProcessBeanFactory(beanFactory);

		assertThat(iamLiquibaseBd.getDependsOn()).containsExactly("liquibase");

		// Test branch when existing dependsOn is non-empty
		iamLiquibaseBd.setDependsOn("customPreMigrationBean");
		postProcessor.postProcessBeanFactory(beanFactory);
		assertThat(iamLiquibaseBd.getDependsOn()).containsExactly("customPreMigrationBean", "liquibase");
	}

	@Test
	@DisplayName("Should omit iamLiquibase when ed-iam.liquibase.enabled is false")
	void shouldOmitIamLiquibaseWhenDisabled() {
		new org.springframework.boot.test.context.runner.ApplicationContextRunner()
				.withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(IamLiquibaseAutoConfiguration.class))
				.withBean(DataSource.class, () -> Mockito.mock(DataSource.class))
				.withPropertyValues("ed-iam.liquibase.enabled=false")
				.run(context -> {
					assertThat(context).doesNotHaveBean("iamLiquibase");
				});
	}

	@Test
	@DisplayName("Should back off when custom iamLiquibase bean is already defined")
	void shouldBackOffWhenCustomIamLiquibaseBeanExists() {
		SpringLiquibase customLiquibase = new SpringLiquibase();
		customLiquibase.setShouldRun(false);
		new org.springframework.boot.test.context.runner.ApplicationContextRunner()
				.withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(IamLiquibaseAutoConfiguration.class))
				.withBean(DataSource.class, () -> Mockito.mock(DataSource.class))
				.withBean("iamLiquibase", SpringLiquibase.class, () -> customLiquibase)
				.run(context -> {
					assertThat(context).hasBean("iamLiquibase");
					assertThat(context.getBean("iamLiquibase")).isSameAs(customLiquibase);
				});
	}
}
