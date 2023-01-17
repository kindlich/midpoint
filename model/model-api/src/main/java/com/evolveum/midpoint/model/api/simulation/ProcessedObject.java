/*
 * Copyright (C) 2010-2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.model.api.simulation;

import static com.evolveum.midpoint.prism.polystring.PolyString.getOrig;
import static com.evolveum.midpoint.util.MiscUtil.argCheck;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ChangeType;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.schema.DeltaConvertor;
import com.evolveum.midpoint.schema.constants.ObjectTypes;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.util.DebugDumpable;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import com.evolveum.prism.xml.ns._public.types_3.PolyStringType;

/**
 * Parsed analogy of {@link SimulationResultProcessedObjectType}.
 *
 * TEMPORARY
 */
public class ProcessedObject<O extends ObjectType> implements DebugDumpable {

    private static final Map<ChangeType, ObjectProcessingStateType> DELTA_TO_PROCESSING_STATE =
            new ImmutableMap.Builder<ChangeType, ObjectProcessingStateType>()
                    .put(ChangeType.ADD, ObjectProcessingStateType.ADDED)
                    .put(ChangeType.DELETE, ObjectProcessingStateType.DELETED)
                    .put(ChangeType.MODIFY, ObjectProcessingStateType.MODIFIED)
                    .build();

    private final String oid; // TODO may be null?
    @NotNull private final Class<O> type;
    private final PolyStringType name;
    @NotNull private final ObjectProcessingStateType state;
    @NotNull private final Collection<String> eventTags;

    /** Present only when parsed from the bean. */
    @NotNull private final Map<String, BigDecimal> metricValues;

    /** Complete information on the tags (optional) */
    private Map<String, TagType> eventTagsMap;

    @Nullable private final O before;
    @Nullable private final O after;
    @Nullable private final ObjectDelta<O> delta;

    private ProcessedObject(
            String oid,
            @NotNull Class<O> type,
            PolyStringType name,
            @NotNull ObjectProcessingStateType state,
            @NotNull Collection<String> eventTags,
            @NotNull Map<String, BigDecimal> metricValues,
            @Nullable O before,
            @Nullable O after,
            @Nullable ObjectDelta<O> delta) {
        this.oid = oid;
        this.type = type;
        this.name = name;
        this.state = state;
        this.eventTags = eventTags;
        this.metricValues = metricValues;
        this.before = before;
        this.after = after;
        this.delta = delta;
    }

    public static <O extends ObjectType> ProcessedObject<O> parse(@NotNull SimulationResultProcessedObjectType bean)
            throws SchemaException {
        Class<?> type = PrismContext.get().getSchemaRegistry().determineClassForTypeRequired(bean.getType());
        argCheck(ObjectType.class.isAssignableFrom(type), "Type is not an ObjectType: %s", type);
        //noinspection unchecked
        return new ProcessedObject<>(
                bean.getOid(),
                (Class<O>) type,
                bean.getName(),
                MiscUtil.argNonNull(bean.getState(), () -> "No processing state in " + bean),
                getEventTagsOids(bean),
                getMetricValues(bean),
                (O) bean.getBefore(),
                (O) bean.getAfter(),
                DeltaConvertor.createObjectDeltaNullable(bean.getDelta()));
    }

    private static Map<String, BigDecimal> getMetricValues(SimulationResultProcessedObjectType bean) {
        return bean.getMetricValue().stream()
                .collect(Collectors.toMap(
                        ProcessedObjectSimulationMetricValueType::getIdentifier,
                        ProcessedObjectSimulationMetricValueType::getValue));
    }

