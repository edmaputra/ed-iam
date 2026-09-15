package io.github.edmaputra.iam.application.port.in;

import io.github.edmaputra.iam.domain.security.CurrentActor;
import io.github.edmaputra.iam.application.model.TokenResponse;
import io.github.edmaputra.iam.application.model.UserProfileResponse;

/**
 * Inbound port defining authentication use cases including user login, token refresh, and profile retrieval.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public interface AuthenticateUserUseCase {

	/**
	 * Authenticates user credentials and issues signed access and refresh tokens.
	 *
	 * @param command the login command containing user credentials and optional tenant ID
	 * @return the token response containing access/refresh tokens and user profile
	 */
	TokenResponse login(LoginCommand command);

	/**
	 * Exchanges a valid refresh token for a newly issued access token.
	 *
	 * @param command the refresh token command
	 * @return the token response containing refreshed tokens
	 */
	TokenResponse refreshToken(RefreshTokenCommand command);

	/**
	 * Retrieves the current authenticated actor's profile and effective permissions.
	 *
	 * @param actor the current security context actor
	 * @return the user profile response
	 */
	UserProfileResponse getMe(CurrentActor actor);

	/**
	 * Switches the active tenant context for the current authenticated user and issues new tokens.
	 *
	 * @param command the switch tenant command
	 * @return the token response containing new access and refresh tokens scoped to the target tenant
	 */
	TokenResponse switchTenant(SwitchTenantCommand command);
}
