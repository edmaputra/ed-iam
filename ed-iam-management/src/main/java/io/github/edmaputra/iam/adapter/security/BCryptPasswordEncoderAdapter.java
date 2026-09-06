package io.github.edmaputra.iam.adapter.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import io.github.edmaputra.iam.application.port.out.PasswordEncoderPort;

/**
 * Adapter implementing {@link PasswordEncoderPort} using Spring Security's {@link BCryptPasswordEncoder}.
 *
 * @author edmaputra
 * @since 1.0.0
 */
public class BCryptPasswordEncoderAdapter implements PasswordEncoderPort {

	private final BCryptPasswordEncoder delegate;

	/**
	 * Creates a default BCrypt password encoder adapter with default strength.
	 */
	public BCryptPasswordEncoderAdapter() {
		this.delegate = new BCryptPasswordEncoder();
	}

	/**
	 * Creates a BCrypt password encoder adapter with custom logarithmic strength.
	 *
	 * @param strength the log rounds strength (between 4 and 31)
	 */
	public BCryptPasswordEncoderAdapter(int strength) {
		this.delegate = new BCryptPasswordEncoder(strength);
	}

	@Override
	public String encode(CharSequence rawPassword) {
		return delegate.encode(rawPassword);
	}

	@Override
	public boolean matches(CharSequence rawPassword, String encodedPassword) {
		return delegate.matches(rawPassword, encodedPassword);
	}
}
