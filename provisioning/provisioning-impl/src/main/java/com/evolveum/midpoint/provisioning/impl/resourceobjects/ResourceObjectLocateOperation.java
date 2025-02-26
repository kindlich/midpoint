/*
 * Copyright (C) 2010-2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.provisioning.impl.resourceobjects;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.provisioning.api.GenericConnectorException;
import com.evolveum.midpoint.provisioning.impl.ProvisioningContext;
import com.evolveum.midpoint.provisioning.ucf.api.ConnectorInstance;
import com.evolveum.midpoint.provisioning.ucf.api.GenericFrameworkException;
import com.evolveum.midpoint.provisioning.ucf.api.UcfFetchErrorReportingMethod;
import com.evolveum.midpoint.provisioning.ucf.api.UcfObjectHandler;
import com.evolveum.midpoint.schema.processor.ResourceObjectIdentification;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.util.Holder;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.FetchErrorReportingMethodType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;
import com.evolveum.midpoint.xml.ns._public.resource.capabilities_3.ReadCapabilityType;

/**
 * Locates the resource object by (arbitrary/first) secondary identifier, implementing this method:
 *
 * - {@link ResourceObjectConverter#locateResourceObject(
 * ProvisioningContext, ResourceObjectIdentification, boolean, OperationResult)}
 *
 * @see ResourceObjectFetchOperation
 * @see ResourceObjectSearchOperation
 */
class ResourceObjectLocateOperation extends AbstractResourceObjectRetrievalOperation {

    private static final Trace LOGGER = TraceManager.getTrace(ResourceObjectLocateOperation.class);

    @NotNull private final ResourceObjectIdentification.SecondaryOnly identification;

    private ResourceObjectLocateOperation(
            @NotNull ProvisioningContext ctx,
            @NotNull ResourceObjectIdentification.SecondaryOnly identification,
            boolean fetchAssociations,
            @Nullable FetchErrorReportingMethodType errorReportingMethod) {
        super(ctx, fetchAssociations, errorReportingMethod);
        this.identification = identification;
    }

    /**
     * Locates the resource object either by primary identifier(s) or by secondary identifier(s).
     * Both cases are handled by the resource, i.e. not by the repository.
     */
    static CompleteResourceObject execute(
            @NotNull ProvisioningContext ctx,
            @NotNull ResourceObjectIdentification.SecondaryOnly identification,
            boolean fetchAssociations,
            @NotNull OperationResult result)
            throws SchemaException, CommunicationException, ObjectNotFoundException, ConfigurationException,
            SecurityViolationException, ExpressionEvaluationException {
        return new ResourceObjectLocateOperation(ctx, identification, fetchAssociations, null)
                .execute(result);
    }

    private CompleteResourceObject execute(OperationResult result)
            throws SchemaException, CommunicationException, ObjectNotFoundException, ConfigurationException,
            SecurityViolationException, ExpressionEvaluationException {

        LOGGER.trace("Locating resource object by secondary identification {}", identification);
        var secondaryIdentifier = identification.getSecondaryIdentifiers().iterator().next(); // there must be at least one

        ConnectorInstance connector = ctx.getConnector(ReadCapabilityType.class, result);

        ObjectQuery query = PrismContext.get().queryFor(ShadowType.class)
                .item(secondaryIdentifier.getSearchPath(), secondaryIdentifier.getDefinition())
                .eq(secondaryIdentifier.getValue())
                .build();
        Holder<ResourceObject> objectHolder = new Holder<>();
        UcfObjectHandler handler = (ucfObject, lResult) -> {
            if (!objectHolder.isEmpty()) {
                throw new IllegalStateException(
                        String.format("More than one object found for %s (%s)",
                                secondaryIdentifier, ctx.getExceptionDescription()));
            }
            objectHolder.setValue(ResourceObject.from(ucfObject));
            return true;
        };
        try {
            // TODO constraints? scope?
            connector.search(
                    ctx.getObjectDefinitionRequired(),
                    query,
                    handler,
                    ctx.createAttributesToReturn(),
                    null,
                    null,
                    UcfFetchErrorReportingMethod.EXCEPTION,
                    ctx.getUcfExecutionContext(),
                    result);
            if (objectHolder.isEmpty()) {
                // We could consider continuing with another secondary identifier, but this was the behavior in 4.8 and before.
                throw new ObjectNotFoundException(
                        String.format("No object found for %s (%s)",
                                secondaryIdentifier, ctx.getExceptionDescription(connector)),
                        ShadowType.class,
                        null,
                        ctx.isAllowNotFound());
            }
            return complete(objectHolder.getValue(), result);
        } catch (GenericFrameworkException e) {
            throw new GenericConnectorException(
                    String.format("Generic exception in connector while searching for object (%s): %s",
                            ctx.getExceptionDescription(connector), e.getMessage()),
                    e);
        }
    }
}
