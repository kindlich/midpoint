/*
 * Copyright (C) 2010-2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.model.impl.lens.projector.focus.inbounds.prep;

import com.evolveum.axiom.concepts.Lazy;
import com.evolveum.midpoint.model.common.mapping.MappingImpl;
import com.evolveum.midpoint.model.impl.ModelBeans;
import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.path.ItemName;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.schema.processor.ResourceAssociationDefinition;
import com.evolveum.midpoint.schema.processor.ResourceAttributeDefinition;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Maintains a list of mapped items: the items ready to produce prepared {@link MappingImpl} objects.
 */
class MappedItems<F extends FocusType> {

    private static final Trace LOGGER = TraceManager.getTrace(MappedItems.class);

    @NotNull private final Source source;

    @NotNull private final Target<F> target;

    @NotNull private final Context context;

    @NotNull private final List<MappedItem<?, ?, F>> mappedItems = new ArrayList<>();

    /**
     * Definition of `association` container in shadows.
     */
    private final Lazy<PrismContainerDefinition<ShadowAssociationType>> lazyAssociationContainerDefinition =
            Lazy.from(() -> getBeans().prismContext.getSchemaRegistry()
                    .findObjectDefinitionByCompileTimeClass(ShadowType.class)
                    .findContainerDefinition(ShadowType.F_ASSOCIATION));


    /**
     * Definition of `auxiliaryObjectClass` property in shadows.
     */
    private final Lazy<PrismPropertyDefinition<QName>> lazyAuxiliaryObjectClassPropertyDefinition =
            Lazy.from(() -> getBeans().prismContext.getSchemaRegistry()
                    .findObjectDefinitionByCompileTimeClass(ShadowType.class)
                    .findPropertyDefinition(ShadowType.F_AUXILIARY_OBJECT_CLASS));

    MappedItems(@NotNull Source source, @NotNull Target<F> target, @NotNull Context context) {
        this.source = source;
        this.target = target;
        this.context = context;
    }

    private @NotNull ModelBeans getBeans() {
        return context.beans;
    }

    /**
     * Here we create all the mapped items.
     *
     * This excludes special mappings. They are _evaluated_ later. (This is planned to be changed!)
     */
    void createMappedItems() {
        for (QName attributeName : source.resourceObjectDefinition.getNamesOfAttributesWithInboundExpressions()) {
            createAttributeMappingCreationRequest(attributeName);
        }

        // FIXME Remove this temporary check
        if (!source.isClockwork()) {
            LOGGER.trace("Skipping application of special properties and aux object classes because of pre-mapping stage");
            return;
        }

        for (QName associationName : source.resourceObjectDefinition.getNamesOfAssociationsWithInboundExpressions()) {
            createAssociationMappingCreationRequest(associationName);
        }

        if (!source.isProjectionBeingDeleted()) {
            createAuxObjClassesMappingCreationRequest();
        } else {
            // TODO why we are skipping evaluation of aux OCs below?
            LOGGER.trace("Skipping application of aux object class mappings because of projection DELETE delta");
        }
    }

    /**
     * Creates a mapping creation request for mapping(s) for given attribute.
     *
     * @param <T> type of the attribute
     * @see #createAssociationMappingCreationRequest(QName)
     * @see #createAuxObjClassesMappingCreationRequest()
     */
    private <T> void createAttributeMappingCreationRequest(QName attributeName) {

        // 1. Definitions

        //noinspection unchecked
        ResourceAttributeDefinition<T> attributeDefinition =
                (ResourceAttributeDefinition<T>) Objects.requireNonNull(
                        source.resourceObjectDefinition.findAttributeDefinition(attributeName),
                        () -> "No definition for attribute " + attributeName);

        ItemPath attributePath = ItemPath.create(ShadowType.F_ATTRIBUTES, attributeName);
        String itemDescription = "attribute " + attributeName;
        List<MappingType> mappingBeans = attributeDefinition.getInboundMappingBeans();

        // 2. Values

        ItemDelta<PrismPropertyValue<T>, PrismPropertyDefinition<T>> attributeAPrioriDelta = getItemAPrioriDelta(attributePath);
        MappedItem.ItemProvider<PrismPropertyValue<T>, PrismPropertyDefinition<T>> attributeProvider =
                () -> getCurrentAttribute(attributeName);

        // 3. Processing source

        ProcessingMode processingMode = source.getItemProcessingMode(
                itemDescription,
                attributeAPrioriDelta,
                mappingBeans,
                attributeDefinition.isIgnored(LayerType.MODEL),
                attributeDefinition.getLimitations(LayerType.MODEL));
        if (processingMode == ProcessingMode.NONE) {
            return;
        }

        // 4. Mapping creation request

        mappedItems.add(
                new MappedItem<>(
                        source,
                        target,
                        context,
                        mappingBeans,
                        attributePath,
                        itemDescription,
                        attributeAPrioriDelta,
                        attributeDefinition,
                        attributeProvider,
                        null, // postprocessor
                        null, // variable producer
                        processingMode));
    }

