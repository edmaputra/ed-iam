package io.github.edmaputra.iam.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Value object representing a paginated collection of domain entities.
 *
 * @param <T>           the type of elements in the page
 * @param content       the elements on the current page
 * @param page          the 0-based page index
 * @param size          the page size
 * @param totalElements the total count of elements across all pages
 * @param totalPages    the total number of pages
 * @author edmaputra
 * @since 0.3.0
 */
public record PagedResult<T>(
		List<T> content,
		int page,
		int size,
		long totalElements,
		int totalPages) {

	public PagedResult {
		Objects.requireNonNull(content, "Content must not be null.");
		content = List.copyOf(content);
		if (page < 0) {
			throw new IllegalArgumentException("Page index must not be negative.");
		}
		if (size <= 0) {
			throw new IllegalArgumentException("Page size must be greater than zero.");
		}
		if (totalElements < 0) {
			throw new IllegalArgumentException("Total elements must not be negative.");
		}
		if (totalPages < 0) {
			throw new IllegalArgumentException("Total pages must not be negative.");
		}
	}

	/**
	 * Creates an empty {@link PagedResult} corresponding to a given {@link PageQuery}.
	 *
	 * @param <T>   the type of elements
	 * @param query the page query
	 * @return an empty paginated result
	 */
	public static <T> PagedResult<T> empty(PageQuery query) {
		Objects.requireNonNull(query, "PageQuery must not be null.");
		return new PagedResult<>(List.of(), query.page(), query.size(), 0L, 0);
	}

	/**
	 * Transforms the content of this page using the provided mapper function.
	 *
	 * @param <R>    the target type
	 * @param mapper the mapping function applied to each element
	 * @return a new {@link PagedResult} containing mapped elements
	 */
	public <R> PagedResult<R> map(Function<? super T, R> mapper) {
		Objects.requireNonNull(mapper, "Mapper must not be null.");
		List<R> mappedContent = content.stream().map(mapper).toList();
		return new PagedResult<>(mappedContent, page, size, totalElements, totalPages);
	}

	/**
	 * Returns whether there is a next page available.
	 *
	 * @return true if current page index is less than total pages minus one
	 */
	public boolean hasNext() {
		return page + 1 < totalPages;
	}

	/**
	 * Returns whether there is a previous page available.
	 *
	 * @return true if current page index is greater than zero
	 */
	public boolean hasPrevious() {
		return page > 0;
	}
}
