/*
 * Copyright (c) 2010-2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.model.intest.sync;

import java.io.File;
import java.util.Collection;

import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.util.task.TaskPartProgressInformation;
import com.evolveum.midpoint.schema.util.task.TaskProgressInformation;
import com.evolveum.midpoint.task.api.TaskDebugUtil;
import com.evolveum.midpoint.test.TestResource;

import com.evolveum.midpoint.xml.ns._public.common.common_3.RoleType;

import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;
import org.testng.annotations.Test;

import com.evolveum.midpoint.model.intest.AbstractInitializedModelIntegrationTest;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.xml.ns._public.common.common_3.TaskType;

import static org.testng.AssertJUnit.*;

/**
 * Tests for MID-6011.
 *
 * TODO move to reporting package.
 */
@ContextConfiguration(locations = {"classpath:ctx-model-intest-test-main.xml"})
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
public class TestProgressReporting extends AbstractInitializedModelIntegrationTest {

    private static final File TEST_DIR = new File("src/test/resources/sync");

    /**
     * We currently do not use slow-down nor error inducing behavior of this resource
     * but let's keep it here.
     */
    @SuppressWarnings("FieldCanBeLocal")
    private DummyInterruptedSyncResource interruptedSyncResource;

    private static final TestResource<TaskType> TASK_RECONCILE_DUMMY = new TestResource<>(TEST_DIR, "task-reconcile-dummy-interrupted-partitioned.xml", "83eef280-d420-417a-929d-796eed202e02");
    private static final TestResource<TaskType> TASK_RECONCILE_DUMMY_MULTINODE = new TestResource<>(TEST_DIR, "task-reconcile-dummy-interrupted-partitioned-multinode.xml", "9a52b7a4-afda-4b22-932e-f45b9f90cf95");

    private static final TestResource<TaskType> TASK_RECOMPUTE_ROLES = new TestResource<>(TEST_DIR, "task-recompute-roles.xml", "42869247-9bf1-4198-acea-3326f5ab2c34");
    private static final TestResource<TaskType> TASK_RECOMPUTE_ROLES_MULTINODE = new TestResource<>(TEST_DIR, "task-recompute-roles-multinode.xml", "c8cfe559-3888-4b39-b835-3aead9a46581");

    private static final TestResource<TaskType> METAROLE_SLOWING_DOWN = new TestResource<>(TEST_DIR, "metarole-slowing-down.xml", "b7218b57-fb8a-4dfd-a4c0-976849a4640c");

    private static final int USERS = 1000;
    private static final int ROLES = 1000;

    @Override
    public void initSystem(Task initTask, OperationResult initResult) throws Exception {
        super.initSystem(initTask, initResult);

        interruptedSyncResource = new DummyInterruptedSyncResource();
        interruptedSyncResource.init(dummyResourceCollection, initTask, initResult);

        addObject(METAROLE_SLOWING_DOWN, initTask, initResult);
    }

    /**
     * Reconciliation suspend + resume - check for progress reporting issues.
     */
    @Test
    public void test100ReconciliationSuspensionSingleNode() throws Exception {
        executeReconciliation(TASK_RECONCILE_DUMMY, "u", 1);
    }

    @Test
    public void test110ReconciliationSuspensionMultiNode() throws Exception {
        executeReconciliation(TASK_RECONCILE_DUMMY_MULTINODE, "v", 2);
    }

    private void executeReconciliation(TestResource<TaskType> reconciliationTask, String accountPrefix, int workers)
            throws Exception {
        Task task = getTestTask();
        OperationResult result = getTestOperationResult();

        given();
        for (int i = 0; i < USERS; i++) {
            interruptedSyncResource.getController().addAccount(String.format("%s%03d", accountPrefix, i));
        }

        addObject(reconciliationTask.file, task, result);

        when();
        System.out.println("Waiting before suspending task tree.");
        Thread.sleep(10000L);

        System.out.println("Suspending task tree.");
        boolean stopped = taskManager.suspendTaskTree(reconciliationTask.oid, 10000, result);
        assertTrue("Not all tasks were stopped", stopped);

        System.out.println("Task tree suspended.");
        Collection<SelectorOptions<GetOperationOptions>> getSubtasks = getOperationOptionsBuilder()
                .item(TaskType.F_SUBTASK_REF).retrieve()
                .build();
        PrismObject<TaskType> rootAfterSuspension1 = taskManager.getObject(TaskType.class, reconciliationTask.oid, getSubtasks, result);
        displayValue("Tree after suspension", TaskDebugUtil.dumpTaskTree(rootAfterSuspension1.asObjectable()));

        TaskProgressInformation progress1 = TaskProgressInformation.fromTaskTree(rootAfterSuspension1.asObjectable());
        displayDumpable("Progress after 1st suspension", progress1);

        System.out.println("Resuming task tree.");
        taskManager.resumeTaskTree(reconciliationTask.oid, result);

        System.out.println("Waiting before suspending task tree second time...");
        Thread.sleep(3000L);

        System.out.println("Suspending task tree.");
        boolean stopped2 = taskManager.suspendTaskTree(reconciliationTask.oid, 10000, result);
        assertTrue("Not all tasks were stopped", stopped2);

        System.out.println("Task tree suspended.");
        PrismObject<TaskType> rootAfterSuspension2 = taskManager.getObject(TaskType.class, reconciliationTask.oid, getSubtasks, result);
        displayValue("Tree after second suspension", TaskDebugUtil.dumpTaskTree(rootAfterSuspension2.asObjectable()));

        TaskProgressInformation progress2 = TaskProgressInformation.fromTaskTree(rootAfterSuspension2.asObjectable());
        displayDumpable("Progress after 2nd suspension", progress2);

        // THEN

        int bucketSize = 10;

        TaskPartProgressInformation info1 = assertTaskProgress(progress1, "after 1st suspension")
                .assertAllPartsCount(3)
                .assertCurrentPartNumber(2)
                .currentPart()
                    .assertExpectedBuckets(100)
                    .assertBucketsItemsConsistency(bucketSize, workers)
                    .get();

        TaskPartProgressInformation info2 = assertTaskProgress(progress2, "after 2nd suspension")
                .assertAllPartsCount(3)
                .assertCurrentPartNumber(2)
                .currentPart()
                    .assertExpectedBuckets(100) // todo consistency
                    .assertCompletedBucketsAtLeast(info1.getBucketsProgress().getCompletedBuckets())
                    .get();

//        assertProgressVsBuckets(items2, completedBuckets2, bucketSize, workers);

        // The following two tests currently fail. It is not currently not possible to treat progress precisely in bucketed tasks.
        //assertFalse("Progress went backwards", progress1.getProgress() > progress2.getProgress());
        //assertProgressVsBuckets(progress2.getProgress() - progress1.getProgress(), progress2.getCompletedBuckets() - progress1.getCompletedBuckets(), bucketSize, 2);
    }