    /**
     * Creates a mapping creation request for mapping(s) for given association.
     *
     * The situation is complicated by the fact that all associations are mixed up in `shadow.association` container.
     *
     * @see #createAttributeMappingCreationRequest(QName)
     * @see #createAuxObjClassesMappingCreationRequest()
     */
    private void createAssociationMappingCreationRequest(QName associationName) {

        // 1. Definitions

        ResourceAssociationDefinition associationDefinition =
                Objects.requireNonNull(
                        source.resourceObjectDefinition.findAssociationDefinition(associationName),
                        () -> "No definition for association " + associationName);
        ItemName itemPath = ShadowType.F_ASSOCIATION;
        String itemDescription = "association " + associationName;
        List<MappingType> mappingBeans = associationDefinition.getInboundMappingTypes();

        // 2. Values

        // TODO Shouldn't we filter the apriori delta for specific association? (Instead of passing any association deltas?)
        ItemDelta<PrismContainerValue<ShadowAssociationType>, PrismContainerDefinition<ShadowAssociationType>>
                associationAPrioriDelta = getItemAPrioriDelta(itemPath);
        MappedItem.ItemProvider<PrismContainerValue<ShadowAssociationType>, PrismContainerDefinition<ShadowAssociationType>>
                associationProvider = () -> getCurrentAssociation(associationName);

        // 3. Processing source

        ProcessingMode processingMode = source.getItemProcessingMode(
                itemDescription,
                associationAPrioriDelta,
                mappingBeans,
                associationDefinition.isIgnored(LayerType.MODEL),
                associationDefinition.getLimitations(LayerType.MODEL));
        if (processingMode == ProcessingMode.NONE) {
            return;
        }

        // 4. Mapping creation request

        mappedItems.add(
                new MappedItem<>(
                        source,
                        target,
                        context,
                        mappingBeans,
                        itemPath, // source path (cannot point to specified association name!)
                        itemDescription,
                        associationAPrioriDelta, // a priori delta for all associations - see TO-DO above
                        lazyAssociationContainerDefinition.get(),
                        associationProvider, // association item provider
                        source::resolveInputEntitlements, // postprocessor
                        source::getEntitlementVariableProducer, // so-called variable producer
                        processingMode));
    }

