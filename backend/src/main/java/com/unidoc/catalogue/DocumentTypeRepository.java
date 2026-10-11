package com.unidoc.catalogue;

import com.unidoc.common.persistence.Normalization;
import com.unidoc.common.persistence.UniDocRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface DocumentTypeRepository
        extends UniDocRepository<DocumentType, Long>, JpaSpecificationExecutor<DocumentType> {

    Optional<DocumentType> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByName(String name);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    boolean existsByCodeAndIdNot(String code, Long id);

    Optional<DocumentType> findByIdAndActiveTrue(Long id);

    List<DocumentType> findByActiveTrueOrderByNameAsc();

    default Optional<DocumentType> findByNormalizedCode(String code) {
        return findByCode(Normalization.documentCode(code));
    }

    default boolean existsByNormalizedCode(String code) {
        return existsByCode(Normalization.documentCode(code));
    }
}
