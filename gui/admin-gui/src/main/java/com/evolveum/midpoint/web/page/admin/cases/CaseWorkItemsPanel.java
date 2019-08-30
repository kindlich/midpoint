/*
 * Copyright (c) 2010-2019 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.evolveum.midpoint.web.page.admin.cases;

import com.evolveum.midpoint.gui.api.GuiStyleConstants;
import com.evolveum.midpoint.gui.api.component.BasePanel;
import com.evolveum.midpoint.gui.api.component.ContainerableListPanel;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.api.util.WebModelServiceUtils;
import com.evolveum.midpoint.gui.impl.prism.PrismContainerValueWrapper;
import com.evolveum.midpoint.prism.PrismConstants;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.CaseTypeUtil;
import com.evolveum.midpoint.schema.util.WorkItemId;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.web.component.data.column.*;
import com.evolveum.midpoint.web.component.menu.cog.ButtonInlineMenuItem;
import com.evolveum.midpoint.web.component.menu.cog.InlineMenuItem;
import com.evolveum.midpoint.web.component.menu.cog.InlineMenuItemAction;
import com.evolveum.midpoint.web.page.admin.workflow.PageAttorneySelection;
import com.evolveum.midpoint.web.session.PageStorage;
import com.evolveum.midpoint.web.session.UserProfileStorage;
import com.evolveum.midpoint.web.util.OnePageParameterEncoder;
import com.evolveum.midpoint.wf.util.ApprovalUtils;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import org.apache.commons.lang.StringUtils;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.mapper.parameter.PageParameters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Created by honchar
 */
public class CaseWorkItemsPanel extends BasePanel<CaseWorkItemType> {

    private static final long serialVersionUID = 1L;

    private static final String DOT_CLASS = CaseWorkItemsPanel.class.getName() + ".";
    private static final String OPERATION_LOAD_POWER_DONOR_OBJECT = DOT_CLASS + "loadPowerDonorObject";
    private static final String OPERATION_COMPLETE_WORK_ITEM = DOT_CLASS + "completeWorkItem";

    private static final String ID_WORKITEMS_TABLE = "workitemsTable";

    public enum View {
        FULL_LIST,				// selectable, full information
        DASHBOARD, 				// not selectable, reduced info (on dashboard)
        ITEMS_FOR_PROCESS		// work items for a process
    }

    private View view;
    private  PageParameters pageParameters;

    public CaseWorkItemsPanel(String id, View view){
        super(id);
        this.view = view;
    }

    public CaseWorkItemsPanel(String id, View view, PageParameters pageParameters){
        super(id);
        this.view = view;
        this.pageParameters = pageParameters;
    }

    @Override
    protected void onInitialize(){
        super.onInitialize();
        initLayout();
    }

    private void initLayout(){
        ContainerableListPanel workItemsPanel = new ContainerableListPanel(ID_WORKITEMS_TABLE,
                UserProfileStorage.TableId.PAGE_CASE_WORK_ITEMS_PANEL) {
            @Override
            protected Class getType() {
                return CaseWorkItemType.class;
            }

            @Override
            protected PageStorage getPageStorage() {
                return CaseWorkItemsPanel.this.getPageBase().getSessionStorage().getWorkItemStorage();
            }

            @Override
            protected List<IColumn<PrismContainerValueWrapper<CaseWorkItemType>, String>> initColumns() {
                return CaseWorkItemsPanel.this.initColumns();
            }

            @Override
            protected ObjectFilter getCustomFilter(){
                return getCaseWorkItemsFilter();
            }

            @Override
            protected Collection<SelectorOptions<GetOperationOptions>> getQueryOptions() {
                return CaseWorkItemsPanel.this.getPageBase().getOperationOptionsBuilder()
                        .item(AbstractWorkItemType.F_ASSIGNEE_REF).resolve()
                        .item(PrismConstants.T_PARENT, CaseType.F_OBJECT_REF).resolve()
                        .item(PrismConstants.T_PARENT, CaseType.F_TARGET_REF).resolve()
                        .build();
            }

            @Override
            protected boolean hideFooterIfSinglePage(){
                return View.DASHBOARD.equals(view);
            }

            @Override
            protected boolean isSearchVisible(){
                return !View.DASHBOARD.equals(view);
            }

        };
        workItemsPanel.setOutputMarkupId(true);
        add(workItemsPanel);
    }

