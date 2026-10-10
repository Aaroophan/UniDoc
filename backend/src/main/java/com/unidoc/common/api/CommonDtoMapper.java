package com.unidoc.common.api;

import com.unidoc.request.DocumentRequest;
import com.unidoc.request.RequestAttachment;
import com.unidoc.request.RequestStatusHistory;
import com.unidoc.user.User;

public final class CommonDtoMapper {

    private CommonDtoMapper() {
    }

    public static UserSummary toUserSummary(User user) {
        return new UserSummary(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt()
        );
    }

    public static ActorSummary toActorSummary(User user) {
        return new ActorSummary(user.getId(), user.getFullName(), user.getRole());
    }

    public static AttachmentMetadata toAttachmentMetadata(RequestAttachment attachment) {
        return new AttachmentMetadata(
                attachment.getId(),
                attachment.getAttachmentKind(),
                attachment.getOriginalFilename(),
                attachment.getContentType(),
                attachment.getSizeBytes(),
                attachment.getCreatedAt()
        );
    }

    public static HistoryEntry toHistoryEntry(RequestStatusHistory history) {
        return new HistoryEntry(
                history.getId(),
                history.getPreviousStatus(),
                history.getNewStatus(),
                toActorSummary(history.getChangedBy()),
                history.getNote(),
                history.getCreatedAt()
        );
    }

    public static RequestSummary toRequestSummary(DocumentRequest request, String registrationNumber) {
        return new RequestSummary(
                request.getId(),
                request.getRequestNumber(),
                request.getStudent().getFullName(),
                registrationNumber,
                request.getDocumentType().getId(),
                request.getDocumentTypeNameSnapshot(),
                request.getDeliveryMethod(),
                request.getStatus(),
                request.getRequiredDate(),
                request.getCreatedAt(),
                request.getUpdatedAt(),
                request.getVersion()
        );
    }
}
