/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.repo.common.task;

import com.evolveum.midpoint.audit.api.AuditService;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.match.MatchingRuleRegistry;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.repo.common.activity.TaskActivityManager;
import com.evolveum.midpoint.repo.common.activity.definition.WorkDefinitionFactory;
import com.evolveum.midpoint.repo.common.activity.handlers.ActivityHandlerRegistry;
import com.evolveum.midpoint.repo.common.expression.ExpressionFactory;
import com.evolveum.midpoint.repo.common.task.work.BucketingManager;
import com.evolveum.midpoint.repo.common.task.work.segmentation.BucketContentFactoryGenerator;
import com.evolveum.midpoint.repo.common.util.OperationExecutionRecorderForTasks;
import com.evolveum.midpoint.schema.SchemaService;
import com.evolveum.midpoint.schema.cache.CacheConfigurationManager;
import com.evolveum.midpoint.task.api.LightweightIdentifierGenerator;
import com.evolveum.midpoint.task.api.TaskManager;
import com.evolveum.midpoint.task.api.Tracer;

import com.google.common.base.MoreObjects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class CommonTaskBeans {

    @Autowired public ActivityHandlerRegistry activityHandlerRegistry;
    @Autowired public TaskManager taskManager;
    @Autowired public Tracer tracer;
    @Autowired public CacheConfigurationManager cacheConfigurationManager;
    @Autowired @Qualifier("cacheRepositoryService") public RepositoryService repositoryService;
    @Autowired @Qualifier("repositoryService") public RepositoryService plainRepositoryService;
    @Autowired public AuditService auditService;
    @Autowired public PrismContext prismContext;
    @Autowired public SchemaService schemaService;
    @Autowired public MatchingRuleRegistry matchingRuleRegistry;
    @Autowired public OperationExecutionRecorderForTasks operationExecutionRecorder;
    @Autowired public LightweightIdentifierGenerator lightweightIdentifierGenerator;
    @Autowired public WorkDefinitionFactory workDefinitionFactory;

    @Autowired public BucketingManager bucketingManager;
    @Autowired public TaskActivityManager activityManager;

    @Autowired public BucketContentFactoryGenerator contentFactoryCreator;
    @Autowired public ExpressionFactory expressionFactory;

    @Autowired(required = false) private AdvancedActivityExecutionSupport advancedActivityExecutionSupport;

    public AdvancedActivityExecutionSupport getAdvancedActivityExecutionSupport() {
        return MoreObjects.firstNonNull(
                advancedActivityExecutionSupport,
                NoOpAdvancedActivityExecutionSupport.INSTANCE);
    }
}