    private static Set<String> getEventTagsOids(SimulationResultProcessedObjectType bean) {
        return bean.getEventTagRef().stream()
                .map(ref -> ref.getOid())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public static <O extends ObjectType> ProcessedObject<?> create(
            O stateBefore, ObjectDelta<O> delta, Collection<String> eventTags) throws SchemaException {

        Class<O> type;
        if (stateBefore != null) {
            //noinspection unchecked
            type = (Class<O>) stateBefore.getClass();
        } else if (delta != null) {
            type = delta.getObjectTypeClass();
        } else {
            return null;
        }

        @Nullable O stateAfter = computeStateAfter(stateBefore, delta);
        @Nullable O anyState = MiscUtil.getFirstNonNull(stateAfter, stateBefore);

        // We may consider returning null if anyState is null (meaning that the delta is MODIFY/DELETE with null stateBefore)

        return new ProcessedObject<>(
                determineOid(anyState, delta),
                type,
                anyState != null ? anyState.getName() : null,
                delta != null ?
                        DELTA_TO_PROCESSING_STATE.get(delta.getChangeType()) :
                        ObjectProcessingStateType.UNMODIFIED,
                eventTags,
                Map.of(),
                stateBefore,
                stateAfter,
                delta);
    }

    public String getOid() {
        return oid;
    }

    public @NotNull Class<O> getType() {
        return type;
    }

    public @Nullable PolyStringType getName() {
        return name;
    }

    public @NotNull ObjectProcessingStateType getState() {
        return state;
    }

    public @NotNull Collection<String> getEventTags() {
        return eventTags;
    }

    public @Nullable Map<String, TagType> getEventTagsMap() {
        return eventTagsMap;
    }

    public void setEventTagsMap(Map<String, TagType> eventTagsMap) {
        this.eventTagsMap = eventTagsMap;
    }

    @Nullable
    public O getBefore() {
        return before;
    }

    @Nullable
    public O getAfter() {
        return after;
    }

    @Nullable
    public ObjectDelta<O> getDelta() {
        return delta;
    }

    private static <O extends ObjectType> O computeStateAfter(O stateBefore, ObjectDelta<O> delta) throws SchemaException {
        if (stateBefore == null) {
            if (delta == null) {
                return null;
            } else if (delta.isAdd()) {
                return delta.getObjectToAdd().asObjectable();
            } else {
                // We may relax this before release - we may still store the delta
                throw new IllegalStateException("No initial state and MODIFY/DELETE delta? Delta: " + delta);
            }
        } else if (delta != null) {
            //noinspection unchecked
            PrismObject<O> clone = (PrismObject<O>) stateBefore.asPrismObject().clone();
            delta.applyTo(clone);
            return clone.asObjectable();
        } else {
            return stateBefore;
        }
    }

    private static <O extends ObjectType> @Nullable String determineOid(O anyState, ObjectDelta<O> delta) {
        if (anyState != null) {
            String oid = anyState.getOid();
            if (oid != null) {
                return oid;
            }
        }
        if (delta != null) {
            return delta.getOid();
        }
        return null;
    }

    public SimulationResultProcessedObjectType toBean() throws SchemaException {
        SimulationResultProcessedObjectType processedObject = new SimulationResultProcessedObjectType()
                .oid(oid)
                .type(
                        PrismContext.get().getSchemaRegistry().determineTypeForClass(type))
                .name(name)
                .state(state)
                .before(prepareObjectForStorage(before))
                .after(prepareObjectForStorage(after))
                .delta(DeltaConvertor.toObjectDeltaType(delta));
        List<ObjectReferenceType> eventTagRef = processedObject.getEventTagRef();
        eventTags.forEach(
                oid -> eventTagRef.add(ObjectTypeUtil.createObjectRef(oid, ObjectTypes.TAG)));
        List<ProcessedObjectSimulationMetricValueType> metricValues = processedObject.getMetricValue();
        this.metricValues.forEach(
                (id, value) -> metricValues.add(
                        new ProcessedObjectSimulationMetricValueType()
                                .identifier(id)
                                .value(value)));
        return processedObject;
    }

    private ObjectType prepareObjectForStorage(@Nullable O original) {
        if (original != null) {
            ObjectType clone = original.clone();
            clone.setOid(null); // this is currently not parseable
            clone.setVersion(null); // this is currently not parseable
            clone.setFetchResult(null); // can contain a lot of garbage
            return clone;
        } else {
            return null;
        }
    }

    @Override
    public String debugDump(int indent) {
        StringBuilder sb = DebugUtil.createTitleStringBuilder(getClass(), indent);
        sb.append(" of ").append(type.getSimpleName());
        sb.append(" ").append(oid);
        sb.append(" (").append(name).append("): ");
        sb.append(state);
        sb.append("\n");
        DebugUtil.debugDumpWithLabelLn(sb, "tags", getEventTagsDebugDump(), indent + 1);
        DebugUtil.debugDumpWithLabelLn(sb, "metrics", metricValues, indent + 1);
        DebugUtil.debugDumpWithLabelLn(sb, "before", before, indent + 1);
        DebugUtil.debugDumpWithLabel(sb, "delta", delta, indent + 1);
        return sb.toString();
    }

    @Override
    public String toString() {
        return "ProcessedObject{" +
                "oid='" + oid + '\'' +
                ", type=" + type +
                ", name=" + name +
                ", state=" + state +
                ", eventTags=" + getEventTagsDebugDump() +
                ", metrics=" + metricValues +
                ", before=" + before +
                ", after=" + after +
                ", delta=" + delta +
                '}';
    }

    private Collection<String> getEventTagsDebugDump() {
        if (eventTagsMap != null) {
            return eventTagsMap.entrySet().stream()
                    .map(e -> getTagDebugDump(e))
                    .collect(Collectors.toList());
        } else {
            return eventTags;
        }
    }

    private String getTagDebugDump(Map.Entry<String, TagType> tagEntry) {
        String tagOid = tagEntry.getKey();
        TagType tag = tagEntry.getValue();
        if (tag != null) {
            return getOrig(tag.getName()) + " (" + tagOid + ")";
        } else {
            return tagOid;
        }
    }

    public @Nullable O getAfterOrBefore() {
        return MiscUtil.getFirstNonNull(after, before);
    }
}
