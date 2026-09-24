package io.github.edmaputra.iam.domain.model;

/**
 * Value object representing pagination request parameters in a framework-agnostic way.
 *
 * @param page 0-based page index
 * @param size the size of the page to be returned
 * @author edmaputra
 * @since 0.3.0
 */
public record PageQuery(int page, int size) {

	private static final int DEFAULT_PAGE = 0;
	private static final int DEFAULT_SIZE = 20;
	private static final int MAX_SIZE = 100;

	public PageQuery {
		if (page < 0) {
			throw new IllegalArgumentException("Page index must not be negative.");
		}
		if (size <= 0) {
			throw new IllegalArgumentException("Page size must be greater than zero.");
		}
	}

	/**
	 * Factory method to create a {@link PageQuery} capped by the maximum allowed page size.
	 *
	 * @param page 0-based page index
	 * @param size the requested page size
	 * @return a validated {@link PageQuery}
	 */
	public static PageQuery of(int page, int size) {
		return new PageQuery(page, Math.min(size, MAX_SIZE));
	}

	/**
	 * Creates a default {@link PageQuery} with page 0 and size 20.
	 *
	 * @return default {@link PageQuery}
	 */
	public static PageQuery defaultPage() {
		return new PageQuery(DEFAULT_PAGE, DEFAULT_SIZE);
	}
}
