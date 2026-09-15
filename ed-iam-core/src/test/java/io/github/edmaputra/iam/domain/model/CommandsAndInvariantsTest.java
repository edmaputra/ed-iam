package io.github.edmaputra.iam.domain.model;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.github.edmaputra.iam.adapter.security.SecurityContextCurrentActor;
import io.github.edmaputra.iam.domain.security.CurrentActor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.edmaputra.iam.application.port.in.CreateScopeNodeCommand;
import io.github.edmaputra.iam.application.port.in.DeleteScopeNodeCommand;
import io.github.edmaputra.iam.application.port.in.GroupCommands.AssignGroupRoleCommand;
import io.github.edmaputra.iam.application.port.in.GroupCommands.CreateGroupCommand;
import io.github.edmaputra.iam.application.port.in.GroupCommands.UpdateGroupCommand;
import io.github.edmaputra.iam.application.port.in.LoginCommand;
import io.github.edmaputra.iam.application.port.in.MoveScopeNodeCommand;
import io.github.edmaputra.iam.application.port.in.RefreshTokenCommand;
import io.github.edmaputra.iam.application.port.in.RoleCommands.CreateRoleCommand;
import io.github.edmaputra.iam.application.port.in.RoleCommands.UpdateRoleCommand;
import io.github.edmaputra.iam.application.port.in.SwitchTenantCommand;
import io.github.edmaputra.iam.application.port.in.UpdateScopeNodeCommand;
import io.github.edmaputra.iam.application.port.in.UserCommands.AssignUserRoleCommand;
import io.github.edmaputra.iam.application.port.in.UserCommands.ChangeUserStatusCommand;
import io.github.edmaputra.iam.application.port.in.UserCommands.CreateUserCommand;
import io.github.edmaputra.iam.application.port.in.UserCommands.UpdateUserCommand;
import io.github.edmaputra.iam.domain.auth.AuthenticatedIdentity;
import io.github.edmaputra.iam.domain.context.ActorType;
import io.github.edmaputra.iam.domain.context.OperationContext;
import io.github.edmaputra.iam.domain.exception.AccessDeniedException;
import io.github.edmaputra.iam.domain.exception.AuthenticationException;
import io.github.edmaputra.iam.domain.exception.GroupNotFoundException;
import io.github.edmaputra.iam.domain.exception.RoleNotFoundException;
import io.github.edmaputra.iam.domain.exception.ScopeNodeNotFoundException;
import io.github.edmaputra.iam.domain.exception.UserNotFoundException;
import io.github.edmaputra.iam.domain.tenancy.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests verifying domain commands, value objects, exceptions, and invariants in {@code ed-iam-core}.
 *
 * @author edmaputra
 * @since 1.0.0
 */
class CommandsAndInvariantsTest {

