package com.unidoc.common.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

import java.util.Optional;

@NoRepositoryBean
public interface UniDocRepository<T, ID> extends Repository<T, ID> {

    <S extends T> S save(S entity);

    <S extends T> S saveAndFlush(S entity);

    Optional<T> findById(ID id);

    boolean existsById(ID id);

    Page<T> findAll(Pageable pageable);

    long count();

    void flush();
}