    private List<IColumn<PrismContainerValueWrapper<CaseWorkItemType>, String>> initColumns(){
        List<IColumn<PrismContainerValueWrapper<CaseWorkItemType>, String>> columns = new ArrayList<>();

        if (View.FULL_LIST.equals(view)) {
            columns.add(new CheckBoxHeaderColumn<>());
        }
        columns.add(new IconColumn<PrismContainerValueWrapper<CaseWorkItemType>>(Model.of("")) {

            private static final long serialVersionUID = 1L;

            @Override
            protected DisplayType getIconDisplayType(IModel<PrismContainerValueWrapper<CaseWorkItemType>> rowModel) {
                return WebComponentUtil.createDisplayType(WebComponentUtil.createDefaultBlackIcon(CaseWorkItemType.COMPLEX_TYPE));
            }

        });
        columns.add(new LinkColumn<PrismContainerValueWrapper<CaseWorkItemType>>(createStringResource("PolicyRulesPanel.nameColumn")){
            private static final long serialVersionUID = 1L;

            @Override
            protected IModel<String> createLinkModel(IModel<PrismContainerValueWrapper<CaseWorkItemType>> rowModel) {
                return Model.of(ColumnUtils.unwrapRowModel(rowModel).getName());
            }

            @Override
            public boolean isEnabled(IModel<PrismContainerValueWrapper<CaseWorkItemType>> rowModel) {
                //TODO should we check any authorization?
                return true;
            }

            @Override
            public void onClick(AjaxRequestTarget target, IModel<PrismContainerValueWrapper<CaseWorkItemType>> rowModel) {
                PageParameters workItemPageParameters = new PageParameters();
                CaseWorkItemType caseWorkItemType = rowModel.getObject().getRealValue();
                CaseType parentCase = CaseTypeUtil.getCase(caseWorkItemType);
                WorkItemId workItemId = WorkItemId.create(parentCase != null ? parentCase.getOid() : "", caseWorkItemType.getId());
                workItemPageParameters.add(OnePageParameterEncoder.PARAMETER, workItemId.asString());
                if (StringUtils.isNotEmpty(getPowerDonorOidParameterValue())){
                    workItemPageParameters.add(PageAttorneySelection.PARAMETER_DONOR_OID, getPowerDonorOidParameterValue());
                }
                CaseWorkItemsPanel.this.getPageBase().navigateToNext(PageCaseWorkItem.class, workItemPageParameters);
            }
        });

        columns.addAll(ColumnUtils.getDefaultWorkItemColumns(getPageBase(), View.FULL_LIST.equals(view)));
        if (View.FULL_LIST.equals(view)) {
            columns.add(new InlineMenuButtonColumn<>(createRowActions(), getPageBase()));
        }
        return columns;
    }

