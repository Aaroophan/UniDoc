package com.unidoc.profile;

import com.unidoc.common.persistence.Normalization;
import com.unidoc.common.persistence.UniDocRepository;

import java.util.Optional;

public interface StudentProfileRepository extends UniDocRepository<StudentProfile, Long> {

    Optional<StudentProfile> findByUserId(Long userId);

    Optional<StudentProfile> findByRegistrationNumber(String registrationNumber);

    boolean existsByRegistrationNumber(String registrationNumber);

    boolean existsByRegistrationNumberAndIdNot(String registrationNumber, Long profileId);

    default Optional<StudentProfile> findByNormalizedRegistrationNumber(String registrationNumber) {
        return findByRegistrationNumber(Normalization.registrationNumber(registrationNumber));
    }

    default boolean normalizedRegistrationNumberExistsForAnotherProfile(
            String registrationNumber,
            Long profileId
    ) {
        return existsByRegistrationNumberAndIdNot(
                Normalization.registrationNumber(registrationNumber),
                profileId
        );
    }
}
