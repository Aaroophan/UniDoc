package com.unidoc.user;

import com.unidoc.common.persistence.Normalization;
import com.unidoc.common.persistence.UniDocRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface UserRepository extends UniDocRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    /** Serializes status and password mutations to prevent lost authVersion updates. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    boolean existsByEmail(String email);

    Page<User> findByRoleAndStatus(UserRole role, UserStatus status, Pageable pageable);

    default Optional<User> findByNormalizedEmail(String email) {
        return findByEmail(Normalization.email(email));
    }

    default boolean existsByNormalizedEmail(String email) {
        return existsByEmail(Normalization.email(email));
    }
}
