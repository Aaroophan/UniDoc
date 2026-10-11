package com.unidoc.catalogue;

import com.unidoc.common.api.PageResponse;
import com.unidoc.common.persistence.Normalization;
import com.unidoc.common.web.QueryParameters;
import com.unidoc.request.DeliveryMethod;
import com.unidoc.user.User;
import com.unidoc.user.UserRepository;
import com.unidoc.user.UserRole;
import com.unidoc.user.UserStatus;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class DocumentCatalogueService {
    private static final Logger log = LoggerFactory.getLogger(DocumentCatalogueService.class);

    private final DocumentTypeRepository types;
    private final UserRepository users;
    private final Validator validator;

    public DocumentCatalogueService(DocumentTypeRepository types, UserRepository users, Validator validator) {
        this.types = types;
        this.users = users;
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    public List<CatalogueDocumentTypeResponse> listActive(long actorId) {
        requireActiveUser(actorId);
        return types.findByActiveTrueOrderByNameAsc().stream().map(DocumentCatalogueService::toCatalogueResponse).toList();
    }

    @Transactional(readOnly = true)
    public CatalogueDocumentTypeResponse getActive(long actorId, long id) {
        requireActiveUser(actorId);
        return toCatalogueResponse(types.findByIdAndActiveTrue(id).orElseThrow(DocumentTypeNotFoundException::new));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminDocumentTypeResponse> listManaged(
            long actorId, String query, Boolean active,
            Integer page, Integer size, String sort, String direction
    ) {
        requireAdmin(actorId);
        String text = QueryParameters.freeText(query, 100);
        Pageable paging = QueryParameters.pageable(page, size, sort, direction,
                DocumentTypeSortProperties.MAPPINGS, DocumentTypeSortProperties.DEFAULT, Sort.Direction.ASC);
        return PageResponse.from(types.findAll(DocumentTypeSpecifications.filtered(text, active), paging),
                DocumentCatalogueService::toAdminResponse);
    }

    @Transactional(readOnly = true)
    public AdminDocumentTypeResponse getManaged(long actorId, long id) {
        requireAdmin(actorId);
        return toAdminResponse(types.findById(id).orElseThrow(DocumentTypeNotFoundException::new));
    }

    @Transactional
    public AdminDocumentTypeResponse create(long actorId, UpsertDocumentTypeRequest request) {
        User actor = requireAdmin(actorId);
        validate(request);
        Editable values = normalize(request);
        ensureUnique(values.code, values.name, null);
        DocumentType type = new DocumentType(values.code, values.name, values.description, values.requirements,
                request.supportingDocumentsRequired(), request.electronicAvailable(), request.physicalAvailable(),
                values.collectionLocation, values.collectionInstructions, actor);
        // The constructor defaults active=true; respect an explicitly inactive initial entry.
        type.replaceEditable(values.code, values.name, values.description, values.requirements,
                request.supportingDocumentsRequired(), request.electronicAvailable(), request.physicalAvailable(),
                values.collectionLocation, values.collectionInstructions, request.active(), actor);
        DocumentType saved = types.saveAndFlush(type);
        log.info("Document type created actorId={} documentTypeId={}", actorId, saved.getId());
        return toAdminResponse(saved);
    }

    @Transactional
    public AdminDocumentTypeResponse update(long actorId, long id, UpsertDocumentTypeRequest request) {
        User actor = requireAdmin(actorId);
        validate(request);
        DocumentType type = types.findById(id).orElseThrow(DocumentTypeNotFoundException::new);
        Editable values = normalize(request);
        ensureUnique(values.code, values.name, id);
        type.replaceEditable(values.code, values.name, values.description, values.requirements,
                request.supportingDocumentsRequired(), request.electronicAvailable(), request.physicalAvailable(),
                values.collectionLocation, values.collectionInstructions, request.active(), actor);
        types.saveAndFlush(type);
        log.info("Document type updated actorId={} documentTypeId={}", actorId, id);
        return toAdminResponse(type);
    }

    @Transactional
    public AdminDocumentTypeResponse changeStatus(long actorId, long id, ChangeDocumentTypeStatusRequest request) {
        User actor = requireAdmin(actorId);
        DocumentType type = types.findById(id).orElseThrow(DocumentTypeNotFoundException::new);
        if (type.changeActive(request.active(), actor)) {
            types.saveAndFlush(type);
            log.info("Document type status changed actorId={} documentTypeId={} active={}",
                    actorId, id, request.active());
        }
        return toAdminResponse(type);
    }

    private void validate(UpsertDocumentTypeRequest request) {
        Set<ConstraintViolation<UpsertDocumentTypeRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
    }

    private void ensureUnique(String code, String name, Long excludingId) {
        boolean codeUsed = excludingId == null ? types.existsByCode(code) : types.existsByCodeAndIdNot(code, excludingId);
        if (codeUsed) throw new DuplicateDocumentTypeCodeException();
        boolean nameUsed = excludingId == null ? types.existsByNameIgnoreCase(name)
                : types.existsByNameIgnoreCaseAndIdNot(name, excludingId);
        if (nameUsed) throw new DuplicateDocumentTypeNameException();
    }

    private User requireActiveUser(long id) {
        User actor = users.findById(id).orElseThrow(() -> new AccessDeniedException("Access denied."));
        if (actor.getStatus() != UserStatus.ACTIVE) throw new AccessDeniedException("Access denied.");
        return actor;
    }

    private User requireAdmin(long id) {
        User actor = requireActiveUser(id);
        if (actor.getRole() != UserRole.ADMIN) throw new AccessDeniedException("Administrator access required.");
        return actor;
    }

    /** Normalized text is persisted, not the original DTO values. */
    private static Editable normalize(UpsertDocumentTypeRequest request) {
        return new Editable(Normalization.documentCode(request.code()),
                clean(request.name()), clean(request.description()), clean(request.requirements()),
                optional(request.collectionLocation()), optional(request.collectionInstructions()));
    }

    private static String clean(String value) {
        return value.strip().replaceAll("(?U)\\s+", " ");
    }

    private static String optional(String value) {
        if (value == null || value.isBlank()) return null;
        return clean(value);
    }

    private record Editable(String code, String name, String description, String requirements,
                            String collectionLocation, String collectionInstructions) { }

    private static CatalogueDocumentTypeResponse toCatalogueResponse(DocumentType type) {
        List<DeliveryMethod> methods = new ArrayList<>(2);
        if (type.isElectronicAvailable()) methods.add(DeliveryMethod.ELECTRONIC);
        if (type.isPhysicalAvailable()) methods.add(DeliveryMethod.PHYSICAL);
        return new CatalogueDocumentTypeResponse(type.getId(), type.getCode(), type.getName(),
                type.getDescription(), type.getRequirements(), type.isSupportingDocumentsRequired(),
                List.copyOf(methods), type.isPhysicalAvailable() ? type.getCollectionLocation() : null,
                type.isPhysicalAvailable() ? type.getCollectionInstructions() : null);
    }

    private static AdminDocumentTypeResponse toAdminResponse(DocumentType type) {
        return new AdminDocumentTypeResponse(type.getId(), type.getCode(), type.getName(),
                type.getDescription(), type.getRequirements(), type.isSupportingDocumentsRequired(),
                type.isElectronicAvailable(), type.isPhysicalAvailable(), type.getCollectionLocation(),
                type.getCollectionInstructions(), type.isActive(), type.getCreatedAt(), type.getUpdatedAt());
    }
}
