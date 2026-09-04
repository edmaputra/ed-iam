package io.github.edmaputra.iam.application.port.out;

/**
 * Outbound port for one-way password hashing and verification.
 *
 * @author edmaputra
 */
public interface PasswordEncoderPort {

	/**
	 * Hashes a raw password.
	 *
	 * @param rawPassword the unhashed password character sequence
	 * @return the encoded password hash
	 */
	String encode(CharSequence rawPassword);

	/**
	 * Verifies if a raw password matches an existing encoded password hash.
	 *
	 * @param rawPassword     the raw unhashed password
	 * @param encodedPassword the stored password hash
	 * @return true if the passwords match
	 */
	boolean matches(CharSequence rawPassword, String encodedPassword);
}
