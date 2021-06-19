/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.repo.common.activity.state;

import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.repo.common.activity.ActivityExecutionException;
import com.evolveum.midpoint.repo.common.activity.ActivityTree;
import com.evolveum.midpoint.repo.common.task.CommonTaskBeans;
import com.evolveum.midpoint.repo.common.task.task.GenericTaskExecution;

import com.evolveum.midpoint.schema.result.OperationResult;

import com.evolveum.midpoint.schema.util.task.ActivityPath;

import com.evolveum.midpoint.task.api.RunningTask;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.CommonException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ActivityStatePersistenceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ActivityStateType;

import com.evolveum.midpoint.xml.ns._public.common.common_3.TaskActivityStateType;

import com.evolveum.midpoint.xml.ns._public.common.common_3.TaskType;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.evolveum.midpoint.schema.result.OperationResultStatus.FATAL_ERROR;
import static com.evolveum.midpoint.task.api.TaskRunResult.TaskRunResultStatus.PERMANENT_ERROR;

import static com.evolveum.midpoint.util.MiscUtil.argCheck;

public class TreeStatePurger {

    private static final Trace LOGGER = TraceManager.getTrace(TreeStatePurger.class);

    @NotNull private final ActivityTree activityTree; // TODO reconsider this
    @NotNull private final GenericTaskExecution taskExecution;
    @NotNull private final CommonTaskBeans beans;

    public TreeStatePurger(@NotNull ActivityTree activityTree, @NotNull GenericTaskExecution taskExecution,
            @NotNull CommonTaskBeans beans) {
        this.activityTree = activityTree;
        this.taskExecution = taskExecution;
        this.beans = beans;
    }

    /**
     * Purges state from current task and its subtasks.
     *
     * * Pre: task is an execution root
     * * Post: task is refreshed
     */
    public void purge(OperationResult result) throws ActivityExecutionException {
        try {
            purgeTask(ActivityPath.empty(), taskExecution.getRunningTask(), result);
            purgeSubtasks(taskExecution.getRunningTask(), result);
        } catch (CommonException e) {
            throw new ActivityExecutionException("Couldn't purge activity tree state", FATAL_ERROR, PERMANENT_ERROR, e);
        }
    }

    private void purgeTask(ActivityPath activityPath, Task task, OperationResult result) throws CommonException {
        new TaskStatePurger(activityPath, task)
                .doPurge(result);
    }

    private void purgeSubtasks(Task parent, OperationResult result) throws CommonException {
        List<? extends Task> subtasks = parent.listSubtasks(result);
        for (Task subtask : subtasks) {
            TaskActivityStateType taskActivityState = subtask.getActivitiesStateOrClone();
            if (taskActivityState == null) {
                LOGGER.error("Non-activity related subtask {} of {}. Please resolve manually.", subtask, parent);
                continue;
            }
            purgeTask(
                    ActivityPath.fromBean(taskActivityState.getLocalRoot()),
                    subtask,
                    result);
        }
    }

    /**
     * Purges activity states in a single task.
     */
    private class TaskStatePurger {

        @NotNull private final ActivityPath activityPath;
        @NotNull private final Task task;
        final TaskActivityStateType taskActivityState;
        @NotNull private final List<ItemDelta<?, ?>> deltas = new ArrayList<>();

        private TaskStatePurger(@NotNull ActivityPath activityPath,
                @NotNull Task task) {

            this.activityPath = activityPath;
            this.task = task;
            this.taskActivityState = task.getActivitiesStateOrClone();
        }

        private void doPurge(OperationResult result) throws CommonException {
            if (taskActivityState == null || taskActivityState.getActivity() == null) {
                return;
            }
            doPurge(Context.root(activityPath, taskActivityState));
            if (!deltas.isEmpty()) {
                beans.repositoryService.modifyObject(TaskType.class, task.getOid(), deltas, result);
                if (task instanceof RunningTask) {
                    task.refresh(result);
                }
            }
        }

        private void doPurge(Context ctx) throws CommonException {
            LOGGER.info("doPurge called for {}", ctx);
            if (isTransient(ctx.currentState)) {
                removeCurrentState(ctx);
            } else {
                purgeCurrentState(ctx);
                for (ActivityStateType child : ctx.currentState.getActivity()) {
                    doPurge(
                            ctx.forChild(child));
                }
            }
        }

        private void removeCurrentState(Context ctx) throws CommonException {
            if (ctx.isLocalRoot()) {
                deleteFromSingle(ctx);
            } else {
                deleteFromMulti(ctx, ctx.currentState.getId());
            }
        }

