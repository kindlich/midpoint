/*
 * Copyright (c) 2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.model.impl.tasks;

import static com.evolveum.midpoint.model.api.ModelExecuteOptions.fromModelExecutionOptionsType;

import javax.xml.namespace.QName;

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.model.api.ModelExecuteOptions;
import com.evolveum.midpoint.model.api.ModelPublicConstants;
import com.evolveum.midpoint.model.impl.lens.LensContext;
import com.evolveum.midpoint.model.impl.tasks.simple.SimpleActivityHandler;
import com.evolveum.midpoint.model.impl.util.ModelImplUtils;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.repo.common.activity.ActivityExecutionException;
import com.evolveum.midpoint.repo.common.activity.definition.AbstractWorkDefinition;
import com.evolveum.midpoint.repo.common.activity.definition.ObjectSetSpecificationProvider;
import com.evolveum.midpoint.repo.common.activity.definition.WorkDefinitionFactory.WorkDefinitionSupplier;
import com.evolveum.midpoint.repo.common.task.ActivityReportingOptions;
import com.evolveum.midpoint.repo.common.task.BaseSearchBasedExecutionSpecificsImpl;
import com.evolveum.midpoint.repo.common.task.ItemProcessingRequest;
import com.evolveum.midpoint.repo.common.task.SearchBasedActivityExecution;
import com.evolveum.midpoint.repo.common.task.SearchBasedActivityExecution.SearchBasedSpecificsSupplier;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.task.work.LegacyWorkDefinitionSource;
import com.evolveum.midpoint.schema.util.task.work.ObjectSetUtil;
import com.evolveum.midpoint.schema.util.task.work.WorkDefinitionSource;
import com.evolveum.midpoint.schema.util.task.work.WorkDefinitionWrapper.TypedWorkDefinitionWrapper;
import com.evolveum.midpoint.task.api.RunningTask;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.exception.CommonException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

/**
 * Executes specified deltas on specified set of objects.
 */
@Component
public class RecomputationActivityHandler
        extends SimpleActivityHandler<
            ObjectType,
            RecomputationActivityHandler.MyWorkDefinition,
            RecomputationActivityHandler> {

    private static final String LEGACY_HANDLER_URI = ModelPublicConstants.RECOMPUTE_HANDLER_URI;
    private static final Trace LOGGER = TraceManager.getTrace(RecomputationActivityHandler.class);

    private static final QName DEFAULT_OBJECT_TYPE_FOR_LEGACY_SPEC = UserType.COMPLEX_TYPE;  // This is pre-4.4 behavior
    private static final QName DEFAULT_OBJECT_TYPE_FOR_NEW_SPEC = AssignmentHolderType.COMPLEX_TYPE; // This is more reasonable

    @Override
    protected @NotNull QName getWorkDefinitionTypeName() {
        return RecomputationWorkDefinitionType.COMPLEX_TYPE;
    }

    @Override
    protected @NotNull Class<MyWorkDefinition> getWorkDefinitionClass() {
        return MyWorkDefinition.class;
    }

    @Override
    protected @NotNull WorkDefinitionSupplier getWorkDefinitionSupplier() {
        return MyWorkDefinition::new;
    }

    @Override
    protected @NotNull SearchBasedSpecificsSupplier<ObjectType, MyWorkDefinition, RecomputationActivityHandler> getSpecificSupplier() {
        return MyExecutionSpecifics::new;
    }

    @Override
    protected @NotNull String getLegacyHandlerUri() {
        return LEGACY_HANDLER_URI;
    }

    @Override
    public String getDefaultArchetypeOid() {
        return SystemObjectsType.ARCHETYPE_RECOMPUTATION_TASK.value();
    }

    @Override
    protected @NotNull String getShortName() {
        return "Recomputation";
    }

    @Override
    public String getIdentifierPrefix() {
        return "recomputation";
    }

    static class MyExecutionSpecifics extends
            BaseSearchBasedExecutionSpecificsImpl<ObjectType, MyWorkDefinition, RecomputationActivityHandler> {

        MyExecutionSpecifics(
                @NotNull SearchBasedActivityExecution<ObjectType, MyWorkDefinition, RecomputationActivityHandler, ?> activityExecution) {
            super(activityExecution);
        }

        @Override
        public @NotNull ActivityReportingOptions getDefaultReportingOptions() {
            return super.getDefaultReportingOptions()
                    .enableActionsExecutedStatistics(true);
        }

        @Override
        public boolean processObject(@NotNull PrismObject<ObjectType> object,
                @NotNull ItemProcessingRequest<PrismObject<ObjectType>> request, RunningTask workerTask, OperationResult result)
                throws CommonException, ActivityExecutionException {
            boolean simulate = activityExecution.isPreview();
            String action = simulate ? "Simulated recomputation" : "Recomputation";

            LOGGER.trace("{} of object {}", action, object);

            LensContext<FocusType> syncContext = getActivityHandler().contextFactory.createRecomputeContext(object,
                    getWorkDefinition().getExecutionOptions(), workerTask, result);
            LOGGER.trace("{} of object {}: context:\n{}", action, object, syncContext.debugDumpLazily());

            if (simulate) {
                getActivityHandler().clockwork.previewChanges(syncContext, null, workerTask, result);
            } else {
                getActivityHandler().clockwork.run(syncContext, workerTask, result);
            }
            LOGGER.trace("{} of object {}: {}", action, object, result.getStatus());
            return true;
        }
    }

    public static class MyWorkDefinition extends AbstractWorkDefinition implements ObjectSetSpecificationProvider {

        private final ObjectSetType objects;
        private final ModelExecuteOptions executionOptions;

        MyWorkDefinition(WorkDefinitionSource source) {
            if (source instanceof LegacyWorkDefinitionSource) {
                LegacyWorkDefinitionSource legacy = (LegacyWorkDefinitionSource) source;
                objects = ObjectSetUtil.fromLegacySource(legacy);
                executionOptions = ModelImplUtils.getModelExecuteOptions(legacy.getTaskExtension());
                ObjectSetUtil.applyDefaultObjectType(objects, DEFAULT_OBJECT_TYPE_FOR_LEGACY_SPEC);
            } else {
                RecomputationWorkDefinitionType typedDefinition = (RecomputationWorkDefinitionType)
                        ((TypedWorkDefinitionWrapper) source).getTypedDefinition();
                objects = ObjectSetUtil.fromConfiguration(typedDefinition.getObjects());
                executionOptions = fromModelExecutionOptionsType(typedDefinition.getExecutionOptions());
                ObjectSetUtil.applyDefaultObjectType(objects, DEFAULT_OBJECT_TYPE_FOR_NEW_SPEC);
            }
        }

        @Override
        public ObjectSetType getObjectSetSpecification() {
            return objects;
        }

        public ModelExecuteOptions getExecutionOptions() {
            return executionOptions;
        }

        @Override
        protected void debugDumpContent(StringBuilder sb, int indent) {
            DebugUtil.debugDumpWithLabelLn(sb, "objects", objects, indent+1);
            DebugUtil.debugDumpWithLabelLn(sb, "executionOptions", String.valueOf(executionOptions), indent+1);
        }
    }
}