	@Test
	@DisplayName("Should validate UserCommands invariants")
	void shouldValidateUserCommands() {
		UserId userId = UserId.generate();
		RoleId roleId = RoleId.generate();
		TenantId tenantId = TenantId.generate();
		ScopeNodeId scopeId = ScopeNodeId.generate();

		// CreateUserCommand
		assertThatThrownBy(() -> new CreateUserCommand(null, "p", "Name", false))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new CreateUserCommand("  ", "p", "Name", false))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new CreateUserCommand("a@b.com", "p", null, false))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new CreateUserCommand("a@b.com", "p", "   ", false))
				.isInstanceOf(IllegalArgumentException.class);

		CreateUserCommand create = new CreateUserCommand("a@b.com", "p", "Name", true);
		assertThat(create.email()).isEqualTo("a@b.com");
		assertThat(create.fullName()).isEqualTo("Name");
		assertThat(create.platformSuperAdmin()).isTrue();

		// UpdateUserCommand
		assertThatThrownBy(() -> new UpdateUserCommand(null, "Name")).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new UpdateUserCommand(userId, null)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new UpdateUserCommand(userId, "  ")).isInstanceOf(IllegalArgumentException.class);
		UpdateUserCommand update = new UpdateUserCommand(userId, "New Name");
		assertThat(update.userId()).isEqualTo(userId);
		assertThat(update.fullName()).isEqualTo("New Name");

		// ChangeUserStatusCommand
		assertThatThrownBy(() -> new ChangeUserStatusCommand(null, UserStatus.ACTIVE)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new ChangeUserStatusCommand(userId, null)).isInstanceOf(NullPointerException.class);
		ChangeUserStatusCommand statusCmd = new ChangeUserStatusCommand(userId, UserStatus.SUSPENDED);
		assertThat(statusCmd.status()).isEqualTo(UserStatus.SUSPENDED);

		// AssignUserRoleCommand
		assertThatThrownBy(() -> new AssignUserRoleCommand(null, roleId, tenantId, scopeId)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new AssignUserRoleCommand(userId, null, tenantId, scopeId)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new AssignUserRoleCommand(userId, roleId, null, scopeId)).isInstanceOf(NullPointerException.class);
		AssignUserRoleCommand assign = new AssignUserRoleCommand(userId, roleId, tenantId, scopeId);
		assertThat(assign.scopeNodeId()).isEqualTo(scopeId);
	}

	@Test
	@DisplayName("Should validate RoleCommands invariants")
	void shouldValidateRoleCommands() {
		RoleId roleId = RoleId.generate();
		TenantId tenantId = TenantId.generate();

		// CreateRoleCommand
		assertThatThrownBy(() -> new CreateRoleCommand(null, "C", "N", "D", Set.of())).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new CreateRoleCommand(tenantId, null, "N", "D", Set.of())).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new CreateRoleCommand(tenantId, "  ", "N", "D", Set.of())).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new CreateRoleCommand(tenantId, "C", null, "D", Set.of())).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new CreateRoleCommand(tenantId, "C", "  ", "D", Set.of())).isInstanceOf(IllegalArgumentException.class);

		CreateRoleCommand createRole = new CreateRoleCommand(tenantId, "CODE", "Name", "Desc", null);
		assertThat(createRole.permissions()).isEmpty();

		// UpdateRoleCommand
		assertThatThrownBy(() -> new UpdateRoleCommand(null, "N", "D", Set.of())).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new UpdateRoleCommand(roleId, null, "D", Set.of())).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new UpdateRoleCommand(roleId, "  ", "D", Set.of())).isInstanceOf(IllegalArgumentException.class);

		UpdateRoleCommand updateRole = new UpdateRoleCommand(roleId, "Name", "Desc", null);
		assertThat(updateRole.permissions()).isEmpty();
	}

	@Test
	@DisplayName("Should validate GroupCommands invariants")
	void shouldValidateGroupCommands() {
		GroupId groupId = GroupId.generate();
		RoleId roleId = RoleId.generate();
		TenantId tenantId = TenantId.generate();
		ScopeNodeId scopeId = ScopeNodeId.generate();

		// CreateGroupCommand
		assertThatThrownBy(() -> new CreateGroupCommand(null, "C", "N", "D", null)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new CreateGroupCommand(tenantId, null, "N", "D", null)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new CreateGroupCommand(tenantId, "  ", "N", "D", null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new CreateGroupCommand(tenantId, "C", null, "D", null)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new CreateGroupCommand(tenantId, "C", "  ", "D", null)).isInstanceOf(IllegalArgumentException.class);

		CreateGroupCommand createGroup = new CreateGroupCommand(tenantId, "CODE", "Name", "Desc", "idp-group");
		assertThat(createGroup.externalIdpGroupName()).isEqualTo("idp-group");

		// UpdateGroupCommand
		assertThatThrownBy(() -> new UpdateGroupCommand(null, "N", "D", null)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new UpdateGroupCommand(groupId, null, "D", null)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new UpdateGroupCommand(groupId, "  ", "D", null)).isInstanceOf(IllegalArgumentException.class);

		UpdateGroupCommand updateGroup = new UpdateGroupCommand(groupId, "Name", "Desc", "idp-group");
		assertThat(updateGroup.description()).isEqualTo("Desc");

		// AssignGroupRoleCommand
		assertThatThrownBy(() -> new AssignGroupRoleCommand(null, roleId, tenantId, scopeId)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new AssignGroupRoleCommand(groupId, null, tenantId, scopeId)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new AssignGroupRoleCommand(groupId, roleId, null, scopeId)).isInstanceOf(NullPointerException.class);

		AssignGroupRoleCommand assign = new AssignGroupRoleCommand(groupId, roleId, tenantId, scopeId);
		assertThat(assign.groupId()).isEqualTo(groupId);
	}

	@Test
	@DisplayName("Should validate ScopeCommands invariants")
	void shouldValidateScopeCommands() {
		TenantId tenantId = TenantId.generate();
		ScopeNodeId scopeId = ScopeNodeId.generate();
		ScopeNodeId parentId = ScopeNodeId.generate();

		// CreateScopeNodeCommand
		assertThatThrownBy(() -> CreateScopeNodeCommand.root(null, "C", "N")).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> CreateScopeNodeCommand.root(tenantId, " ", "N")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> CreateScopeNodeCommand.root(tenantId, "C", " ")).isInstanceOf(IllegalArgumentException.class);

		CreateScopeNodeCommand root = CreateScopeNodeCommand.root(tenantId, "C", "N");
		assertThat(root.parentId()).isNull();

		CreateScopeNodeCommand child = CreateScopeNodeCommand.child(tenantId, parentId, "CHILD", "Child");
		assertThat(child.parentId()).isEqualTo(parentId);

		// UpdateScopeNodeCommand
		assertThatThrownBy(() -> new UpdateScopeNodeCommand(null, scopeId, "C", "N")).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new UpdateScopeNodeCommand(tenantId, null, "C", "N")).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new UpdateScopeNodeCommand(tenantId, scopeId, " ", "N")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new UpdateScopeNodeCommand(tenantId, scopeId, "C", " ")).isInstanceOf(IllegalArgumentException.class);

		UpdateScopeNodeCommand updateCmd = new UpdateScopeNodeCommand(tenantId, scopeId, "CODE", "Name");
		assertThat(updateCmd.id()).isEqualTo(scopeId);
		assertThat(updateCmd.code()).isEqualTo("CODE");
		assertThat(updateCmd.name()).isEqualTo("Name");

		// MoveScopeNodeCommand
		assertThatThrownBy(() -> new MoveScopeNodeCommand(null, scopeId, parentId)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new MoveScopeNodeCommand(tenantId, null, parentId)).isInstanceOf(NullPointerException.class);
		MoveScopeNodeCommand move = new MoveScopeNodeCommand(tenantId, scopeId, parentId);
		assertThat(move.id()).isEqualTo(scopeId);
		assertThat(move.newParentId()).isEqualTo(parentId);
		assertThat(move.optionalNewParentId()).contains(parentId);

		// DeleteScopeNodeCommand
		assertThatThrownBy(() -> new DeleteScopeNodeCommand(null, scopeId)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new DeleteScopeNodeCommand(tenantId, null)).isInstanceOf(NullPointerException.class);
		DeleteScopeNodeCommand del = new DeleteScopeNodeCommand(tenantId, scopeId);
		assertThat(del.id()).isEqualTo(scopeId);
	}

	@Test
	@DisplayName("Should validate other commands, auth, and operation context")
	void shouldValidateOtherCommandsAndContext() {
		TenantId tenantId = TenantId.generate();
		UserId userId = UserId.generate();

		// SwitchTenantCommand
		io.github.edmaputra.iam.adapter.security.SecurityContextCurrentActor actor = new io.github.edmaputra.iam.adapter.security.SecurityContextCurrentActor(
				userId.value(), "a@b.com", null, false, false, Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
		assertThatThrownBy(() -> new SwitchTenantCommand(null, tenantId)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new SwitchTenantCommand(actor, null)).isInstanceOf(NullPointerException.class);
		SwitchTenantCommand st = new SwitchTenantCommand(actor, tenantId);
		assertThat(st.targetTenantId()).isEqualTo(tenantId);
		assertThat(st.currentActor()).isEqualTo(actor);

		// RefreshTokenCommand
		assertThatThrownBy(() -> new RefreshTokenCommand(null)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new RefreshTokenCommand("  ")).isInstanceOf(IllegalArgumentException.class);
		RefreshTokenCommand rt = new RefreshTokenCommand("token123");
		assertThat(rt.refreshToken()).isEqualTo("token123");

		// LoginCommand with and without tenant
		LoginCommand l1 = new LoginCommand("user@test.org", "pass", tenantId);
		assertThat(l1.tenantId()).isEqualTo(tenantId);
		LoginCommand l2 = LoginCommand.of("user@test.org", "pass");
		assertThat(l2.tenantId()).isNull();

		// AuthenticatedIdentity
		assertThatThrownBy(() -> new AuthenticatedIdentity(null, "a@b.com", "N", false, ProviderType.LOCAL))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new AuthenticatedIdentity(userId, null, "N", false, ProviderType.LOCAL))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new AuthenticatedIdentity(userId, "a@b.com", null, false, ProviderType.LOCAL))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new AuthenticatedIdentity(userId, "a@b.com", "N", false, null))
				.isInstanceOf(NullPointerException.class);

		AuthenticatedIdentity identity = new AuthenticatedIdentity(userId, "a@b.com", "N", false, ProviderType.LOCAL);
		assertThat(identity.providerType()).isEqualTo(ProviderType.LOCAL);
		assertThat(identity.fullName()).isEqualTo("N");

		// UserIdentity and ProviderType
		UserIdentity ui = UserIdentity.create(userId, ProviderType.OIDC_GENERIC, "sub-123", "https://accounts.example.com");
		assertThat(ui.getProviderType()).isEqualTo(ProviderType.OIDC_GENERIC);
		assertThat(ui.getExternalSubjectId()).isEqualTo("sub-123");
		assertThat(ui.optionalIssuerUrl()).contains("https://accounts.example.com");

		UserIdentity uiNoIssuer = UserIdentity.create(userId, ProviderType.OIDC_AZURE, "sub-456", null);
		assertThat(uiNoIssuer.optionalIssuerUrl()).isEmpty();

		// OperationContext
		assertThatThrownBy(() -> new OperationContext(null, ActorType.USER, null, "corr")).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new OperationContext("  ", ActorType.USER, null, "corr")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new OperationContext("actor", null, null, "corr")).isInstanceOf(NullPointerException.class);
		OperationContext sys = OperationContext.system();
		assertThat(sys.actor()).isEqualTo("system");
		assertThat(sys.actorType()).isEqualTo(ActorType.SYSTEM);
		assertThat(sys.optionalTenantId()).isEmpty();
		assertThat(sys.optionalTenantUuid()).isEmpty();
		assertThat(sys.optionalCorrelationId()).isEmpty();

		TenantId opTenantId = TenantId.generate();
		OperationContext sysTenant = OperationContext.system(opTenantId);
		assertThat(sysTenant.actorType()).isEqualTo(ActorType.SYSTEM);
		assertThat(sysTenant.optionalTenantId()).contains(opTenantId);
		assertThat(sysTenant.optionalTenantUuid()).contains(opTenantId.value());

		OperationContext sysTenantUuid = OperationContext.system(opTenantId.value());
		assertThat(sysTenantUuid.optionalTenantId()).contains(opTenantId);

		OperationContext custom = OperationContext.of("custom-actor", "corr-99");
		assertThat(custom.actor()).isEqualTo("custom-actor");
		assertThat(custom.actorType()).isEqualTo(ActorType.USER);
		assertThat(custom.optionalCorrelationId()).contains("corr-99");

		OperationContext single = OperationContext.of("single-actor");
		assertThat(single.actor()).isEqualTo("single-actor");
		assertThat(single.actorType()).isEqualTo(ActorType.USER);
		assertThat(single.optionalCorrelationId()).isEmpty();

		OperationContext userCtx = OperationContext.user("john", opTenantId, "corr-1");
		assertThat(userCtx.actor()).isEqualTo("john");
		assertThat(userCtx.actorType()).isEqualTo(ActorType.USER);
		assertThat(userCtx.optionalTenantId()).contains(opTenantId);
		assertThat(userCtx.optionalCorrelationId()).contains("corr-1");

		OperationContext userUuidCtx = OperationContext.user("john", opTenantId.value(), "corr-1");
		assertThat(userUuidCtx.optionalTenantId()).contains(opTenantId);

		OperationContext userNoCorr = OperationContext.user("john", opTenantId);
		assertThat(userNoCorr.optionalCorrelationId()).isEmpty();

		OperationContext userUuidNoCorr = OperationContext.user("john", opTenantId.value());
		assertThat(userUuidNoCorr.optionalTenantId()).contains(opTenantId);

		OperationContext machineCtx = OperationContext.machine("service-client", opTenantId, "corr-2");
		assertThat(machineCtx.actor()).isEqualTo("service-client");
		assertThat(machineCtx.actorType()).isEqualTo(ActorType.MACHINE);
		assertThat(machineCtx.optionalTenantId()).contains(opTenantId);
		assertThat(machineCtx.optionalCorrelationId()).contains("corr-2");

		OperationContext machineUuidCtx = OperationContext.machine("service-client", opTenantId.value(), "corr-2");
		assertThat(machineUuidCtx.optionalTenantId()).contains(opTenantId);

		OperationContext machineNoCorr = OperationContext.machine("service-client", opTenantId);
		assertThat(machineNoCorr.optionalCorrelationId()).isEmpty();

		OperationContext machineUuidNoCorr = OperationContext.machine("service-client", opTenantId.value());
		assertThat(machineUuidNoCorr.optionalTenantId()).contains(opTenantId);

		OperationContext ofAll = OperationContext.of("caller", ActorType.MACHINE, opTenantId, "corr-3");
		assertThat(ofAll.actorType()).isEqualTo(ActorType.MACHINE);

		OperationContext ofUuid = OperationContext.of("caller", ActorType.MACHINE, opTenantId.value(), "corr-3");
		assertThat(ofUuid.optionalTenantId()).contains(opTenantId);

		OperationContext ofTypeTenant = OperationContext.of("caller", ActorType.MACHINE, opTenantId);
		assertThat(ofTypeTenant.optionalCorrelationId()).isEmpty();

		OperationContext ofTypeUuid = OperationContext.of("caller", ActorType.MACHINE, opTenantId.value());
		assertThat(ofTypeUuid.optionalTenantId()).contains(opTenantId);

		OperationContext ofType = OperationContext.of("caller", ActorType.MACHINE);
		assertThat(ofType.optionalTenantId()).isEmpty();
	}

	@Test
	@DisplayName("Should construct domain exceptions properly")
	void shouldConstructDomainExceptions() {
		UserId userId = UserId.generate();
		GroupId groupId = GroupId.generate();
		RoleId roleId = RoleId.generate();
		ScopeNodeId scopeId = ScopeNodeId.generate();

		assertThat(new AccessDeniedException("denied").getMessage()).isEqualTo("denied");

		assertThat(new UserNotFoundException(userId).getMessage()).contains(userId.value().toString());
		assertThat(new UserNotFoundException("user@email.com").getMessage()).contains("user@email.com");

		assertThat(new GroupNotFoundException(groupId).getMessage()).contains(groupId.value().toString());
		assertThat(new GroupNotFoundException("SURGERY").getMessage()).contains("SURGERY");

		assertThat(new ScopeNodeNotFoundException(scopeId).getMessage()).contains(scopeId.value().toString());
		assertThat(new ScopeNodeNotFoundException("/path/").getMessage()).contains("/path/");

		assertThat(new RoleNotFoundException(roleId).getMessage()).contains(roleId.value().toString());
		assertThat(new RoleNotFoundException("ADMIN").getMessage()).contains("ADMIN");

		assertThat(new AuthenticationException("auth-error").getMessage()).isEqualTo("auth-error");
		assertThat(new AuthenticationException("auth-error", new RuntimeException()).getCause()).isNotNull();
	}

	@Test
	@DisplayName("Should cover all branches of CurrentActor default methods")
	void shouldCoverCurrentActorDefaultMethods() {
		UUID targetScope = UUID.randomUUID();
		UUID otherScope = UUID.randomUUID();

		// Superadmin
		CurrentActor superAdmin = new io.github.edmaputra.iam.adapter.security.SecurityContextCurrentActor(
				UUID.randomUUID(), "sa@test.org", null, true, false, Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
		assertThat(superAdmin.hasPermission("ANY_PERM")).isTrue();
		assertThat(superAdmin.canAccessScope(targetScope)).isTrue();
		assertThat(superAdmin.canAccessScope(null)).isTrue();

		// Tenant-wide user
		CurrentActor tenantWide = new io.github.edmaputra.iam.adapter.security.SecurityContextCurrentActor(
				UUID.randomUUID(), "tw@test.org", UUID.randomUUID(), false, true, Set.of(), Set.of(), Set.of("READ"), Set.of(), Set.of());
		assertThat(tenantWide.hasPermission("READ")).isTrue();
		assertThat(tenantWide.hasPermission("WRITE")).isFalse();
		assertThat(tenantWide.canAccessScope(targetScope)).isTrue();

		// Scoped user
		CurrentActor scopedUser = new io.github.edmaputra.iam.adapter.security.SecurityContextCurrentActor(
				UUID.randomUUID(), "sc@test.org", UUID.randomUUID(), false, false, Set.of(), Set.of(), Set.of("READ"), Set.of(targetScope), Set.of("/path/"));
		assertThat(scopedUser.canAccessScope(targetScope)).isTrue();
		assertThat(scopedUser.canAccessScope(otherScope)).isFalse();
		assertThat(scopedUser.canAccessScope(null)).isFalse();

		// Null permissions/scopes defensive branch
		CurrentActor nullCollectionsActor = new CurrentActor() {
			@Override public UUID userId() { return UUID.randomUUID(); }
			@Override public String email() { return "e@t.org"; }
			@Override public UUID tenantId() { return null; }
			@Override public boolean isPlatformSuperAdmin() { return false; }
			@Override public boolean isTenantWide() { return false; }
			@Override public Set<String> groups() { return null; }
			@Override public Set<String> roles() { return null; }
			@Override public Set<String> permissions() { return null; }
			@Override public Set<UUID> accessibleScopeNodeIds() { return null; }
		};
		assertThat(nullCollectionsActor.hasPermission("READ")).isFalse();
		assertThat(nullCollectionsActor.canAccessScope(targetScope)).isFalse();
	}

	@Test
	@DisplayName("Should cover EffectiveAccess, UserProfileResponse, and TokenResponse branches")
	void shouldCoverModelResponseBranches() {
		UserId userId = UserId.generate();
		TenantId tenantId = TenantId.generate();
		UUID scopeId = UUID.randomUUID();

		// EffectiveAccess with populated collections
		io.github.edmaputra.iam.application.model.EffectiveAccess access = new io.github.edmaputra.iam.application.model.EffectiveAccess(
				userId, "u@test.org", tenantId, false, false,
				Set.of(tenantId), Set.of("G1"), Set.of("R1"), Set.of("P1"), Set.of(scopeId), Set.of("/p/"));
		assertThat(access.availableTenants()).contains(tenantId);
		assertThat(access.groups()).contains("G1");

		// Secondary constructor
		io.github.edmaputra.iam.application.model.EffectiveAccess secondary = new io.github.edmaputra.iam.application.model.EffectiveAccess(
				userId, "u@test.org", tenantId, false, false,
				Set.of("G1"), Set.of("R1"), Set.of("P1"), Set.of(scopeId), Set.of("/p/"));
		assertThat(secondary.availableTenants()).isEmpty();

		// UserProfileResponse with populated collections
		io.github.edmaputra.iam.application.model.UserProfileResponse profile = new io.github.edmaputra.iam.application.model.UserProfileResponse(
				userId.value(), "u@test.org", "Full Name", tenantId.value(), false, false,
				Set.of(tenantId.value()), Set.of("G1"), Set.of("R1"), Set.of("P1"), Set.of(scopeId), Set.of("/p/"));
		assertThat(profile.groups()).contains("G1");

		// TokenResponse with null, blank, and custom tokenType
		io.github.edmaputra.iam.application.model.TokenResponse t1 = new io.github.edmaputra.iam.application.model.TokenResponse("acc", "ref", null, 3600, profile);
		assertThat(t1.tokenType()).isEqualTo("Bearer");

		io.github.edmaputra.iam.application.model.TokenResponse t2 = new io.github.edmaputra.iam.application.model.TokenResponse("acc", "ref", "   ", 3600, profile);
		assertThat(t2.tokenType()).isEqualTo("Bearer");

		io.github.edmaputra.iam.application.model.TokenResponse t3 = io.github.edmaputra.iam.application.model.TokenResponse.of("acc", "ref", 3600, profile);
		assertThat(t3.tokenType()).isEqualTo("Bearer");

		io.github.edmaputra.iam.application.model.TokenResponse t4 = new io.github.edmaputra.iam.application.model.TokenResponse("acc", "ref", "CustomAuth", 3600, profile);
		assertThat(t4.tokenType()).isEqualTo("CustomAuth");
	}

	@Test
	@DisplayName("Should cover OidcAuthCredentials and PasswordAuthCredentials branches")
	void shouldCoverAuthCredentialsBranches() {
		TenantId tenantId = TenantId.generate();

		// OidcAuthCredentials with null fullName -> defaults to email
		io.github.edmaputra.iam.domain.auth.OidcAuthCredentials o1 = new io.github.edmaputra.iam.domain.auth.OidcAuthCredentials(
				"token", "oidc@test.org", "sub-1", null, "https://idp.org", List.of("g1"), tenantId);
		assertThat(o1.fullName()).isEqualTo("oidc@test.org");
		assertThat(o1.optionalIdToken()).contains("token");
		assertThat(o1.optionalIssuerUrl()).contains("https://idp.org");
		assertThat(o1.optionalTenantId()).contains(tenantId);
		assertThat(o1.credentialType()).isEqualTo(io.github.edmaputra.iam.domain.auth.AuthCredentialType.OIDC_TOKEN);

		// OidcAuthCredentials with blank fullName -> defaults to email, null groups -> empty
		io.github.edmaputra.iam.domain.auth.OidcAuthCredentials o2 = new io.github.edmaputra.iam.domain.auth.OidcAuthCredentials(
				null, "oidc@test.org", "sub-1", "   ", null, null, null);
		assertThat(o2.fullName()).isEqualTo("oidc@test.org");
		assertThat(o2.externalGroups()).isEmpty();

		// PasswordAuthCredentials blank validation
		assertThatThrownBy(() -> new io.github.edmaputra.iam.domain.auth.PasswordAuthCredentials("  ", "pass"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new io.github.edmaputra.iam.domain.auth.PasswordAuthCredentials("user@test.org", "   "))
				.isInstanceOf(IllegalArgumentException.class);
		io.github.edmaputra.iam.domain.auth.PasswordAuthCredentials p = new io.github.edmaputra.iam.domain.auth.PasswordAuthCredentials("u@t.org", "pass");
		assertThat(p.credentialType()).isEqualTo(io.github.edmaputra.iam.domain.auth.AuthCredentialType.PASSWORD);

		// ScopeTreeNode from null or empty
		assertThat(io.github.edmaputra.iam.application.model.ScopeTreeNode.from(null)).isEmpty();
		assertThat(io.github.edmaputra.iam.application.model.ScopeTreeNode.from(java.util.List.of())).isEmpty();
	}
}
