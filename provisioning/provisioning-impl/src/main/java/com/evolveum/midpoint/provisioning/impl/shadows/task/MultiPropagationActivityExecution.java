/*
 * Copyright (c) 2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.provisioning.impl.shadows.task;

import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.crypto.EncryptionException;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.provisioning.ucf.api.GenericFrameworkException;
import com.evolveum.midpoint.repo.common.activity.ActivityExecutionException;
import com.evolveum.midpoint.repo.common.activity.execution.ExecutionInstantiationContext;
import com.evolveum.midpoint.repo.common.task.ActivityReportingOptions;
import com.evolveum.midpoint.repo.common.task.ItemProcessingRequest;
import com.evolveum.midpoint.repo.common.task.ObjectSearchBasedActivityExecution;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.RunningTask;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.CommonException;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AbstractActivityWorkStateType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;

/**
 * Execution of a multi-propagation task.
 */
public class MultiPropagationActivityExecution
        extends ObjectSearchBasedActivityExecution
        <ResourceType,
                MultiPropagationWorkDefinition,
                MultiPropagationActivityHandler,
                AbstractActivityWorkStateType> {

    private static final Trace LOGGER = TraceManager.getTrace(MultiPropagationActivityExecution.class);

    MultiPropagationActivityExecution(
            @NotNull ExecutionInstantiationContext<MultiPropagationWorkDefinition, MultiPropagationActivityHandler> context) {
        super(context, "Multi-propagation");
    }

    @Override
    public @NotNull ActivityReportingOptions getDefaultReportingOptions() {
        return super.getDefaultReportingOptions()
                .enableActionsExecutedStatistics(true)
                .enableSynchronizationStatistics(false)
                .skipWritingOperationExecutionRecords(true); // to avoid resource change (invalidates the caches)
    }

    @Override
    public boolean processObject(@NotNull PrismObject<ResourceType> resource,
            @NotNull ItemProcessingRequest<PrismObject<ResourceType>> request,
            RunningTask workerTask, OperationResult result)
            throws CommonException, ActivityExecutionException {
        LOGGER.trace("Propagating provisioning operations on {}", resource);

        ObjectQuery shadowQuery = getBeans().prismContext.queryFor(ShadowType.class)
                .item(ShadowType.F_RESOURCE_REF).ref(resource.getOid())
                .and()
                .exists(ShadowType.F_PENDING_OPERATION)
                .build();

        getBeans().repositoryService.searchObjectsIterative(ShadowType.class, shadowQuery, (shadow, lResult) -> {
            propagateOperationsOnShadow(shadow, resource, workerTask, lResult);
            return true;
        }, null, true, result);

        LOGGER.trace("Propagation of {} done", resource);
        return true;
    }

    private void propagateOperationsOnShadow(PrismObject<ShadowType> shadow, PrismObject<ResourceType> resource,
            Task workerTask, OperationResult result) {
        try {
            getActivityHandler().shadowsFacade.propagateOperations(resource, shadow, workerTask, result);
        } catch (CommonException | GenericFrameworkException | EncryptionException e) {
            throw new SystemException("Provisioning error: " + e.getMessage(), e);
        }
    }
}
