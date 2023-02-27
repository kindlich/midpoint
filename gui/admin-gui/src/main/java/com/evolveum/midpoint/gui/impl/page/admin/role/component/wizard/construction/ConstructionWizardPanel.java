/*
 * Copyright (C) 2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.impl.page.admin.role.component.wizard.construction;

import com.evolveum.midpoint.gui.api.component.wizard.WizardModel;
import com.evolveum.midpoint.gui.api.component.wizard.WizardPanel;
import com.evolveum.midpoint.gui.api.component.wizard.WizardStep;
import com.evolveum.midpoint.gui.api.prism.wrapper.PrismContainerValueWrapper;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.impl.component.wizard.AbstractWizardPanel;
import com.evolveum.midpoint.gui.impl.component.wizard.WizardPanelHelper;
import com.evolveum.midpoint.gui.impl.page.admin.assignmentholder.FocusDetailsModels;
import com.evolveum.midpoint.gui.impl.page.admin.resource.ResourceDetailsModel;
import com.evolveum.midpoint.gui.impl.page.admin.resource.component.wizard.objectType.attributeMapping.AttributeOutboundStepPanel;
import com.evolveum.midpoint.gui.impl.page.admin.resource.component.wizard.objectType.credentials.CredentialsWizardPanel;
import com.evolveum.midpoint.gui.impl.page.admin.resource.component.wizard.objectType.credentials.PasswordStepPanel;
import com.evolveum.midpoint.gui.impl.page.admin.role.component.wizard.ApplicationRoleWizardPanel;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.util.annotation.Experimental;
import com.evolveum.midpoint.web.model.PrismContainerWrapperModel;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import org.apache.commons.lang3.StringUtils;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.model.IModel;

import java.util.ArrayList;
import java.util.List;

/**
 * @author lskublik
 */

public class ConstructionWizardPanel<AR extends AbstractRoleType> extends AbstractWizardPanel<AssignmentType, FocusDetailsModels<AR>> {

    public ConstructionWizardPanel(String id, WizardPanelHelper<AssignmentType, FocusDetailsModels<AR>> helper) {
        super(id, helper);
    }

    protected void initLayout() {
        add(createWizardFragment(new WizardPanel(
                getIdOfWizardPanel(),
                new WizardModel(createConstructionSteps(getValueModel())))));
    }

    private void showConstructionWizard(
            AjaxRequestTarget target,
            IModel<PrismContainerValueWrapper<AssignmentType>> valueModel,
            String stepId) {
        WizardModel wizardModel = new WizardModel(createConstructionSteps(valueModel));
        if (StringUtils.isNotEmpty(stepId)) {
            wizardModel.setActiveStepById(stepId);
        }
        showWizardFragment(
                target,
                new WizardPanel(getIdOfWizardPanel(), wizardModel));
    }

    private List<WizardStep> createConstructionSteps(IModel<PrismContainerValueWrapper<AssignmentType>> valueModel) {
        List<WizardStep> steps = new ArrayList<>();

        ConstructionResourceStepPanel<AR> selectResource =
                new ConstructionResourceStepPanel<>(getHelper().getDetailsModel(), valueModel) {

                    @Override
                    protected void onExitPerformed(AjaxRequestTarget target) {
                        super.onExitPerformed(target);
                        ConstructionWizardPanel.this.onExitPerformed(target);
                    }
                };

        steps.add(selectResource);

        steps.add(new ConstructionResourceObjectTypeStepPanel<>(getHelper().getDetailsModel(), selectResource.getValueModel()){
            @Override
            protected void onExitPerformed(AjaxRequestTarget target) {
                super.onExitPerformed(target);
                ConstructionWizardPanel.this.onExitPerformed(target);
            }
        });

        steps.add(new ConstructionGroupStepPanel<>(getHelper().getDetailsModel(), selectResource.getValueModel()){
            @Override
            protected void onExitPerformed(AjaxRequestTarget target) {
                super.onExitPerformed(target);
                ConstructionWizardPanel.this.onExitPerformed(target);
            }
        });

        steps.add( new ConstructionOutboundMappingsStepPanel<>(getHelper().getDetailsModel(), selectResource.getValueModel()) {
            @Override
            protected void inEditOutboundValue(IModel<PrismContainerValueWrapper<MappingType>> rowModel, AjaxRequestTarget target) {
                showOutboundAttributeMappingWizardFragment(target, rowModel, selectResource.getValueModel());
            }

            @Override
            protected void onExitPerformed(AjaxRequestTarget target) {
                super.onExitPerformed(target);
                ConstructionWizardPanel.this.onExitPerformed(target);
            }

            @Override
            protected void onSubmitPerformed(AjaxRequestTarget target) {
//                if (!isSavedAfterWizard()) {
//                    onExitPerformed(target);
//                    return;
//                }
                OperationResult result = ConstructionWizardPanel.this.onSavePerformed(target);
                if (result != null && !result.isError()) {
//                    WebComponentUtil.createToastForUpdateObject(target, RoleType.COMPLEX_TYPE);
                    onExitPerformed(target);
                }
            }

            @Override
            protected IModel<String> getSubmitLabelModel() {
                if (isSavedAfterWizard()) {
                    return super.getSubmitLabelModel();
                }
                return getPageBase().createStringResource("WizardPanel.confirm");
            }
        });

        return steps;
    }

    private void showOutboundAttributeMappingWizardFragment(
            AjaxRequestTarget target,
            IModel<PrismContainerValueWrapper<MappingType>> rowModel,
            IModel<PrismContainerValueWrapper<AssignmentType>> valueModel) {
        showWizardFragment(
                target,
                new WizardPanel(getIdOfWizardPanel(), new WizardModel(createOutboundAttributeMappingSteps(rowModel, valueModel))));
    }

    private List<WizardStep> createOutboundAttributeMappingSteps(
            IModel<PrismContainerValueWrapper<MappingType>> rowModel,
            IModel<PrismContainerValueWrapper<AssignmentType>> valueModel) {
        List<WizardStep> steps = new ArrayList<>();
        steps.add(new AttributeOutboundStepPanel<>(getAssignmentHolderModel(), rowModel) {
            @Override
            protected void onExitPerformed(AjaxRequestTarget target) {
                showConstructionWizard(target, valueModel, ConstructionOutboundMappingsStepPanel.PANEL_TYPE);
            }
        });
        return steps;
    }
}
