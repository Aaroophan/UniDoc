package com.unidoc.user;

import com.unidoc.auth.JwtTokenService;
import com.unidoc.common.api.PageResponse;
import com.unidoc.common.api.UserSummary;
import com.unidoc.common.exception.InvalidQueryParameterException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "UNIDOC_DB_URL=jdbc:h2:mem:unidoc-user-service;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "UNIDOC_DB_USERNAME=sa", "UNIDOC_DB_PASSWORD=",
        "UNIDOC_JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "UNIDOC_STORAGE_ROOT=target/user-service-test-uploads",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class UserServiceTests {
    @Autowired private UserService service;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JwtTokenService tokens;

    @Test
    void createsOnlyStaffAndAdminWithNormalizedEmailsAndBCryptHashes() {
        User admin = account(UserRole.ADMIN);
        for (UserRole role : new UserRole[]{UserRole.STAFF, UserRole.ADMIN}) {
            String email = "Issue7-" + UUID.randomUUID() + "@Example.TEST";
            UserSummary created = service.createManagedAccount(admin.getId(),
                    new CreateManagedAccountRequest("  Managed User  ", email, role, "Good@12345"));
            User saved = users.findById(created.id()).orElseThrow();
            assertThat(created.email()).isEqualTo(email.toLowerCase());
            assertThat(created.fullName()).isEqualTo("Managed User");
            assertThat(created.role()).isEqualTo(role);
            assertThat(created.status()).isEqualTo(UserStatus.ACTIVE);
            assertThat(created.createdAt()).isNotNull();
            assertThat(saved.getAuthVersion()).isZero();
            assertThat(saved.getPasswordHash()).startsWith("$2a$12$");
            assertThat(encoder.matches("Good@12345", saved.getPasswordHash())).isTrue();
        }
    }

    @Test
    void excludesStudentCreationAndRejectsNonAdminCallersWithinService() {
        User admin = account(UserRole.ADMIN);
        User staff = account(UserRole.STAFF);
        CreateManagedAccountRequest student = new CreateManagedAccountRequest("A Student", uniqueEmail(), UserRole.STUDENT, "Good@12345");
        assertThatThrownBy(() -> service.createManagedAccount(admin.getId(), student))
                .isInstanceOf(InvalidQueryParameterException.class);
        assertThatThrownBy(() -> service.createManagedAccount(staff.getId(),
                new CreateManagedAccountRequest("A Staff", uniqueEmail(), UserRole.STAFF, "Good@12345")))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.listUsers(staff.getId(), null, null, null, 0, 20, null, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void duplicateEmailCheckIsCaseInsensitive() {
        User admin = account(UserRole.ADMIN);
        String email = uniqueEmail();
        service.createManagedAccount(admin.getId(), new CreateManagedAccountRequest("Managed User", email, UserRole.STAFF, "Good@12345"));
        assertThatThrownBy(() -> service.createManagedAccount(admin.getId(),
                new CreateManagedAccountRequest("Another User", "  " + email.toUpperCase() + "  ", UserRole.ADMIN, "Good@12345")))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void statusChangesIncrementOnlyWhenDifferentAndSelfDeactivationFails() {
        User admin = account(UserRole.ADMIN);
        User target = account(UserRole.STAFF);
        long id = target.getId();
        assertThat(service.changeStatus(admin.getId(), id, new ChangeAccountStatusRequest(UserStatus.ACTIVE)).status())
                .isEqualTo(UserStatus.ACTIVE);
        assertThat(users.findById(id).orElseThrow().getAuthVersion()).isZero();
        service.changeStatus(admin.getId(), id, new ChangeAccountStatusRequest(UserStatus.INACTIVE));
        assertThat(users.findById(id).orElseThrow().getAuthVersion()).isEqualTo(1);
        service.changeStatus(admin.getId(), id, new ChangeAccountStatusRequest(UserStatus.INACTIVE));
        assertThat(users.findById(id).orElseThrow().getAuthVersion()).isEqualTo(1);
        service.changeStatus(admin.getId(), id, new ChangeAccountStatusRequest(UserStatus.ACTIVE));
        assertThat(users.findById(id).orElseThrow().getAuthVersion()).isEqualTo(2);
        assertThatThrownBy(() -> service.changeStatus(admin.getId(), admin.getId(), new ChangeAccountStatusRequest(UserStatus.INACTIVE)))
                .isInstanceOf(SelfDeactivationNotAllowedException.class);
        assertThat(users.findById(admin.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void passwordResetRehashesAndIncrementsVersionWithoutActivatingInactiveUser() {
        User admin = account(UserRole.ADMIN);
        User staff = account(UserRole.STAFF);
        String tokenBefore = tokens.issue(staff).value();
        service.changeStatus(admin.getId(), staff.getId(), new ChangeAccountStatusRequest(UserStatus.INACTIVE));
        service.resetPassword(admin.getId(), staff.getId(), new ResetManagedPasswordRequest("Different@1234", "Different@1234"));
        User updated = users.findById(staff.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(UserStatus.INACTIVE);
        assertThat(updated.getAuthVersion()).isEqualTo(2);
        assertThat(encoder.matches("Different@1234", updated.getPasswordHash())).isTrue();
        assertThat(encoder.matches("Good@12345", updated.getPasswordHash())).isFalse();
        assertThat(tokenBefore).isNotBlank();
    }

    @Test
    void listAppliesFiltersCombinationsPaginationAndAllowedSorting() {
        User admin = account(UserRole.ADMIN);
        String mark = UUID.randomUUID().toString().substring(0, 7);
        User alice = users.saveAndFlush(new User("alice-" + mark + "@example.test", "hash", "Marker " + mark + " Alice", UserRole.STAFF));
        User bob = users.saveAndFlush(new User("bob-" + mark + "@example.test", "hash", "Marker " + mark + " Bob", UserRole.STAFF));
        users.saveAndFlush(new User("other-" + mark + "@example.test", "hash", "Marker " + mark + " Admin", UserRole.ADMIN));
        service.changeStatus(admin.getId(), bob.getId(), new ChangeAccountStatusRequest(UserStatus.INACTIVE));
        PageResponse<UserSummary> page = service.listUsers(admin.getId(), mark, "STAFF", "ACTIVE", 0, 1, "fullName", "asc");
        assertThat(page.items()).extracting(UserSummary::id).containsExactly(alice.getId());
        assertThat(page.totalItems()).isEqualTo(1);
        assertThat(page.totalPages()).isEqualTo(1);
        PageResponse<UserSummary> allStaff = service.listUsers(admin.getId(), mark, "STAFF", null, 0, 1, "email", "asc");
        assertThat(allStaff.totalItems()).isEqualTo(2);
        assertThat(allStaff.items()).hasSize(1);
        assertThat(allStaff.first()).isTrue();
        assertThat(allStaff.last()).isFalse();
        assertThat(service.listUsers(admin.getId(), mark, "STAFF", null, 1, 1, "email", "asc").items())
                .extracting(UserSummary::id).containsExactly(bob.getId());
        assertThat(service.listUsers(admin.getId(), "nonexistent-" + mark, null, null, null, null, null, null).items()).isEmpty();
    }

    @Test
    void rejectsInvalidQueryAndEscapesSqlWildcards() {
        User admin = account(UserRole.ADMIN);
        for (String value : new String[]{"wrongSort", "passwordHash", "authVersion"}) {
            assertThatThrownBy(() -> service.listUsers(admin.getId(), null, null, null, 0, 20, value, "desc"))
                    .isInstanceOf(InvalidQueryParameterException.class);
        }
        assertThatThrownBy(() -> service.listUsers(admin.getId(), null, "bogus", null, 0, 20, null, null))
                .isInstanceOf(InvalidQueryParameterException.class);
        assertThatThrownBy(() -> service.listUsers(admin.getId(), "a".repeat(101), null, null, 0, 20, null, null))
                .isInstanceOf(InvalidQueryParameterException.class);
        assertThatThrownBy(() -> service.listUsers(admin.getId(), null, null, null, -1, 20, null, null))
                .isInstanceOf(InvalidQueryParameterException.class);
        assertThatThrownBy(() -> service.listUsers(admin.getId(), null, null, null, 0, 101, null, null))
                .isInstanceOf(InvalidQueryParameterException.class);
        assertThat(service.listUsers(admin.getId(), "%_\\", null, null, 0, 20, null, null).items()).isEmpty();
    }

    private User account(UserRole role) {
        return users.saveAndFlush(new User(uniqueEmail(), encoder.encode("Good@12345"), "Issue Seven Actor", role));
    }

    private static String uniqueEmail() {
        return "issue7-" + UUID.randomUUID() + "@example.test";
    }
}