    protected List<InlineMenuItem> createRowActions() {
        List<InlineMenuItem> menu = new ArrayList<>();

        menu.add(new ButtonInlineMenuItem(createStringResource("pageWorkItem.button.reject")) {
            private static final long serialVersionUID = 1L;

            @Override
            public InlineMenuItemAction initAction() {
                return new ColumnMenuAction<PrismContainerValueWrapper<CaseWorkItemType>>() {

                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        workItemActionPerformed(getRowModel(), false, target);
                    }
                };
            }

            @Override
            public IModel<Boolean> getEnabled() {
                IModel<PrismContainerValueWrapper<CaseWorkItemType>> rowModel = ((ColumnMenuAction<PrismContainerValueWrapper<CaseWorkItemType>>)getAction()).getRowModel();
                if (rowModel != null && rowModel.getObject() != null && rowModel.getObject().getRealValue() != null){
                    CaseWorkItemType workItem = rowModel.getObject().getRealValue();
                    return Model.of(!CaseTypeUtil.isClosed(CaseTypeUtil.getCase(workItem)));
                } else {
                    return super.getEnabled();
                }
            }

            @Override
            public IModel<String> getConfirmationMessageModel(){
                return createStringResource("CaseWorkItemsPanel.confirmWorkItemsRejectAction");
            }

            @Override
            public String getButtonIconCssClass(){
                return GuiStyleConstants.CLASS_ICON_NO_OBJECTS;
            }
        });
        menu.add(new ButtonInlineMenuItem(createStringResource("pageWorkItem.button.approve")) {
            private static final long serialVersionUID = 1L;

            @Override
            public InlineMenuItemAction initAction() {
                return new ColumnMenuAction<PrismContainerValueWrapper<CaseWorkItemType>>() {

                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        workItemActionPerformed(getRowModel(), true, target);
                    }
                };
            }

            @Override
            public String getButtonIconCssClass(){
                return GuiStyleConstants.CLASS_ICON_ACTIVATION_ACTIVE;
            }

            @Override
            public IModel<Boolean> getEnabled() {
                IModel<PrismContainerValueWrapper<CaseWorkItemType>> rowModel = ((ColumnMenuAction<PrismContainerValueWrapper<CaseWorkItemType>>)getAction()).getRowModel();
                if (rowModel != null && rowModel.getObject() != null && rowModel.getObject().getRealValue() != null){
                    CaseWorkItemType workItem = rowModel.getObject().getRealValue();
                    return Model.of(!CaseTypeUtil.isClosed(CaseTypeUtil.getCase(workItem)));
                } else {
                    return super.getEnabled();
                }
            }

            @Override
            public IModel<String> getConfirmationMessageModel(){
                return createStringResource("CaseWorkItemsPanel.confirmWorkItemsApproveAction");
            }
        });

        return menu;
    }

    private void workItemActionPerformed(IModel<PrismContainerValueWrapper<CaseWorkItemType>> rowModel, boolean approved,
                                         AjaxRequestTarget target){
        List<PrismContainerValueWrapper<CaseWorkItemType>> selectedWorkItems = new ArrayList<>();
        if (rowModel == null) {
            ContainerableListPanel<CaseWorkItemType> tablePanel = getContainerableListPanel();
            selectedWorkItems.addAll(tablePanel.getProvider().getSelectedData());
        } else {
            selectedWorkItems.addAll(Arrays.asList(rowModel.getObject()));
        }

        if (selectedWorkItems.size() == 0){
            warn(getString("CaseWorkItemsPanel.noWorkItemIsSelected"));
            target.add(getPageBase().getFeedbackPanel());
            return;
        }
        Task task = CaseWorkItemsPanel.this.getPageBase().createSimpleTask(OPERATION_LOAD_POWER_DONOR_OBJECT);
        OperationResult result = new OperationResult(OPERATION_LOAD_POWER_DONOR_OBJECT);
        final PrismObject<UserType> powerDonor = StringUtils.isNotEmpty(getPowerDonorOidParameterValue()) ?
                WebModelServiceUtils.loadObject(UserType.class, getPowerDonorOidParameterValue(),
                        CaseWorkItemsPanel.this.getPageBase(), task, result) : null;
        selectedWorkItems.forEach(workItemToReject -> {
            WebComponentUtil.workItemApproveActionPerformed(target, workItemToReject.getRealValue(),
                    new AbstractWorkItemOutputType(getPrismContext()).outcome(ApprovalUtils.toUri(approved)),
                    null, powerDonor, approved,  OPERATION_COMPLETE_WORK_ITEM, CaseWorkItemsPanel.this.getPageBase());
        });

        result.computeStatusComposite();
        WebComponentUtil.clearProviderCache(getContainerableListPanel().getProvider());

        target.add(getContainerableListPanel());

    }

    private ContainerableListPanel<CaseWorkItemType> getContainerableListPanel(){
        return (ContainerableListPanel<CaseWorkItemType>) get(ID_WORKITEMS_TABLE);
    }

    protected ObjectFilter getCaseWorkItemsFilter(){
        return null;
    }

    private String getPowerDonorOidParameterValue(){
        if (pageParameters != null && pageParameters.get(PageAttorneySelection.PARAMETER_DONOR_OID) != null){
            return pageParameters.get(PageAttorneySelection.PARAMETER_DONOR_OID).toString();
        }
        return null;
    }
}
