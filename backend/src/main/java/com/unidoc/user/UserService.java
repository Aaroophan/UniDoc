package com.unidoc.user;

import com.unidoc.common.api.CommonDtoMapper;
import com.unidoc.common.api.PageResponse;
import com.unidoc.common.api.UserSummary;
import com.unidoc.common.exception.InvalidQueryParameterException;
import com.unidoc.common.persistence.Normalization;
import com.unidoc.common.web.QueryParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private final UserRepository users;
    private final PasswordEncoder encoder;

    public UserService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserSummary> listUsers(
            long actorId, String q, String role, String status,
            Integer page, Integer size, String sort, String direction
    ) {
        requireAdmin(actorId);
        String text = QueryParameters.freeText(q, 100);
        UserRole filterRole = QueryParameters.enumValue(role, UserRole.class, "role");
        UserStatus filterStatus = QueryParameters.enumValue(status, UserStatus.class, "status");
        Pageable paging = QueryParameters.pageable(
                page, size, sort, direction, UserSortProperties.MAPPINGS,
                UserSortProperties.DEFAULT, Sort.Direction.DESC
        );
        return PageResponse.from(
                users.findAll(UserSpecifications.filtered(text, filterRole, filterStatus), paging),
                CommonDtoMapper::toUserSummary
        );
    }

    @Transactional
    public UserSummary createManagedAccount(long actorId, CreateManagedAccountRequest request) {
        requireAdmin(actorId);
        if (request.role() != UserRole.STAFF && request.role() != UserRole.ADMIN) {
            throw new InvalidQueryParameterException("role must be STAFF or ADMIN.");
        }
        String email = Normalization.email(request.email());
        if (users.existsByEmail(email)) throw new DuplicateEmailException();
        User account = new User(email, encoder.encode(request.password()), request.fullName().trim(),
                request.role(), UserStatus.ACTIVE);
        User saved = users.saveAndFlush(account);
        log.info("Managed account created actorId={} userId={} role={}", actorId, saved.getId(), saved.getRole());
        return CommonDtoMapper.toUserSummary(saved);
    }

    @Transactional
    public UserSummary changeStatus(long actorId, long userId, ChangeAccountStatusRequest request) {
        requireAdmin(actorId);
        User account = users.findByIdForUpdate(userId).orElseThrow(UserNotFoundException::new);
        UserStatus next = request.status();
        if (next == null) throw new InvalidQueryParameterException("status is required.");
        if (actorId == userId && next == UserStatus.INACTIVE) throw new SelfDeactivationNotAllowedException();
        if (account.changeStatus(next)) {
            users.saveAndFlush(account);
            log.info("Account status changed actorId={} userId={} status={}", actorId, userId, next);
        }
        return CommonDtoMapper.toUserSummary(account);
    }

    @Transactional
    public void resetPassword(long actorId, long userId, ResetManagedPasswordRequest request) {
        requireAdmin(actorId);
        User account = users.findByIdForUpdate(userId).orElseThrow(UserNotFoundException::new);
        account.resetPasswordHash(encoder.encode(request.newPassword()));
        users.saveAndFlush(account);
        log.info("Account password reset actorId={} targetUserId={}", actorId, userId);
    }

    private void requireAdmin(long actorId) {
        User actor = users.findById(actorId).orElseThrow(
                () -> new AccessDeniedException("Administrator access required."));
        if (actor.getRole() != UserRole.ADMIN || actor.getStatus() != UserStatus.ACTIVE) {
            throw new AccessDeniedException("Administrator access required.");
        }
    }
}
