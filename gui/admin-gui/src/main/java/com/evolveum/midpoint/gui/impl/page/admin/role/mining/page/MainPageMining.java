/*
 * Copyright (C) 2010-2023 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.gui.impl.page.admin.role.mining.page;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

import com.evolveum.midpoint.gui.impl.error.ErrorPanel;

import com.github.openjson.JSONObject;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
import org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.export.AbstractExportableColumn;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.StringResourceModel;
import org.apache.wicket.request.mapper.parameter.PageParameters;

import com.evolveum.midpoint.authentication.api.authorization.AuthorizationAction;
import com.evolveum.midpoint.authentication.api.authorization.PageDescriptor;
import com.evolveum.midpoint.authentication.api.authorization.Url;
import com.evolveum.midpoint.gui.api.GuiStyleConstants;
import com.evolveum.midpoint.gui.api.component.MainObjectListPanel;
import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.impl.component.icon.CompositedIconBuilder;
import com.evolveum.midpoint.gui.impl.page.admin.role.mining.details.objects.ParentClusterBasicDetailsPanel;
import com.evolveum.midpoint.gui.impl.page.admin.role.mining.perform.ExecuteClusteringPanel;
import com.evolveum.midpoint.model.api.AssignmentObjectRelation;
import com.evolveum.midpoint.model.api.authentication.CompiledObjectCollectionView;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.security.api.AuthorizationConstants;
import com.evolveum.midpoint.web.component.AjaxButton;
import com.evolveum.midpoint.web.component.data.column.ColumnMenuAction;
import com.evolveum.midpoint.web.component.data.column.ObjectNameColumn;
import com.evolveum.midpoint.web.component.form.MidpointForm;
import com.evolveum.midpoint.web.component.menu.cog.ButtonInlineMenuItem;
import com.evolveum.midpoint.web.component.menu.cog.InlineMenuItem;
import com.evolveum.midpoint.web.component.menu.cog.InlineMenuItemAction;
import com.evolveum.midpoint.web.component.util.SelectableBean;
import com.evolveum.midpoint.web.page.admin.PageAdmin;
import com.evolveum.midpoint.web.session.UserProfileStorage.TableId;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ArchetypeType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.RoleAnalysisSession;

import static com.evolveum.midpoint.gui.impl.page.admin.role.mining.utils.ClusterObjectUtils.*;

@PageDescriptor(
        urls = {
                @Url(mountUrl = "/admin/mainMining", matchUrlForSecurity = "/admin/mainMining")
        },
        action = {
                @AuthorizationAction(actionUri = AuthorizationConstants.AUTZ_UI_USERS_ALL_URL,
                        label = "PageAdminUsers.auth.usersAll.label",
                        description = "PageAdminUsers.auth.usersAll.description"),
                @AuthorizationAction(actionUri = AuthorizationConstants.AUTZ_UI_USERS_URL,
                        label = "PageUsers.auth.users.label",
                        description = "PageUsers.auth.users.description"),
                @AuthorizationAction(actionUri = AuthorizationConstants.AUTZ_UI_USERS_VIEW_URL,
                        label = "PageUsers.auth.users.view.label",
                        description = "PageUsers.auth.users.view.description")
        })
public class MainPageMining extends PageAdmin {
    @Serial private static final long serialVersionUID = 1L;

    private static final String ID_MAIN_FORM = "mainForm";
    private static final String ID_TABLE = "table";

    public MainPageMining(PageParameters params) {
        super(params);
    }

    @Override
    protected void onInitialize() {
        super.onInitialize();
        initLayout();
    }

    private MainObjectListPanel<RoleAnalysisSession> getTable() {
        return (MainObjectListPanel<RoleAnalysisSession>) get(createComponentPath(ID_MAIN_FORM, ID_TABLE));
    }

    private InlineMenuItem createDeleteInlineMenu() {
        return new ButtonInlineMenuItem(createStringResource("MainObjectListPanel.menu.delete")) {
            @Override
            public CompositedIconBuilder getIconCompositedBuilder() {
                return getDefaultCompositedIconBuilder(GuiStyleConstants.CLASS_ICON_TRASH);
            }

            @Override
            public InlineMenuItemAction initAction() {
                return new ColumnMenuAction<SelectableBean<ArchetypeType>>() {
                    @Override
                    public void onClick(AjaxRequestTarget target) {

                        List<SelectableBean<RoleAnalysisSession>> selectedObjects = getTable().getSelectedObjects();
                        OperationResult result = new OperationResult("Delete clusters objects");
                        if (selectedObjects == null || selectedObjects.size() == 0) {
                            try {
                                deleteAllRoleAnalysisObjects(result, ((PageBase) getPage()));
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        } else {

                            for (SelectableBean<RoleAnalysisSession> selectedObject : selectedObjects) {
                                try {
                                    String parentOid = selectedObject.getValue().asPrismObject().getOid();
                                    List<String> roleAnalysisClusterRef = selectedObject.getValue().getRoleAnalysisClusterRef();
                                    deleteRoleAnalysisObjects(result, (PageBase) getPage(), parentOid, roleAnalysisClusterRef);
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        }

                    }
                };
            }

            @Override
            public IModel<String> getConfirmationMessageModel() {
                String actionName = createStringResource("MainObjectListPanel.message.deleteAction").getString();
                return getTable().getConfirmationMessageModel((ColumnMenuAction<?>) getAction(), actionName);
            }
        };
    }

    protected void initLayout() {

        Form<?> mainForm = new MidpointForm<>(ID_MAIN_FORM);
        add(mainForm);

        if (!isNativeRepo()) {
            mainForm.add(new ErrorPanel(ID_TABLE,
                    () -> getString("PageAdmin.menu.top.resources.templates.list.nonNativeRepositoryWarning")));
            return;
        }
        MainObjectListPanel<RoleAnalysisSession> table = new MainObjectListPanel<>(ID_TABLE, RoleAnalysisSession.class) {

            @Override
            protected List<InlineMenuItem> createInlineMenu() {
                List<InlineMenuItem> menuItems = new ArrayList<>();
                menuItems.add(MainPageMining.this.createDeleteInlineMenu());
                return menuItems;
            }

            @Override
            protected List<IColumn<SelectableBean<RoleAnalysisSession>, String>> createDefaultColumns() {

                List<IColumn<SelectableBean<RoleAnalysisSession>, String>> columns = new ArrayList<>();

                IColumn<SelectableBean<RoleAnalysisSession>, String> column;

                column = new ObjectNameColumn<>(createStringResource("ObjectType.name")) {

                    @Serial private static final long serialVersionUID = 1L;

                    @Override
                    public void onClick(AjaxRequestTarget target, IModel<SelectableBean<RoleAnalysisSession>> rowModel) {

                        ParentClusterBasicDetailsPanel detailsPanel = new ParentClusterBasicDetailsPanel(((PageBase) getPage()).getMainPopupBodyId(),
                                Model.of("TO DO: details"), rowModel) {
                            @Override
                            public void onClose(AjaxRequestTarget ajaxRequestTarget) {
                                super.onClose(ajaxRequestTarget);
                            }
                        };
                        ((PageBase) getPage()).showMainPopup(detailsPanel, target);
                    }
                };

                columns.add(column);

                column = new AbstractExportableColumn<>(getHeaderTitle("mode")) {

                    @Override
                    public void populateItem(Item<ICellPopulator<SelectableBean<RoleAnalysisSession>>> cellItem,
                            String componentId, IModel<SelectableBean<RoleAnalysisSession>> model) {
                        cellItem.add(new Label(componentId,
                                model.getObject().getValue() != null && model.getObject().getValue().getProcessMode() != null ?
                                        model.getObject().getValue().getProcessMode() : null));
                    }

                    @Override
                    public IModel<String> getDataModel(IModel<SelectableBean<RoleAnalysisSession>> rowModel) {
                        return Model.of("");
                    }

                };
                columns.add(column);

                column = new AbstractExportableColumn<>(getHeaderTitle("similarity.option")) {

                    @Override
                    public void populateItem(Item<ICellPopulator<SelectableBean<RoleAnalysisSession>>> cellItem,
                            String componentId, IModel<SelectableBean<RoleAnalysisSession>> model) {
                        cellItem.add(new Label(componentId,
                                model.getObject().getValue() != null && model.getObject().getValue().getOptions() != null ?
                                        new JSONObject(model.getObject().getValue().getOptions()).getString("similarity") : null));
                    }

                    @Override
                    public IModel<String> getDataModel(IModel<SelectableBean<RoleAnalysisSession>> rowModel) {
                        return Model.of("");
                    }

                };
                columns.add(column);

                column = new AbstractExportableColumn<>(getHeaderTitle("intersection.option")) {

                    @Override
                    public void populateItem(Item<ICellPopulator<SelectableBean<RoleAnalysisSession>>> cellItem,
                            String componentId, IModel<SelectableBean<RoleAnalysisSession>> model) {
                        cellItem.add(new Label(componentId,
                                model.getObject().getValue() != null && model.getObject().getValue().getOptions() != null ?
                                        new JSONObject(model.getObject().getValue().getOptions()).getString("minIntersection") : null));
                    }

                    @Override
                    public IModel<String> getDataModel(IModel<SelectableBean<RoleAnalysisSession>> rowModel) {
                        return Model.of("");
                    }

                };
                columns.add(column);

                column = new AbstractExportableColumn<>(getHeaderTitle("minAssign.option")) {

                    @Override
                    public void populateItem(Item<ICellPopulator<SelectableBean<RoleAnalysisSession>>> cellItem,
                            String componentId, IModel<SelectableBean<RoleAnalysisSession>> model) {
                        cellItem.add(new Label(componentId,
                                model.getObject().getValue() != null && model.getObject().getValue().getOptions() != null ?
                                        new JSONObject(model.getObject().getValue().getOptions()).getString("assignThreshold") : null));
                    }

                    @Override
                    public IModel<String> getDataModel(IModel<SelectableBean<RoleAnalysisSession>> rowModel) {
                        return Model.of("");
                    }

                };
                columns.add(column);

                column = new AbstractExportableColumn<>(getHeaderTitle("group.option")) {

                    @Override
                    public void populateItem(Item<ICellPopulator<SelectableBean<RoleAnalysisSession>>> cellItem,
                            String componentId, IModel<SelectableBean<RoleAnalysisSession>> model) {
                        cellItem.add(new Label(componentId,
                                model.getObject().getValue() != null && model.getObject().getValue().getOptions() != null ?
                                        new JSONObject(model.getObject().getValue().getOptions()).getString("minGroup") : null));
                    }

                    @Override
                    public IModel<String> getDataModel(IModel<SelectableBean<RoleAnalysisSession>> rowModel) {
                        return Model.of("");
                    }

                };
                columns.add(column);

                column = new AbstractExportableColumn<>(getHeaderTitle("density")) {

                    @Override
                    public void populateItem(Item<ICellPopulator<SelectableBean<RoleAnalysisSession>>> cellItem,
                            String componentId, IModel<SelectableBean<RoleAnalysisSession>> model) {

                        cellItem.add(new Label(componentId, Model.of(model.getObject().getValue().getMeanDensity())));

                    }

                    @Override
                    public IModel<String> getDataModel(IModel<SelectableBean<RoleAnalysisSession>> rowModel) {
                        return Model.of("");
                    }

                };
                columns.add(column);

                column = new AbstractExportableColumn<>(getHeaderTitle("consist")) {

                    @Override
                    public void populateItem(Item<ICellPopulator<SelectableBean<RoleAnalysisSession>>> cellItem,
                            String componentId, IModel<SelectableBean<RoleAnalysisSession>> model) {
                        cellItem.add(new Label(componentId,
                                model.getObject().getValue() != null && model.getObject().getValue().getElementConsist() != null ?
                                        model.getObject().getValue().getElementConsist() : null));
                    }

                    @Override
                    public IModel<String> getDataModel(IModel<SelectableBean<RoleAnalysisSession>> rowModel) {
                        return Model.of("");
                    }

                };
                columns.add(column);

                column = new AbstractColumn<>(
                        createStringResource("RoleMining.button.title.load")) {

                    @Override
                    public void populateItem(Item<ICellPopulator<SelectableBean<RoleAnalysisSession>>> cellItem,
                            String componentId, IModel<SelectableBean<RoleAnalysisSession>> model) {
                        if (model.getObject().getValue() != null && model.getObject().getValue().getName() != null) {

                            AjaxButton ajaxButton = new AjaxButton(componentId,
                                    Model.of(String.valueOf(model.getObject().getValue().getName()))) {
                                @Override
                                public void onClick(AjaxRequestTarget ajaxRequestTarget) {
                                    PageParameters params = new PageParameters();
                                    params.set(PageCluster.PARAMETER_MODE, model.getObject().getValue().getProcessMode());
                                    params.set(PageCluster.PARAMETER_PARENT_OID, model.getObject().getValue().getOid());

                                    ((PageBase) getPage()).navigateToNext(PageCluster.class, params);
                                }
                            };

                            ajaxButton.add(AttributeAppender.replace("class", " btn btn-primary btn-sm d-flex "
                                    + "justify-content-center align-items-center"));
                            ajaxButton.add(new AttributeAppender("style", " width:100px; height:20px"));
                            ajaxButton.setOutputMarkupId(true);

                            cellItem.add(ajaxButton);

                        } else {
                            cellItem.add(new Label(componentId,
                                    (Integer) null));
                        }
                    }

                    @Override
                    public boolean isSortable() {
                        return true;
                    }

                    @Override
                    public String getSortProperty() {
                        return RoleAnalysisSession.F_NAME.toString();
                    }
                };
                columns.add(column);

                return columns;
            }

            @Override
            protected void newObjectPerformed(AjaxRequestTarget target, AssignmentObjectRelation relation,
                    CompiledObjectCollectionView collectionView) {

                ExecuteClusteringPanel detailsPanel = new ExecuteClusteringPanel(((PageBase) getPage()).getMainPopupBodyId(),
                        Model.of("New cluster")) {
                    @Override
                    public void onClose(AjaxRequestTarget ajaxRequestTarget) {
                        super.onClose(ajaxRequestTarget);
                    }
                };
                ((PageBase) getPage()).showMainPopup(detailsPanel, target);
            }

            @Override
            protected TableId getTableId() {
                return TableId.TABLE_USERS;
            }

            @Override
            protected String getNothingSelectedMessage() {
                return getString("pageMining.message.nothingSelected");
            }

            @Override
            protected String getConfirmMessageKeyForMultiObject() {
                return "pageMining.message.confirmationMessageForMultipleObject";
            }

            @Override
            protected String getConfirmMessageKeyForSingleObject() {
                return "pagemMining.message.confirmationMessageForSingleObject";
            }
        };
        table.setOutputMarkupId(true);
        mainForm.add(table);

    }

    @Override
    protected List<String> pageParametersToBeRemoved() {
        return List.of(PageBase.PARAMETER_SEARCH_BY_NAME);
    }

    protected StringResourceModel getHeaderTitle(String identifier) {
        return createStringResource("RoleMining.cluster.table.column.header." + identifier);
    }
}
