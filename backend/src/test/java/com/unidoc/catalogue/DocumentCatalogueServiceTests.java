package com.unidoc.catalogue;

import com.unidoc.common.api.PageResponse;
import com.unidoc.common.exception.InvalidQueryParameterException;
import com.unidoc.request.DeliveryMethod;
import com.unidoc.request.DocumentRequest;
import com.unidoc.request.DocumentRequestRepository;
import com.unidoc.user.User;
import com.unidoc.user.UserRepository;
import com.unidoc.user.UserRole;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "UNIDOC_DB_URL=jdbc:h2:mem:unidoc-catalogue-service;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "UNIDOC_DB_USERNAME=sa", "UNIDOC_DB_PASSWORD=",
        "UNIDOC_JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "UNIDOC_STORAGE_ROOT=target/catalogue-service-test-uploads",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class DocumentCatalogueServiceTests {
    @Autowired private DocumentCatalogueService service;
    @Autowired private DocumentTypeRepository types;
    @Autowired private UserRepository users;
    @Autowired private DocumentRequestRepository requests;
    @Autowired private EntityManager entityManager;

    @Test
    void createNormalizesContentAndStoresAdminAuditActorsAndInactiveStatus() {
        User admin = user(UserRole.ADMIN);
        String code = code();
        AdminDocumentTypeResponse saved = service.create(admin.getId(), payload("  " + code.toLowerCase() + "  ",
                "   Confirmation   Letter   ", true, false, false, null));
        DocumentType entity = types.findById(saved.id()).orElseThrow();
        assertThat(saved.code()).isEqualTo(code);
        assertThat(saved.name()).isEqualTo("Confirmation Letter");
        assertThat(saved.description()).isEqualTo("A description for the university document.");
        assertThat(saved.electronicAvailable()).isTrue();
        assertThat(saved.physicalAvailable()).isFalse();
        assertThat(saved.active()).isFalse();
        assertThat(saved.collectionLocation()).isNull();
        assertThat(saved.createdAt()).isNotNull();
        assertThat(saved.updatedAt()).isNotNull();
        assertThat(entity.getCreatedBy().getId()).isEqualTo(admin.getId());
        assertThat(entity.getUpdatedBy().getId()).isEqualTo(admin.getId());
    }

    @Test
    void allRolesReadOnlySeeActiveItemsInNameOrderAndInactiveDetailIsHidden() {
        User admin = user(UserRole.ADMIN);
        User staff = user(UserRole.STAFF);
        User student = user(UserRole.STUDENT);
        AdminDocumentTypeResponse zulu = service.create(admin.getId(), payload(code(), "Zulu Letter", true, true, true, "Registry Desk"));
        AdminDocumentTypeResponse alpha = service.create(admin.getId(), payload(code(), "Alpha Letter", false, true, true, "Registry Desk"));
        AdminDocumentTypeResponse hidden = service.create(admin.getId(), payload(code(), "Hidden Letter", true, false, false, null));
        for (User caller : new User[]{admin, staff, student}) {
            var list = service.listActive(caller.getId());
            assertThat(list).extracting(CatalogueDocumentTypeResponse::name).containsExactly("Alpha Letter", "Zulu Letter");
            assertThat(service.getActive(caller.getId(), zulu.id()).availableDeliveryMethods())
                    .containsExactly(DeliveryMethod.ELECTRONIC, DeliveryMethod.PHYSICAL);
            assertThat(service.getActive(caller.getId(), alpha.id()).collectionLocation()).isEqualTo("Registry Desk");
            assertThatThrownBy(() -> service.getActive(caller.getId(), hidden.id()))
                    .isInstanceOf(DocumentTypeNotFoundException.class);
        }
        assertThat(service.getManaged(admin.getId(), hidden.id()).active()).isFalse();
    }

    @Test
    void supportsElectronicOnlyPhysicalOnlyAndRejectsInvalidDeliveryConfiguration() {
        User admin = user(UserRole.ADMIN);
        var electronic = service.create(admin.getId(), payload(code(), "Electronic Only", true, false, true, null));
        var physical = service.create(admin.getId(), payload(code(), "Physical Only", false, true, true, "Collection Counter"));
        assertThat(service.getActive(admin.getId(), electronic.id()).availableDeliveryMethods())
                .containsExactly(DeliveryMethod.ELECTRONIC);
        assertThat(service.getActive(admin.getId(), electronic.id()).collectionLocation()).isNull();
        assertThat(service.getActive(admin.getId(), physical.id()).availableDeliveryMethods())
                .containsExactly(DeliveryMethod.PHYSICAL);
        assertThatThrownBy(() -> service.create(admin.getId(), payload(code(), "No Delivery", false, false, true, null)))
                .isInstanceOf(ConstraintViolationException.class).hasMessageContaining("Enable at least one delivery method");
        assertThatThrownBy(() -> service.create(admin.getId(), payload(code(), "Missing Location", false, true, true, " ")))
                .isInstanceOf(ConstraintViolationException.class).hasMessageContaining("Collection location is required");
    }

    @Test
    void rejectsDuplicateCodeAndNameRegardlessOfCaseIncludingOnUpdate() {
        User admin = user(UserRole.ADMIN);
        String duplicateCode = code();
        AdminDocumentTypeResponse a = service.create(admin.getId(), payload(duplicateCode, "Original Letter", true, true, true, "Office A"));
        AdminDocumentTypeResponse b = service.create(admin.getId(), payload(code(), "Other Letter", true, false, true, null));
        assertThatThrownBy(() -> service.create(admin.getId(), payload(duplicateCode.toLowerCase(), "Third Letter", true, false, true, null)))
                .isInstanceOf(DuplicateDocumentTypeCodeException.class);
        assertThatThrownBy(() -> service.create(admin.getId(), payload(code(), "oRiGiNaL lEtTeR", true, false, true, null)))
                .isInstanceOf(DuplicateDocumentTypeNameException.class);
        assertThatThrownBy(() -> service.update(admin.getId(), b.id(), payload(duplicateCode, "Other Letter", true, false, true, null)))
                .isInstanceOf(DuplicateDocumentTypeCodeException.class);
        assertThatThrownBy(() -> service.update(admin.getId(), b.id(), payload(code(), "original letter", true, false, true, null)))
                .isInstanceOf(DuplicateDocumentTypeNameException.class);
        // Reusing the unchanged code and name is valid for the same record.
        assertThat(service.update(admin.getId(), a.id(), payload(duplicateCode.toLowerCase(), "Original Letter", true, true, true, "Office B")).collectionLocation())
                .isEqualTo("Office B");
    }

    @Test
    void updateReplacesEditableValuesButPreservesCreatorAndCreationTime() {
        User creator = user(UserRole.ADMIN);
        User editor = user(UserRole.ADMIN);
        var created = service.create(creator.getId(), payload(code(), "Before Name", true, true, true, "Office A"));
        assertThat(created.supportingDocumentsRequired()).isTrue();
        var changed = service.update(editor.getId(), created.id(),
                payload(code(), "After Name", false, true, false, "Office B", false));
        assertThat(changed.name()).isEqualTo("After Name");
        assertThat(changed.supportingDocumentsRequired()).isFalse();
        assertThat(changed.physicalAvailable()).isTrue();
        assertThat(changed.electronicAvailable()).isFalse();
        assertThat(changed.active()).isFalse();
        assertThat(changed.createdAt()).isEqualTo(created.createdAt());
        DocumentType entity = types.findById(created.id()).orElseThrow();
        assertThat(entity.getCreatedBy().getId()).isEqualTo(creator.getId());
        assertThat(entity.getUpdatedBy().getId()).isEqualTo(editor.getId());
    }

    @Test
    void deactivationAndReactivationAreIdempotentAndDoNotDeleteCatalogueRows() {
        User admin = user(UserRole.ADMIN);
        var created = service.create(admin.getId(), payload(code(), "Toggle Name", true, false, true, null));
        var inactive = service.changeStatus(admin.getId(), created.id(), new ChangeDocumentTypeStatusRequest(false));
        assertThat(inactive.active()).isFalse();
        assertThatThrownBy(() -> service.getActive(admin.getId(), created.id())).isInstanceOf(DocumentTypeNotFoundException.class);
        var repeat = service.changeStatus(admin.getId(), created.id(), new ChangeDocumentTypeStatusRequest(false));
        assertThat(repeat.updatedAt()).isEqualTo(inactive.updatedAt());
        assertThat(types.existsById(created.id())).isTrue();
        assertThat(service.changeStatus(admin.getId(), created.id(), new ChangeDocumentTypeStatusRequest(true)).active()).isTrue();
        assertThat(service.getActive(admin.getId(), created.id()).name()).isEqualTo("Toggle Name");
    }

    @Test
    void filtersSortsAndPaginatesAllIncludingInactiveWithoutUnboundedQueries() {
        User admin = user(UserRole.ADMIN);
        String marker = "C" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        service.create(admin.getId(), payload(marker + "A", "Alpha " + marker, true, false, true, null));
        service.create(admin.getId(), payload(marker + "B", "Beta " + marker, true, false, false, null));
        service.create(admin.getId(), payload(marker + "C", "Gamma " + marker, true, false, true, null));
        PageResponse<AdminDocumentTypeResponse> page = service.listManaged(admin.getId(), marker, null, 0, 1, "name", "asc");
        assertThat(page.totalItems()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.items()).extracting(AdminDocumentTypeResponse::name).containsExactly("Alpha " + marker);
        assertThat(service.listManaged(admin.getId(), marker, false, 0, 20, "name", "asc").items())
                .extracting(AdminDocumentTypeResponse::name).containsExactly("Beta " + marker);
        assertThat(service.listManaged(admin.getId(), marker.toLowerCase(), true, 0, 20, "code", "desc").totalItems()).isEqualTo(2);
        assertThat(service.listManaged(admin.getId(), marker, null, 1, 1, "name", "asc").items())
                .extracting(AdminDocumentTypeResponse::name).containsExactly("Beta " + marker);
        assertThat(service.listManaged(admin.getId(), "no-such-" + marker, null, 0, 20, "name", "asc").items()).isEmpty();
    }

    @Test
    void validatesPaginationQuerySortingAndProtectsServiceBoundary() {
        User admin = user(UserRole.ADMIN);
        User staff = user(UserRole.STAFF);
        assertThatThrownBy(() -> service.create(staff.getId(), payload(code(), "Staff Created", true, false, true, null)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.listManaged(staff.getId(), null, null, null, null, null, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.update(staff.getId(), 1, payload(code(), "Updated Name", true, false, true, null)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.changeStatus(staff.getId(), 1, new ChangeDocumentTypeStatusRequest(false)))
                .isInstanceOf(AccessDeniedException.class);
        for (String invalid : new String[]{"passwordHash", "createdBy", "unknown"}) {
            assertThatThrownBy(() -> service.listManaged(admin.getId(), null, null, 0, 20, invalid, "asc"))
                    .isInstanceOf(InvalidQueryParameterException.class);
        }
        assertThatThrownBy(() -> service.listManaged(admin.getId(), "x".repeat(101), null, 0, 20, null, null))
                .isInstanceOf(InvalidQueryParameterException.class);
        assertThatThrownBy(() -> service.listManaged(admin.getId(), null, null, -1, 20, null, null))
                .isInstanceOf(InvalidQueryParameterException.class);
        assertThatThrownBy(() -> service.listManaged(admin.getId(), null, null, 0, 101, null, null))
                .isInstanceOf(InvalidQueryParameterException.class);
        assertThatThrownBy(() -> service.listManaged(admin.getId(), null, null, 0, 20, null, "sideways"))
                .isInstanceOf(InvalidQueryParameterException.class);
        assertThat(service.listManaged(admin.getId(), "%_\\", null, 0, 20, null, null).items()).isEmpty();
    }

    @Test
    void existingRequestSnapshotsSurviveCatalogueRenameAndCollectionChange() {
        User admin = user(UserRole.ADMIN);
        User student = user(UserRole.STUDENT);
        var created = service.create(admin.getId(), payload(code(), "Before Snapshot", true, true, true, "Original Desk"));
        DocumentType type = types.findById(created.id()).orElseThrow();
        DocumentRequest request = requests.saveAndFlush(new DocumentRequest(student, type, DeliveryMethod.PHYSICAL,
                "A document for academic administration", LocalDate.now().plusDays(5)));
        request.assignRequestNumber();
        requests.flush();
        String number = request.getRequestNumber();
        service.update(admin.getId(), type.getId(), payload(type.getCode(), "After Snapshot", true, true, true, "New Desk"));
        entityManager.flush();
        entityManager.clear();
        DocumentRequest historical = requests.findByRequestNumber(number).orElseThrow();
        assertThat(historical.getDocumentTypeNameSnapshot()).isEqualTo("Before Snapshot");
        assertThat(historical.getCollectionLocationSnapshot()).isEqualTo("Original Desk");
        assertThat(historical.getDocumentType().getName()).isEqualTo("After Snapshot");
    }

    private User user(UserRole role) {
        return users.saveAndFlush(new User("issue8-service-" + UUID.randomUUID() + "@example.test", "hash", "Catalogue Tester", role));
    }

    private static String code() {
        return "DT_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    private static UpsertDocumentTypeRequest payload(String code, String name,
                                                     boolean electronic, boolean physical, boolean active, String location) {
        return payload(code, name, electronic, physical, active, location, true);
    }

    private static UpsertDocumentTypeRequest payload(String code, String name,
                                                     boolean electronic, boolean physical, boolean active,
                                                     String location, boolean supportingDocumentsRequired) {
        return new UpsertDocumentTypeRequest(code, name, "  A description   for the university document.  ",
                "Supply the required evidence.", supportingDocumentsRequired,
                electronic, physical, location, null, active);
    }
}
