/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.provisioning.impl.shadows.task;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.provisioning.api.ProvisioningService;
import com.evolveum.midpoint.provisioning.impl.shadows.ShadowsFacade;
import com.evolveum.midpoint.repo.common.task.definition.WorkDefinitionFactory;
import com.evolveum.midpoint.repo.common.task.execution.ActivityExecution;
import com.evolveum.midpoint.repo.common.task.execution.ActivityInstantiationContext;
import com.evolveum.midpoint.repo.common.task.handlers.ActivityHandler;
import com.evolveum.midpoint.repo.common.task.handlers.ActivityHandlerRegistry;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.xml.ns._public.common.common_3.PropagationWorkDefinitionType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SystemObjectsType;

/**
 * TODO
 */
@Component
public class PropagationActivityHandler implements ActivityHandler<PropagationWorkDefinition> {

    private static final String LEGACY_HANDLER_URI = SchemaConstants.NS_PROVISIONING_TASK + "/propagation/handler-3";
    private static final String ARCHETYPE_OID = SystemObjectsType.ARCHETYPE_SYSTEM_TASK.value(); // TODO

    @Autowired WorkDefinitionFactory workDefinitionFactory;
    @Autowired ActivityHandlerRegistry handlerRegistry;
    @Autowired ProvisioningService provisioningService;
    @Autowired ShadowsFacade shadowsFacade;

    @PostConstruct
    public void register() {
        handlerRegistry.register(PropagationWorkDefinitionType.COMPLEX_TYPE, LEGACY_HANDLER_URI,
                PropagationWorkDefinition.class, PropagationWorkDefinition::new, this);
    }

    @PreDestroy
    public void unregister() {
        handlerRegistry.unregister(PropagationWorkDefinitionType.COMPLEX_TYPE, LEGACY_HANDLER_URI,
                PropagationWorkDefinition.class);
    }

    @Override
    public @NotNull ActivityExecution createExecution(@NotNull ActivityInstantiationContext<PropagationWorkDefinition> context,
            @NotNull OperationResult result) {
        return new PropagationActivityExecution(context, this);
    }
}
