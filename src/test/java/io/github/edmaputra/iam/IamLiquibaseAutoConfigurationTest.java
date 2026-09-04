package io.github.edmaputra.iam;

import javax.sql.DataSource;

import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

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
}