        private void deleteFromSingle(Context ctx) throws SchemaException {
            LOGGER.info("Deleting from single: task = {}, activity path = '{}', item path = '{}'",
                    task, ctx.currentActivityPath, ctx.currentStateItemPath); // TODO trace

            deltas.addAll(
                    beans.prismContext.deltaFor(TaskType.class)
                            .item(ctx.currentStateItemPath).replace()
                            .asItemDeltas());
        }

        private void deleteFromMulti(Context ctx, Long id) throws SchemaException {
            LOGGER.info("Deleting from multi: task = {}, activity path = '{}', item path = '{}' with id = {}",
                    task, ctx.currentActivityPath, ctx.currentHolderItemPath, id); // TODO trace

            argCheck(id != null, "Null activity state PCV id in task %s activity path '%s' item path '%s'",
                    task, ctx.currentActivityPath, ctx.currentHolderItemPath);
            deltas.addAll(
                    beans.prismContext.deltaFor(TaskType.class)
                            .item(ctx.currentHolderItemPath).delete(new ActivityStateType().id(id))
                            .asItemDeltas());
        }

        private void purgeCurrentState(Context ctx) throws SchemaException {
            LOGGER.info("Purging from multi: task = {}, activity path = '{}', item path = '{}'",
                    task, ctx.currentActivityPath, ctx.currentStateItemPath); // TODO trace

            deltas.addAll(
                    beans.prismContext.deltaFor(TaskType.class)
                            .item(ctx.currentStateItemPath.append(ActivityStateType.F_REALIZATION_STATE)).replace()
                            .item(ctx.currentStateItemPath.append(ActivityStateType.F_RESULT_STATUS)).replace()
                            .item(ctx.currentStateItemPath.append(ActivityStateType.F_RESULT_STATUS)).replace()
                            .item(ctx.currentStateItemPath.append(ActivityStateType.F_BUCKETING)).replace()
                            .asItemDeltas());

            if (ctx.currentState.getPersistence() == ActivityStatePersistenceType.PERPETUAL_EXCEPT_STATISTICS) {
                deltas.addAll(
                        beans.prismContext.deltaFor(TaskType.class)
                                .item(ctx.currentStateItemPath.append(ActivityStateType.F_PROGRESS)).replace()
                                .item(ctx.currentStateItemPath.append(ActivityStateType.F_STATISTICS)).replace()
                                .asItemDeltas());
            }
        }
    }

    private boolean isTransient(ActivityStateType state) {
        return state.getPersistence() == null || state.getPersistence() == ActivityStatePersistenceType.SINGLE_REALIZATION;
    }

    private static class Context {
        @NotNull private final ActivityPath currentActivityPath;
        @NotNull private final ItemPath currentHolderItemPath;
        @NotNull private final ItemPath currentStateItemPath;
        @NotNull private final Object currentStateHolder;
        @NotNull private final ActivityStateType currentState;

        private Context(@NotNull ActivityPath currentActivityPath, @NotNull ItemPath currentHolderItemPath,
                @NotNull ItemPath currentStateItemPath, @NotNull Object currentStateHolder,
                @NotNull ActivityStateType currentState) {
            this.currentActivityPath = currentActivityPath;
            this.currentHolderItemPath = currentHolderItemPath;
            this.currentStateItemPath = currentStateItemPath;
            this.currentStateHolder = currentStateHolder;
            this.currentState = currentState;
        }

        public static @NotNull Context root(ActivityPath path, @NotNull TaskActivityStateType taskActivityState) {
            return new Context(
                    path,
                    TaskType.F_ACTIVITY_STATE,
                    ItemPath.create(TaskType.F_ACTIVITY_STATE, TaskActivityStateType.F_ACTIVITY),
                    taskActivityState,
                    taskActivityState.getActivity());
        }

        public @NotNull Context forChild(ActivityStateType child) {
            return new Context(
                    currentActivityPath.append(child.getIdentifier()),
                    currentStateItemPath,
                    currentStateItemPath.append(currentState.getId(), ActivityStateType.F_ACTIVITY),
                    currentState,
                    child);
        }

        boolean isLocalRoot() {
            return currentStateHolder instanceof TaskActivityStateType;
        }

        @Override
        public String toString() {
            return "Context{" +
                    "currentActivityPath=" + currentActivityPath +
                    ", currentHolderItemPath=" + currentHolderItemPath +
                    ", currentStateItemPath=" + currentStateItemPath +
                    ", currentStateHolder:" + currentStateHolder.getClass().getSimpleName() +
                    ", currentState=" + currentState +
                    '}';
        }
    }
}
