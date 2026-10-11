package com.unidoc.catalogue;

import com.unidoc.request.DeliveryMethod;

import java.util.List;

/** All-role read model: only active types, no mutable/audit metadata. */
public record CatalogueDocumentTypeResponse(
        long id,
        String code,
        String name,
        String description,
        String requirements,
        boolean supportingDocumentsRequired,
        List<DeliveryMethod> availableDeliveryMethods,
        String collectionLocation,
        String collectionInstructions
) { }