    /**
     * Creates a mapping creation request for mapping(s) for auxiliary object classes.
     *
     * @see #createAttributeMappingCreationRequest(QName)
     * @see #createAttributeMappingCreationRequest(QName)
     */
    private void createAuxObjClassesMappingCreationRequest() {

        // 1. Definitions

        ItemName itemPath = ShadowType.F_AUXILIARY_OBJECT_CLASS;
        String itemDescription = "auxiliary object classes";

        ResourceBidirectionalMappingAndDefinitionType auxiliaryObjectClassMappings =
                source.resourceObjectDefinition.getAuxiliaryObjectClassMappings();
        if (auxiliaryObjectClassMappings == null) {
            return;
        }
        List<MappingType> mappingBeans = auxiliaryObjectClassMappings.getInbound();
        if (mappingBeans.isEmpty()) {
            return;
        }

        // 2. Values

        ItemDelta<PrismPropertyValue<QName>, PrismPropertyDefinition<QName>> itemAPrioriDelta = getItemAPrioriDelta(itemPath);
        MappedItem.ItemProvider<PrismPropertyValue<QName>, PrismPropertyDefinition<QName>> itemProvider =
                this::getCurrentAuxiliaryObjectClasses;

        // 3. Processing source

        ProcessingMode processingMode = source.getItemProcessingMode(
                itemDescription,
                itemAPrioriDelta,
                mappingBeans,
                false, // aux OCs are never ignored
                null); // aux OCs are never unreadable
        if (processingMode == ProcessingMode.NONE) {
            return;
        }

        // 4. Mapping creation request

        // Note that we intentionally ignore a-priori delta for aux OCs. The reason is unknown for me, but
        // this is how it was done before 4.5.
        //
        // We also insist on fetching the full shadow, unless getProcessingSource() has told us that we should not process
        // these mappings.
        //
        // TODO reconsider these irregularities in behavior (comparing with attribute/association mappings).

        mappedItems.add(
                new MappedItem<>(
                        source,
                        target,
                        context,
                        mappingBeans,
                        itemPath,
                        itemDescription,
                        null, // ignoring a priori delta
                        lazyAuxiliaryObjectClassPropertyDefinition.get(),
                        itemProvider,
                        null, // postprocessor
                        null, // variable producer
                        ProcessingMode.ABSOLUTE_STATE));
    }

    /** Returns a-priori delta for given item. */
    private <V extends PrismValue, D extends ItemDefinition<?>> ItemDelta<V, D> getItemAPrioriDelta(ItemPath path) {
        if (source.aPrioriDelta != null) {
            return source.aPrioriDelta.findItemDelta(path);
        } else {
            return null;
        }
    }

    private @Nullable <T> PrismProperty<T> getCurrentAttribute(QName attributeName) {
        if (source.currentShadow != null) {
            return source.currentShadow.findProperty(ItemPath.create(ShadowType.F_ATTRIBUTES, attributeName));
        } else {
            return null;
        }
    }

    private @Nullable PrismContainer<ShadowAssociationType> getCurrentAssociation(QName associationName)
            throws SchemaException {
        LOGGER.trace("Getting filtered associations for {}", associationName);
        PrismContainer<ShadowAssociationType> currentAssociation = source.currentShadow != null ?
                source.currentShadow.findContainer(ShadowType.F_ASSOCIATION) : null;

        if (currentAssociation != null) {
            PrismContainer<ShadowAssociationType> filteredAssociations = currentAssociation.getDefinition().instantiate();
            Collection<PrismContainerValue<ShadowAssociationType>> filteredAssociationValues = currentAssociation.getValues().stream()
                    .filter(rVal -> associationName.equals(rVal.asContainerable().getName()))
                    .map(PrismContainerValue::clone)
                    .collect(Collectors.toList());
            context.beans.prismContext.adopt(filteredAssociations);
            // Make sure all the modified/cloned associations have proper definition
            filteredAssociations.applyDefinition(currentAssociation.getDefinition(), false);
            filteredAssociations.addAll(filteredAssociationValues);
            return filteredAssociations;
        } else {
            return null;
        }
    }

    private @Nullable PrismProperty<QName> getCurrentAuxiliaryObjectClasses() {
        if (source.currentShadow != null) {
            return source.currentShadow.findProperty(ShadowType.F_AUXILIARY_OBJECT_CLASS);
        } else {
            return null;
        }
    }

    @NotNull List<MappedItem<?, ?, F>> getMappedItems() {
        return mappedItems;
    }

    boolean isFullStateRequired() {
        for (MappedItem<?, ?, ?> mappedItem : mappedItems) {
            if (mappedItem.doesRequireAbsoluteState()) {
                LOGGER.trace("The mapping(s) for {} require the absolute state, we'll load it if it will be necessary",
                        mappedItem.itemDescription);
                return true;
            }
        }
        return false;
    }
}
