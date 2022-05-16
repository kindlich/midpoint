/*
 * Copyright (C) 2010-2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.provisioning.impl.resources;

import static com.evolveum.midpoint.provisioning.impl.resources.ResourceCompletionOperation.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.evolveum.midpoint.prism.PrismProperty;

import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;

import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismObjectDefinition;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.provisioning.api.ProvisioningService;
import com.evolveum.midpoint.provisioning.impl.CommonBeans;
import com.evolveum.midpoint.provisioning.ucf.api.ConnectorInstance;
import com.evolveum.midpoint.provisioning.ucf.api.ExecuteProvisioningScriptOperation;
import com.evolveum.midpoint.provisioning.ucf.api.GenericFrameworkException;
import com.evolveum.midpoint.provisioning.ucf.api.UcfExecutionContext;
import com.evolveum.midpoint.provisioning.util.ProvisioningUtil;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.internals.InternalCounters;
import com.evolveum.midpoint.schema.internals.InternalMonitor;
import com.evolveum.midpoint.schema.processor.ResourceObjectTypeDefinition;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.result.OperationResultStatus;
import com.evolveum.midpoint.schema.statistics.ConnectorOperationalStatus;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.schema.util.ResourceTypeUtil;
import com.evolveum.midpoint.task.api.LightweightIdentifierGenerator;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.resource.capabilities_3.CapabilityType;
import com.evolveum.midpoint.xml.ns._public.resource.capabilities_3.ScriptCapabilityType;

@Component
public class ResourceManager {

    @Autowired @Qualifier("cacheRepositoryService")
    private RepositoryService repositoryService;

    @Autowired private ResourceCache resourceCache;
    @Autowired private ConnectorManager connectorManager;
    @Autowired private PrismContext prismContext;
    @Autowired private ResourceOperationalStateManager operationalStateManager;
    @Autowired private ProvisioningService provisioningService;
    @Autowired private LightweightIdentifierGenerator lightweightIdentifierGenerator;
    @Autowired private CommonBeans beans;
    @Autowired private ResourceCapabilitiesHelper capabilitiesHelper;

    @Autowired ResourceSchemaHelper schemaHelper;
    @Autowired SchemaFetcher schemaFetcher;
    @Autowired ResourceConnectorsManager connectorSelector;

    private static final Trace LOGGER = TraceManager.getTrace(ResourceManager.class);

    private static final String DOT_CLASS = ResourceManager.class.getName() + ".";
    private static final String OPERATION_DISCOVER_CONFIGURATION = DOT_CLASS + "discoverConfiguration";

    /**
     * Completes a resource that has been just retrieved from the repository, usually by a search operation.
     * (If the up-to-date cached version of the resource is available, it is used immediately.)
     */
    public @NotNull PrismObject<ResourceType> completeResource(
            @NotNull PrismObject<ResourceType> repositoryObject,
            @Nullable GetOperationOptions options,
            @NotNull Task task,
            @NotNull OperationResult result)
            throws ObjectNotFoundException, SchemaException, ExpressionEvaluationException, ConfigurationException {

        String oid = repositoryObject.getOid();
        boolean readonly = GetOperationOptions.isReadOnly(options);

        PrismObject<ResourceType> cachedResource = resourceCache.get(oid, repositoryObject.getVersion(), readonly);
        if (cachedResource != null) {
            LOGGER.trace("Returning resource from cache:\n{}", cachedResource.debugDumpLazily());
            return cachedResource;
        } else {
            return completeAndCacheResource(repositoryObject, options, task, result);
        }
    }

    /**
     * Gets a resource. We try the cache first. If it's not there, then we fetch, complete, and cache it.
     */
    @NotNull public PrismObject<ResourceType> getResource(
            @NotNull String oid, @Nullable GetOperationOptions options, @NotNull Task task, @NotNull OperationResult result)
            throws ObjectNotFoundException, SchemaException, ExpressionEvaluationException, ConfigurationException {
        boolean readonly = GetOperationOptions.isReadOnly(options);
        PrismObject<ResourceType> cachedResource = resourceCache.getIfLatest(oid, readonly, result);
        if (cachedResource != null) {
            LOGGER.trace("Returning resource from cache:\n{}", cachedResource.debugDumpLazily());
            return cachedResource;
        } else {
            // We must obviously NOT fetch resource from repo as read-only. We are going to modify it.
            PrismObject<ResourceType> repositoryObject = readResourceFromRepository(oid, result);
            return completeAndCacheResource(repositoryObject, options, task, result);
        }
    }

    private @NotNull PrismObject<ResourceType> completeAndCacheResource(
            @NotNull PrismObject<ResourceType> repositoryObject,
            @Nullable GetOperationOptions options,
            @NotNull Task task,
            @NotNull OperationResult result)
            throws ObjectNotFoundException, SchemaException, ExpressionEvaluationException, ConfigurationException {
        String oid = repositoryObject.getOid();

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Completing and caching fetched resource {}, version {} to cache "
                            + "(previously cached version {}, options={})",
                    repositoryObject, repositoryObject.getVersion(), beans.resourceCache.getVersion(oid), options);
        }

        ResourceCompletionOperation completionOperation = new ResourceCompletionOperation(repositoryObject, options, task, beans);
        PrismObject<ResourceType> completedResource =
                completionOperation.execute(result);

        logResourceAfterCompletion(completedResource);

        // TODO fix this diagnostics using member methods of the completion operation
        if (!isComplete(completedResource)) {
            // No not cache non-complete resources (e.g. those retrieved with noFetch)
            LOGGER.debug("Not putting {} into cache because it's not complete: hasSchema={}, hasCapabilitiesCached={}",
                    repositoryObject, hasSchema(completedResource), hasCapabilitiesCached(completedResource));
        } else {
            OperationResultStatus completionStatus = completionOperation.getOperationResultStatus();
            if (completionStatus != OperationResultStatus.SUCCESS) {
                LOGGER.debug("Not putting {} into cache because the completeResource operation status is {}",
                        ObjectTypeUtil.toShortString(repositoryObject), completionStatus);
            } else {
                LOGGER.debug("Putting {} into cache", repositoryObject);
                // Cache only resources that are completely OK
                beans.resourceCache.put(completedResource, completionOperation.getAncestorsOids());
            }
        }
        return completedResource;
    }

    private void logResourceAfterCompletion(PrismObject<ResourceType> completedResource) {
        if (!LOGGER.isTraceEnabled()) {
            return;
        }
        LOGGER.trace("Resource after completion, before putting into cache:\n{}", completedResource.debugDump());
        Element xsdSchemaElement = ResourceTypeUtil.getResourceXsdSchema(completedResource);
        if (xsdSchemaElement == null) {
            LOGGER.trace("Schema: null");
        } else {
            LOGGER.trace("Schema:\n{}",
                    DOMUtil.serializeDOMToString(ResourceTypeUtil.getResourceXsdSchema(completedResource)));
        }
    }

    @NotNull PrismObject<ResourceType> readResourceFromRepository(String oid, OperationResult parentResult)
            throws ObjectNotFoundException, SchemaException {
        InternalMonitor.recordCount(InternalCounters.RESOURCE_REPOSITORY_READ_COUNT);
        return repositoryService.getObject(ResourceType.class, oid, null, parentResult);
    }

    public void deleteResource(@NotNull String oid, OperationResult parentResult) throws ObjectNotFoundException {
        resourceCache.invalidateSingle(oid);
        repositoryService.deleteObject(ResourceType.class, oid, parentResult);
    }

    public SystemConfigurationType getSystemConfiguration() {
        return provisioningService.getSystemConfiguration();
    }


    /**
     * Test the connection.
     *
     * @param resource Resource object. Must NOT be immutable!
     *
     * @throws ObjectNotFoundException If the resource object cannot be found in repository (e.g. when trying to set its
     *                                 availability status).
     */
    public void testConnection(PrismObject<ResourceType> resource, Task task, OperationResult parentResult)
            throws ObjectNotFoundException, SchemaException, ConfigurationException {
        // FIXME temporary code
        new ResourceExpansionOperation(resource.asObjectable(), beans)
                .execute(parentResult);

        getTestConnectionOp(resource, task).execute(parentResult);
    }

    /**
     * Test partial configuration.
     *
     * @param resource Resource object. Must NOT be immutable!
     */
    public void testPartialConfiguration(PrismObject<ResourceType> resource, Task task, OperationResult parentResult) {
        new TestPartialConfigurationOperation(resource, task, beans).execute(parentResult);
    }

    public <T> Collection<PrismProperty<T>> discoverConfiguration(PrismObject<ResourceType> resource, OperationResult parentResult) {
        ConnectorSpec connectorSpec = connectorSelector.createDefaultConnectorSpec(resource);

        OperationResult connectorResult = parentResult
                .createSubresult(OPERATION_DISCOVER_CONFIGURATION);
        connectorResult.addParam(OperationResult.PARAM_NAME, connectorSpec.getConnectorName());
        connectorResult.addParam(OperationResult.PARAM_OID, connectorSpec.getConnectorOid());

        ConnectorInstance connector;
        try {
            connector = connectorManager.getConfiguredConnectorInstance(connectorSpec, false, false, connectorResult);
        } catch (CommunicationException | ConfigurationException | ObjectNotFoundException |
                SchemaException | RuntimeException e) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.error("Failed while discovering of configuration, error: {}", e.getMessage(), e);
            }
            connectorResult.recordFatalError("Failed while discovering of configuration, error: " + e.getMessage(), e);
            return Collections.emptySet();
        }

        Collection<PrismProperty<T>> suggestions = connector.discoverConfiguration(connectorResult);
        connectorResult.recordSuccess();
        return suggestions;
    }

    private AbstractTestConnectionOperation getTestConnectionOp(PrismObject<ResourceType> resource, Task task) {
        if (isRepoResource(resource)) {
            return new TestConnectionOperationResourceInRepo(resource, task, beans);
        } else {
            return new TestConnectionOperation(resource, task, beans);
        }
    }

    private boolean isRepoResource(PrismObject<ResourceType> resource) {
        String resourceOid = resource.getOid();
        return org.apache.commons.lang3.StringUtils.isNotEmpty(resourceOid);
    }

    /**
     * Modifies resource availability status in the repository (if needed).
     *
     * The necessity of status modification is determined against the current version of the resource - unless "skipGetResource"
     * is set. The resource is hopefully cached ResourceCache, so the performance impact should be almost ponone.
     *
     * Also note that in-memory representation of the resource is not modified. As a side effect, the cached resource
     * is invalidated because of the modification. But it will be loaded on the next occasion. This should be quite harmless,
     * as we do not expect availability changes to occur frequently.
     *
     * @param statusChangeReason Description of the reason of changing the availability status.
     * @param skipGetResource True if we want to skip "getResource" operation and therefore apply the change regardless of
     *                        the current resource availability status. This is to be used in situations where we expect that
     *                        the resource might not be successfully retrievable (e.g. if it's broken).
     *
     * @throws ObjectNotFoundException If the resource object does not exist in repository.
     */
    public void modifyResourceAvailabilityStatus(String resourceOid, AvailabilityStatusType newStatus, String statusChangeReason,
            Task task, OperationResult result, boolean skipGetResource) throws ObjectNotFoundException {

        AvailabilityStatusType currentStatus;
        String resourceDesc;
        PrismObject<ResourceType> resource;
        if (skipGetResource) {
            resource = null;
            currentStatus = null;
            resourceDesc = "resource " + resourceOid;
        } else {
            try {
                resource = getResource(resourceOid, GetOperationOptions.createNoFetch(), task, result);
            } catch (ConfigurationException | SchemaException | ExpressionEvaluationException e) {
                // We actually do not expect any of these exceptions here. The resource is most probably in use
                result.recordFatalError("Unexpected exception: " + e.getMessage(), e);
                throw new SystemException("Unexpected exception: " + e.getMessage(), e);
            }
            ResourceType resourceBean = resource.asObjectable();
            currentStatus = ResourceTypeUtil.getLastAvailabilityStatus(resourceBean);
            resourceDesc = resource.toString();
        }

        if (newStatus != currentStatus) {
            try {
                List<ItemDelta<?, ?>> modifications = operationalStateManager.createAndLogOperationalStateDeltas(
                        currentStatus, newStatus, resourceDesc, statusChangeReason, resource);
                repositoryService.modifyObject(ResourceType.class, resourceOid, modifications, result);
                result.computeStatusIfUnknown();
                InternalMonitor.recordCount(InternalCounters.RESOURCE_REPOSITORY_MODIFY_COUNT);
            } catch (SchemaException | ObjectAlreadyExistsException e) {
                throw new SystemException("Unexpected exception while recording operation state change: " + e.getMessage(), e);
            }
        }
    }

    public void modifyResourceAvailabilityStatus(PrismObject<ResourceType> resource, AvailabilityStatusType newStatus, String statusChangeReason) {

        AvailabilityStatusType currentStatus = ResourceTypeUtil.getLastAvailabilityStatus(resource.asObjectable());
        String resourceDesc = resource.toString();

        if (newStatus != currentStatus) {
            OperationalStateType newState = operationalStateManager.createAndLogOperationalState(
                    currentStatus,newStatus, resourceDesc, statusChangeReason);
            resource.asObjectable().operationalState(newState);
        }
    }

    public void applyDefinition(
            ObjectDelta<ResourceType> delta,
            ResourceType resourceWhenNoOid,
            GetOperationOptions options,
            Task task,
            OperationResult objectResult)
            throws SchemaException, ObjectNotFoundException, ExpressionEvaluationException, ConfigurationException {
        schemaHelper.applyDefinition(delta, resourceWhenNoOid, options, task, objectResult);
    }

    public void applyDefinition(PrismObject<ResourceType> resource, Task task, OperationResult parentResult)
            throws ObjectNotFoundException, SchemaException, ExpressionEvaluationException, ConfigurationException {
        schemaHelper.applyConnectorSchemasToResource(resource, task, parentResult);
    }

    public void applyDefinition(ObjectQuery query, OperationResult result) {
        // TODO: not implemented yet
    }

    /**
     * Apply proper definition (connector schema) to the resource.
     */
    void applyConnectorSchemaToResource(ConnectorSpec connectorSpec, PrismObjectDefinition<ResourceType> resourceDefinition,
            PrismObject<ResourceType> resource, Task task, OperationResult result)
            throws SchemaException, ObjectNotFoundException, ExpressionEvaluationException, CommunicationException,
            ConfigurationException, SecurityViolationException {
        schemaHelper.applyConnectorSchemaToResource(connectorSpec, resourceDefinition, resource, task, result);
    }

    public Object executeScript(String resourceOid, ProvisioningScriptType script, Task task, OperationResult result)
            throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException,
            ExpressionEvaluationException {
        PrismObject<ResourceType> resource = getResource(resourceOid, null, task, result);
        ConnectorSpec connectorSpec = connectorSelector.selectConnector(resource, ScriptCapabilityType.class);
        //  TODO!
//        if (connectorSpec == null) {
//            throw new UnsupportedOperationException("No connector supports script capability");
//        }
        ConnectorInstance connectorInstance = connectorManager.getConfiguredConnectorInstance(connectorSpec, false, result);
        ExecuteProvisioningScriptOperation scriptOperation = ProvisioningUtil.convertToScriptOperation(script, "script on "+resource, prismContext);
        try {
            UcfExecutionContext ucfCtx = new UcfExecutionContext(
                    lightweightIdentifierGenerator, resource.asObjectable(), task);
            return connectorInstance.executeScript(scriptOperation, ucfCtx, result);
        } catch (GenericFrameworkException e) {
            // Not expected. Transform to system exception
            result.recordFatalError("Generic provisioning framework error", e);
            throw new SystemException("Generic provisioning framework error: " + e.getMessage(), e);
        }
    }

    public List<ConnectorOperationalStatus> getConnectorOperationalStatus(
            PrismObject<ResourceType> resource, OperationResult result)
            throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException {
        List<ConnectorOperationalStatus> statuses = new ArrayList<>();
        for (ConnectorSpec connectorSpec : connectorSelector.getAllConnectorSpecs(resource)) {
            ConnectorInstance connectorInstance = connectorManager.getConfiguredConnectorInstance(connectorSpec, false, result);
            ConnectorOperationalStatus operationalStatus = connectorInstance.getOperationalStatus();
            if (operationalStatus != null) {
                operationalStatus.setConnectorName(connectorSpec.getConnectorName());
                statuses.add(operationalStatus);
            }
        }
        return statuses;
    }

    // Should be used only internally (private). But it is public, because it is accessed from the tests.
    @VisibleForTesting
    public <T extends CapabilityType> ConnectorInstance getConfiguredConnectorInstance(
            PrismObject<ResourceType> resource,
            Class<T> capabilityClass,
            boolean forceFresh,
            OperationResult parentResult)
            throws SchemaException, ObjectNotFoundException, CommunicationException, ConfigurationException {
        ConnectorSpec connectorSpec = connectorSelector.selectConnector(resource, capabilityClass);
        return connectorManager.getConfiguredConnectorInstance(connectorSpec, forceFresh, parentResult);
    }

    // Used by the tests. Does not change anything.
    @SuppressWarnings("SameParameterValue")
    @VisibleForTesting
    public <T extends CapabilityType> ConnectorInstance getConfiguredConnectorInstanceFromCache(
            PrismObject<ResourceType> resource, Class<T> operationCapabilityClass) throws ConfigurationException {
        ConnectorSpec connectorSpec = connectorSelector.selectConnector(resource, operationCapabilityClass);
        return connectorManager.getConfiguredConnectorInstanceFromCache(connectorSpec);
    }

    /**
     * Gets a specific capability from resource/connectors/object-class.
     */
    public <T extends CapabilityType> T getCapability(
            @NotNull ResourceType resource,
            @Nullable ResourceObjectTypeDefinition objectTypeDefinition,
            @NotNull Class<T> operationCapabilityClass) {
        return capabilitiesHelper.getCapability(resource, objectTypeDefinition, operationCapabilityClass);
    }
}
