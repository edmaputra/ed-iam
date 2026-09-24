package io.github.edmaputra.iam.domain.model;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for domain pagination records {@link PageQuery} and {@link PagedResult}.
 *
 * @author edmaputra
 * @since 0.3.0
 */
class PaginationTest {

	@Test
	@DisplayName("Should create PageQuery with valid arguments and apply size cap")
	void shouldCreateValidPageQuery() {
		PageQuery query = new PageQuery(0, 10);
		assertThat(query.page()).isZero();
		assertThat(query.size()).isEqualTo(10);

		PageQuery defaultQuery = PageQuery.defaultPage();
		assertThat(defaultQuery.page()).isZero();
		assertThat(defaultQuery.size()).isEqualTo(20);

		PageQuery cappedQuery = PageQuery.of(2, 500);
		assertThat(cappedQuery.page()).isEqualTo(2);
		assertThat(cappedQuery.size()).isEqualTo(100);
	}

	@Test
	@DisplayName("Should throw IllegalArgumentException when PageQuery has negative page or non-positive size")
	void shouldRejectInvalidPageQuery() {
		assertThatThrownBy(() -> new PageQuery(-1, 10))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Page index must not be negative.");

		assertThatThrownBy(() -> new PageQuery(0, 0))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Page size must be greater than zero.");

		assertThatThrownBy(() -> new PageQuery(0, -5))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Page size must be greater than zero.");
	}

	@Test
	@DisplayName("Should create PagedResult, map contents, and verify navigation flags")
	void shouldCreateAndMapPagedResult() {
		List<String> items = List.of("alpha", "beta", "gamma");
		PagedResult<String> result = new PagedResult<>(items, 0, 3, 10L, 4);

		assertThat(result.content()).containsExactly("alpha", "beta", "gamma");
		assertThat(result.page()).isZero();
		assertThat(result.size()).isEqualTo(3);
		assertThat(result.totalElements()).isEqualTo(10L);
		assertThat(result.totalPages()).isEqualTo(4);
		assertThat(result.hasNext()).isTrue();
		assertThat(result.hasPrevious()).isFalse();

		// Middle page
		PagedResult<String> middle = new PagedResult<>(items, 1, 3, 10L, 4);
		assertThat(middle.hasNext()).isTrue();
		assertThat(middle.hasPrevious()).isTrue();

		// Last page
		PagedResult<String> last = new PagedResult<>(List.of("omega"), 3, 3, 10L, 4);
		assertThat(last.hasNext()).isFalse();
		assertThat(last.hasPrevious()).isTrue();

		// Mapping
		PagedResult<Integer> mapped = result.map(String::length);
		assertThat(mapped.content()).containsExactly(5, 4, 5);
		assertThat(mapped.page()).isZero();
		assertThat(mapped.totalElements()).isEqualTo(10L);
		assertThat(mapped.totalPages()).isEqualTo(4);
	}

	@Test
	@DisplayName("Should create empty PagedResult from PageQuery")
	void shouldCreateEmptyPagedResult() {
		PageQuery query = PageQuery.of(1, 15);
		PagedResult<String> empty = PagedResult.empty(query);

		assertThat(empty.content()).isEmpty();
		assertThat(empty.page()).isEqualTo(1);
		assertThat(empty.size()).isEqualTo(15);
		assertThat(empty.totalElements()).isZero();
		assertThat(empty.totalPages()).isZero();
		assertThat(empty.hasNext()).isFalse();
		assertThat(empty.hasPrevious()).isTrue();
	}

	@Test
	@DisplayName("Should reject invalid PagedResult parameters")
	void shouldRejectInvalidPagedResult() {
		assertThatThrownBy(() -> new PagedResult<>(null, 0, 10, 0L, 0))
				.isInstanceOf(NullPointerException.class);

		assertThatThrownBy(() -> new PagedResult<>(List.of(), -1, 10, 0L, 0))
				.isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> new PagedResult<>(List.of(), 0, 0, 0L, 0))
				.isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> new PagedResult<>(List.of(), 0, 10, -1L, 0))
				.isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> new PagedResult<>(List.of(), 0, 10, 0L, -1))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