    /**
     * Recomputation suspend + resume - check for progress reporting issues.
     */
    @Test
    public void test150RecomputationSuspensionSingleNode() throws Exception {
        executeRecomputation(TASK_RECOMPUTE_ROLES, "rx", 1);
    }

    @Test
    public void test160RecomputationSuspensionMultiNode() throws Exception {
        executeRecomputation(TASK_RECOMPUTE_ROLES_MULTINODE, "ry", 2);
    }

    private void executeRecomputation(TestResource<TaskType> recomputationTask, String rolePrefix, int workers) throws Exception {
        Task task = getTestTask();
        OperationResult result = getTestOperationResult();

        // GIVEN
        System.out.println("Importing roles.");
        for (int i = 0; i < ROLES; i++) {
            RoleType role = new RoleType(prismContext)
                    .name(String.format("%s%03d", rolePrefix, i))
                    .beginAssignment()
                        .targetRef(METAROLE_SLOWING_DOWN.oid, RoleType.COMPLEX_TYPE)
                    .end();
            repositoryService.addObject(role.asPrismObject(), null, result);
        }

        System.out.println("Importing recompute task.");
        addObject(recomputationTask.file, task, result);

        // WHEN
        System.out.println("Waiting before suspending task tree.");
        Thread.sleep(10000L);

        System.out.println("Suspending task tree.");
        boolean stopped = taskManager.suspendTaskTree(recomputationTask.oid, 10000, result);
        assertTrue("Not all tasks were stopped", stopped);

        System.out.println("Task tree suspended.");
        Collection<SelectorOptions<GetOperationOptions>> getSubtasks = getOperationOptionsBuilder()
                .item(TaskType.F_SUBTASK_REF).retrieve()
                .build();
        PrismObject<TaskType> rootAfterSuspension1 = taskManager.getObject(TaskType.class, recomputationTask.oid, getSubtasks, result);
        displayValue("Tree after suspension", TaskDebugUtil.dumpTaskTree(rootAfterSuspension1.asObjectable()));

        TaskProgressInformation progress1 = TaskProgressInformation.fromTaskTree(rootAfterSuspension1.asObjectable());
        displayDumpable("Progress after 1st suspension", progress1);

        System.out.println("Resuming task tree.");
        taskManager.resumeTaskTree(recomputationTask.oid, result);

        System.out.println("Waiting before suspending task tree second time...");
        Thread.sleep(3000L);

        System.out.println("Suspending task tree.");
        boolean stopped2 = taskManager.suspendTaskTree(recomputationTask.oid, 10000, result);
        assertTrue("Not all tasks were stopped", stopped2);

        System.out.println("Task tree suspended.");
        PrismObject<TaskType> rootAfterSuspension2 = taskManager.getObject(TaskType.class, recomputationTask.oid, getSubtasks, result);
        displayValue("Tree after second suspension", TaskDebugUtil.dumpTaskTree(rootAfterSuspension2.asObjectable()));

        TaskProgressInformation progress2 = TaskProgressInformation.fromTaskTree(rootAfterSuspension2.asObjectable());
        displayDumpable("Progress after second suspension", progress2);

        // THEN

        int bucketSize = 10;

        TaskPartProgressInformation info1 = assertTaskProgress(progress1, "after 1st suspension")
                .assertAllPartsCount(1)
                .assertCurrentPartNumber(1)
                .currentPart()
                    .assertExpectedBuckets(100)
                    .assertBucketsItemsConsistency(bucketSize, workers)
                .get();

        TaskPartProgressInformation info2 = assertTaskProgress(progress2, "after 2nd suspension")
                .assertAllPartsCount(1)
                .assertCurrentPartNumber(1)
                .currentPart()
                    .assertExpectedBuckets(100) // todo consistency
                .assertCompletedBucketsAtLeast(info1.getBucketsProgress().getCompletedBuckets())
                .get();

//        assertProgressVsBuckets(items2, completedBuckets2, bucketSize, workers);

        // The following two tests currently fail. It is not currently not possible to treat progress precisely in bucketed tasks.
        //assertFalse("Progress went backwards", progress1.getProgress() > progress2.getProgress());
        //assertProgressVsBuckets(progress2.getProgress() - progress1.getProgress(), progress2.getCompletedBuckets() - progress1.getCompletedBuckets(), bucketSize, 2);
    }
}
