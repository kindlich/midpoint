/*
 * Copyright (C) 2010-2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.impl.page.admin.abstractrole.component;

import com.evolveum.midpoint.gui.api.GuiStyleConstants;
import com.evolveum.midpoint.gui.api.component.ChooseMemberPopup;
import com.evolveum.midpoint.gui.api.component.MainObjectListPanel;
import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.api.util.GuiDisplayTypeUtil;
import com.evolveum.midpoint.gui.impl.component.icon.CompositedIcon;
import com.evolveum.midpoint.gui.impl.component.icon.CompositedIconBuilder;
import com.evolveum.midpoint.gui.impl.component.icon.IconCssStyle;
import com.evolveum.midpoint.gui.impl.page.admin.AbstractObjectMainPanel;
import com.evolveum.midpoint.gui.impl.page.admin.assignmentholder.FocusDetailsModels;
import com.evolveum.midpoint.model.api.AssignmentCandidatesSpecification;
import com.evolveum.midpoint.model.api.AssignmentObjectRelation;
import com.evolveum.midpoint.model.api.authentication.CompiledObjectCollectionView;
import com.evolveum.midpoint.prism.PrismConstants;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismReferenceDefinition;
import com.evolveum.midpoint.prism.path.ItemName;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.util.PolyStringUtils;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.constants.ExpressionConstants;
import com.evolveum.midpoint.schema.constants.ObjectTypes;
import com.evolveum.midpoint.schema.constants.RelationTypes;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.DisplayableValue;
import com.evolveum.midpoint.util.QNameUtil;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.application.PanelDisplay;
import com.evolveum.midpoint.web.application.PanelInstance;
import com.evolveum.midpoint.web.application.PanelInstances;
import com.evolveum.midpoint.web.application.PanelType;
import com.evolveum.midpoint.web.component.AjaxIconButton;
import com.evolveum.midpoint.web.component.CompositedIconButtonDto;
import com.evolveum.midpoint.web.component.MultiFunctinalButtonDto;
import com.evolveum.midpoint.web.component.data.SelectableBeanObjectDataProvider;
import com.evolveum.midpoint.web.component.dialog.ChooseFocusTypeAndRelationDialogPanel;
import com.evolveum.midpoint.web.component.dialog.ConfigureTaskConfirmationPanel;
import com.evolveum.midpoint.web.component.form.MidpointForm;
import com.evolveum.midpoint.web.component.menu.cog.ButtonInlineMenuItem;
import com.evolveum.midpoint.web.component.menu.cog.InlineMenuItem;
import com.evolveum.midpoint.web.component.menu.cog.InlineMenuItemAction;
import com.evolveum.midpoint.web.component.search.ContainerTypeSearchItem;
import com.evolveum.midpoint.web.component.search.refactored.Search;
import com.evolveum.midpoint.web.component.search.SearchFactory;
import com.evolveum.midpoint.web.component.search.SearchValue;
import com.evolveum.midpoint.web.component.util.SelectableBean;
import com.evolveum.midpoint.web.page.admin.configuration.component.HeaderMenuAction;
import com.evolveum.midpoint.web.page.admin.roles.AbstractRoleCompositedSearchItem;
import com.evolveum.midpoint.web.page.admin.roles.SearchBoxConfigurationHelper;
import com.evolveum.midpoint.web.security.GuiAuthorizationConstants;
import com.evolveum.midpoint.web.session.MemberPanelStorage;
import com.evolveum.midpoint.web.session.PageStorage;
import com.evolveum.midpoint.web.session.SessionStorage;
import com.evolveum.midpoint.web.session.UserProfileStorage;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import com.evolveum.prism.xml.ns._public.types_3.PolyStringType;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.wicket.Component;
import org.apache.wicket.RestartResponseException;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.export.AbstractExportableColumn;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.StringResourceModel;
import org.jetbrains.annotations.NotNull;

import javax.xml.namespace.QName;
import java.util.*;

@PanelType(name = "members")
@PanelInstances(instances = {
        @PanelInstance(identifier = "roleMembers",
                applicableForType = RoleType.class,
                applicableForOperation = OperationTypeType.MODIFY,
                display = @PanelDisplay(label = "pageRole.members", icon = GuiStyleConstants.CLASS_GROUP_ICON, order = 80)),
        @PanelInstance(identifier = "roleGovernance",
                applicableForType = RoleType.class,
                applicableForOperation = OperationTypeType.MODIFY,
                display = @PanelDisplay(label = "pageRole.governance", icon = GuiStyleConstants.CLASS_GROUP_ICON, order = 90)),
        @PanelInstance(identifier = "serviceMembers",
                applicableForType = ServiceType.class,
                applicableForOperation = OperationTypeType.MODIFY,
                display = @PanelDisplay(label = "pageRole.members", icon = GuiStyleConstants.CLASS_GROUP_ICON, order = 80)),
        @PanelInstance(identifier = "serviceGovernance",
                applicableForType = ServiceType.class,
                applicableForOperation = OperationTypeType.MODIFY,
                display = @PanelDisplay(label = "pageRole.governance", icon = GuiStyleConstants.CLASS_GROUP_ICON, order = 90))
})
@PanelDisplay(label = "Members", order = 60)
public class AbstractRoleMemberPanel<R extends AbstractRoleType> extends AbstractObjectMainPanel<R, FocusDetailsModels<R>> {

    private static final long serialVersionUID = 1L;

    public enum QueryScope { // temporarily public because of migration
        SELECTED, ALL, ALL_DIRECT
    }

    private static final Trace LOGGER = TraceManager.getTrace(AbstractRoleMemberPanel.class);
    private static final String DOT_CLASS = AbstractRoleMemberPanel.class.getName() + ".";

    protected static final String OPERATION_LOAD_MEMBER_RELATIONS = DOT_CLASS + "loadMemberRelationsList";

    protected static final String ID_FORM = "form";

    protected static final String ID_CONTAINER_MEMBER = "memberContainer";
    protected static final String ID_MEMBER_TABLE = "memberTable";

    private SearchBoxConfigurationType additionalPanelConfig;

    private static final Map<QName, Map<String, String>> AUTHORIZATIONS = new HashMap<>();
    private static final Map<QName, UserProfileStorage.TableId> TABLES_ID = new HashMap<>();

    static {
        TABLES_ID.put(RoleType.COMPLEX_TYPE, UserProfileStorage.TableId.ROLE_MEMBER_PANEL);
        TABLES_ID.put(ServiceType.COMPLEX_TYPE, UserProfileStorage.TableId.SERVICE_MEMBER_PANEL);
        TABLES_ID.put(OrgType.COMPLEX_TYPE, UserProfileStorage.TableId.ORG_MEMBER_PANEL);
        TABLES_ID.put(ArchetypeType.COMPLEX_TYPE, UserProfileStorage.TableId.ARCHETYPE_MEMBER_PANEL);
    }

    static {
        AUTHORIZATIONS.put(RoleType.COMPLEX_TYPE, GuiAuthorizationConstants.ROLE_MEMBERS_AUTHORIZATIONS);
        AUTHORIZATIONS.put(ServiceType.COMPLEX_TYPE, GuiAuthorizationConstants.SERVICE_MEMBERS_AUTHORIZATIONS);
        AUTHORIZATIONS.put(OrgType.COMPLEX_TYPE, GuiAuthorizationConstants.ORG_MEMBERS_AUTHORIZATIONS);
        AUTHORIZATIONS.put(ArchetypeType.COMPLEX_TYPE, GuiAuthorizationConstants.ARCHETYPE_MEMBERS_AUTHORIZATIONS);
    }

    private CompiledObjectCollectionView compiledCollectionViewFromPanelConfiguration;

    public AbstractRoleMemberPanel(String id, FocusDetailsModels<R> model, ContainerPanelConfigurationType config) {
        super(id, model, config);
    }

    protected void initLayout() {
        Form<?> form = new MidpointForm<>(ID_FORM);
        form.setOutputMarkupId(true);
        add(form);
        this.additionalPanelConfig = getAdditionalPanelConfig();
        initMemberTable(form);
        setOutputMarkupId(true);
    }

    protected Form<?> getForm() {
        return (Form) get(ID_FORM);
    }

    private <AH extends AssignmentHolderType> void initMemberTable(Form<?> form) {
        WebMarkupContainer memberContainer = new WebMarkupContainer(ID_CONTAINER_MEMBER);
        memberContainer.setOutputMarkupId(true);
        memberContainer.setOutputMarkupPlaceholderTag(true);
        form.add(memberContainer);

        //TODO QName defines a relation value which will be used for new member creation
        MainObjectListPanel<AH> childrenListPanel = new MainObjectListPanel<>(
                ID_MEMBER_TABLE, getDefaultObjectTypeClass(), getSearchOptions(), getPanelConfiguration()) {

            private static final long serialVersionUID = 1L;

            @Override
            protected UserProfileStorage.TableId getTableId() {
                return AbstractRoleMemberPanel.this.getTableId(getComplexTypeQName());
            }

            @Override
            protected List<IColumn<SelectableBean<AH>, String>> createDefaultColumns() {
                List<IColumn<SelectableBean<AH>, String>> columns = super.createDefaultColumns();
                columns.add(createRelationColumn());
                return columns;
            }

            @Override
            protected boolean isObjectDetailsEnabled(IModel<SelectableBean<AH>> rowModel) {
                if (rowModel == null || rowModel.getObject() == null
                        || rowModel.getObject().getValue() == null) {
                    return false;
                }
                Class<?> objectClass = rowModel.getObject().getValue().getClass();
                return WebComponentUtil.hasDetailsPage(objectClass);
            }

            @Override
            protected List<Component> createToolbarButtonsList(String buttonId) {
                List<Component> buttonsList = super.createToolbarButtonsList(buttonId);
                AjaxIconButton assignButton = createAssignButton(buttonId);
                buttonsList.add(1, assignButton);
                return buttonsList;
            }

            @Override
            protected List<InlineMenuItem> createInlineMenu() {
                return createRowActions();
            }

            @Override
            protected String getStorageKey() {
                return AbstractRoleMemberPanel.this.createStorageKey();
            }

            protected PageStorage getPageStorage(String storageKey){
                return getSession().getSessionStorage().getPageStorageMap().get(storageKey);
            }

            @Override
            protected Search<AH> createSearch(Class<AH> type) {
                return createMemberSearch(type);
            }

            @Override
            protected SelectableBeanObjectDataProvider<AH> createProvider() {
                SelectableBeanObjectDataProvider<AH> provider = createSelectableBeanObjectDataProvider(() -> getCustomizedQuery(getSearchModel().getObject()), null);
                provider.addQueryVariables(ExpressionConstants.VAR_PARENT_OBJECT, AbstractRoleMemberPanel.this.getModelObject());
                return provider;
            }

            @Override
            public void refreshTable(AjaxRequestTarget target) {
//                if (getSearchModel().isLoaded() && getSearchModel().getObject()!= null
//                        && getSearchModel().getObject().isTypeChanged()) {
//                    clearCache();
//                }
//                if (reloadPageOnRefresh()) {
//                    throw new RestartResponseException(getPage().getClass());
//                } else {
//                    super.refreshTable(target);
//                }
            }

            @Override
            protected boolean showNewObjectCreationPopup() {
                return CollectionUtils.isNotEmpty(getNewObjectReferencesList(getObjectCollectionView(), null));
            }

            @Override
            protected List<ObjectReferenceType> getNewObjectReferencesList(CompiledObjectCollectionView collectionView, AssignmentObjectRelation relation) {
                List<ObjectReferenceType> refList = super.getNewObjectReferencesList(collectionView, relation);
                if (refList == null) {
                    refList = new ArrayList<>();
                }
                if (relation != null && CollectionUtils.isNotEmpty(relation.getArchetypeRefs())) {
                    refList.addAll(relation.getArchetypeRefs());
                }
                ObjectReferenceType membershipRef = new ObjectReferenceType();
                membershipRef.setOid(AbstractRoleMemberPanel.this.getModelObject().getOid());
                membershipRef.setType(AbstractRoleMemberPanel.this.getModelObject().asPrismObject().getComplexTypeDefinition().getTypeName());
                membershipRef.setRelation(relation != null && CollectionUtils.isNotEmpty(relation.getRelations()) ?
                        relation.getRelations().get(0) : null);
                refList.add(membershipRef);
                return refList;
            }

            @Override
            protected LoadableModel<MultiFunctinalButtonDto> loadButtonDescriptions() {
                return loadMultiFunctionalButtonModel(true);
            }

            @Override
            public ContainerPanelConfigurationType getPanelConfiguration() {
                return AbstractRoleMemberPanel.this.getPanelConfiguration();
            }

            @Override
            protected String getTitleForNewObjectButton() {
                return createStringResource("TreeTablePanel.menu.createMember").getString();
            }
        };
        childrenListPanel.setOutputMarkupId(true);
        memberContainer.add(childrenListPanel);
    }

    protected boolean reloadPageOnRefresh() {
        return false;
    }

    private  <AH extends AssignmentHolderType> IColumn<SelectableBean<AH>, String> createRelationColumn() {
        return new AbstractExportableColumn<>(
                createStringResource("roleMemberPanel.relation")) {
            private static final long serialVersionUID = 1L;

            @Override
            public void populateItem(Item<ICellPopulator<SelectableBean<AH>>> cellItem,
                    String componentId, IModel<SelectableBean<AH>> rowModel) {
                cellItem.add(new Label(componentId,
                        getRelationValue(rowModel.getObject().getValue())));
            }

            @Override
            public IModel<String> getDataModel(IModel<SelectableBean<AH>> rowModel) {
                return Model.of(getRelationValue(rowModel.getObject().getValue()));
            }

        };
    }

    private <AH extends AssignmentHolderType> String getRelationValue(AH value) {
        List<String> relations = new ArrayList<>();
            for (ObjectReferenceType roleMembershipRef : value.getRoleMembershipRef()) {
                List<QName> defaultRelations = getDefaultRelationsForActions();
                if (roleMembershipRef.getOid().equals(getModelObject().getOid())
                        && (defaultRelations.contains(roleMembershipRef.getRelation())
                        || defaultRelations.contains(PrismConstants.Q_ANY))) {
                    String relation = roleMembershipRef.getRelation().getLocalPart();
                    RelationDefinitionType relationDef = WebComponentUtil.getRelationDefinition(roleMembershipRef.getRelation());
                    if (relationDef != null) {
                        DisplayType display = relationDef.getDisplay();
                        if (display != null) {
                            PolyStringType label = display.getLabel();
                            if (PolyStringUtils.isNotEmpty(label)) {
                                relation = WebComponentUtil.getTranslatedPolyString(label);
                            }
                        }
                    }
                    relations.add(relation);
                }
            }
        return String.join(", ", relations);
    }

    private CompiledObjectCollectionView getCompiledCollectionViewFromPanelConfiguration() {
        if (compiledCollectionViewFromPanelConfiguration != null) {
            return compiledCollectionViewFromPanelConfiguration;
        }
        if (getPanelConfiguration() == null) {
            return null;
        }
        if (getPanelConfiguration().getListView() == null) {
            return null;
        }
        CollectionRefSpecificationType collectionRefSpecificationType = getPanelConfiguration().getListView().getCollection();

        if (collectionRefSpecificationType == null) {
            compiledCollectionViewFromPanelConfiguration = new CompiledObjectCollectionView();
            getPageBase().getModelInteractionService().applyView(compiledCollectionViewFromPanelConfiguration, getPanelConfiguration().getListView());
            return null;
        }
        Task task = getPageBase().createSimpleTask("Compile collection");
        OperationResult result = task.getResult();
        try {
            compiledCollectionViewFromPanelConfiguration = getPageBase().getModelInteractionService().compileObjectCollectionView(collectionRefSpecificationType, AssignmentType.class, task, result);
        } catch (Throwable e) {
            LOGGER.error("Cannot compile object collection view for panel configuration {}. Reason: {}", getPanelConfiguration(), e.getMessage(), e);
            result.recordFatalError("Cannot compile object collection view for panel configuration " + getPanelConfiguration() + ". Reason: " + e.getMessage(), e);
            getPageBase().showResult(result);
        }
        return compiledCollectionViewFromPanelConfiguration;

    }

    private <AH extends AssignmentHolderType> Class<AH> getDefaultObjectTypeClass() {
        QName objectTypeQname = getMemberPanelStorage().getDefaultObjectType();
        return ObjectTypes.getObjectTypeClass(objectTypeQname);
    }

    private <AH extends AssignmentHolderType> Search<AH> createMemberSearch(Class<AH> type) {
        MemberPanelStorage memberPanelStorage = getMemberPanelStorage();
        if (memberPanelStorage == null) { //normally, this should not happen
            return SearchFactory.createMemberPanelSearch(type, getPageBase());
        }

        if (memberPanelStorage.getSearch() != null) {
            return memberPanelStorage.getSearch();
        }

        return SearchFactory.createMemberPanelSearch(getDefaultObjectTypeClass(), createSearchConfig(), getPageBase());
//        Search<AH> search = SearchFactory.createSearch(createSearchTypeItem(memberPanelStorage), null, null,
//                null, getPageBase(), null, true, true, Search.PanelType.MEMBER_PANEL);
//        search.addCompositedSpecialItem(createMemberSearchPanel(search, memberPanelStorage));

//        if (additionalPanelConfig != null){
//            search.setCanConfigure(!Boolean.FALSE.equals(additionalPanelConfig.isAllowToConfigureSearchItems()));
//        }
//        return search;
    }

    private SearchBoxConfigurationType createSearchConfig() {
        SearchBoxConfigurationType searchConfig = new SearchBoxConfigurationType();
        ObjectTypeSearchItemConfigurationType objTypeConfig = new ObjectTypeSearchItemConfigurationType();
        objTypeConfig.getSupportedTypes().addAll(getDefaultSupportedObjectTypes(false));
        objTypeConfig.setDefaultValue(WebComponentUtil.classToQName(getPrismContext(), getDefaultObjectType()));
        searchConfig.setObjectTypeConfiguration(objTypeConfig);

        RelationSearchItemConfigurationType relationConfig = new RelationSearchItemConfigurationType();
        relationConfig.getSupportedRelations().addAll(getSupportedRelations());
        searchConfig.setRelationConfiguration(relationConfig);

        return searchConfig;
    }

    private <AH extends AssignmentHolderType> ContainerTypeSearchItem<AH> createSearchTypeItem(MemberPanelStorage memberPanelStorage) {
        ContainerTypeSearchItem<AH> searchTypeItem = new ContainerTypeSearchItem<>(createTypeSearchValue(memberPanelStorage.getDefaultObjectType()), getAllowedTypes());
        searchTypeItem.setConfiguration(memberPanelStorage.getObjectTypeSearchItem());
        searchTypeItem.setVisible(true);
        return searchTypeItem;
    }

    private AbstractRoleCompositedSearchItem createMemberSearchPanel(Search search, MemberPanelStorage memberPanelStorage) {
        return new AbstractRoleCompositedSearchItem(search, memberPanelStorage) {

            @Override
            protected PrismReferenceDefinition getReferenceDefinition(ItemName refName) {
                return PrismContext.get().getSchemaRegistry()
                        .findContainerDefinitionByCompileTimeClass(AssignmentType.class)
                        .findReferenceDefinition(refName);
            }

            @Override
            protected R getAbstractRoleObject() {
                return AbstractRoleMemberPanel.this.getModelObject();
            }

        };
    }

    private <AH extends AssignmentHolderType> ObjectQuery getCustomizedQuery(Search<AH> search) {
        MemberPanelStorage memberPanelStorage = getMemberPanelStorage();
        if (noMemberSearchItemVisible(memberPanelStorage)) {
            PrismContext prismContext = getPageBase().getPrismContext();
            return prismContext.queryFor(search.getTypeClass())
                    .exists(AssignmentHolderType.F_ASSIGNMENT)
                        .block()
                        .item(AssignmentType.F_TARGET_REF)
                        .ref(MemberOperationsHelper.createReferenceValuesList(getModelObject(), getRelationsForSearch(memberPanelStorage)))
                        .endBlock().build();
        }
        return null;
    }

    private boolean noMemberSearchItemVisible(MemberPanelStorage memberPanelStorage) {
        return !memberPanelStorage.isRelationVisible() && !memberPanelStorage.isIndirectVisible()
                && (!isOrg() || !memberPanelStorage.isSearchScopeVisible())
                && (isNotRole() || !memberPanelStorage.isTenantVisible())
                && (isNotRole() || !memberPanelStorage.isProjectVisible());
    }

    private List<QName> getRelationsForSearch(MemberPanelStorage memberPanelStorage) {
        List<QName> relations = new ArrayList<>();
        if (QNameUtil.match(PrismConstants.Q_ANY, memberPanelStorage.getDefaultRelation())) {
            relations.addAll(memberPanelStorage.getSupportedRelations());
        } else {
            relations.add(memberPanelStorage.getDefaultRelation());
        }
        return relations;
    }

    private boolean isOrg() {
        return getModelObject() instanceof OrgType;
    }

    private boolean isNotRole() {
        return !(getModelObject() instanceof RoleType);
    }

    private String createStorageKey() {
        UserProfileStorage.TableId tableId = getTableId(getComplexTypeQName());
        String collectionName = getPanelConfiguration() != null ? ("_" + getPanelConfiguration().getIdentifier()) : "";
        return tableId.name() + "_" + getStorageKeyTabSuffix() + collectionName;
    }

    private <AH extends AssignmentHolderType> List<DisplayableValue<Class<AH>>> getAllowedTypes() {
        List<DisplayableValue<Class<AH>>> ret = new ArrayList<>();
        ret.add(new SearchValue<>(getChoiceForAllTypes(), "ObjectTypes.all"));

        List<QName> types = getMemberPanelStorage().getSupportedObjectTypes();
        for (QName type : types) {
            ret.add(createTypeSearchValue(type));
        }
        return ret;
    }

    protected <AH extends AssignmentHolderType> Class<AH> getChoiceForAllTypes () {
        return (Class<AH>) FocusType.class;
    }

    private <AH extends AssignmentHolderType> SearchValue<Class<AH>> createTypeSearchValue(QName type) {
        Class<AH> typeClass = ObjectTypes.getObjectTypeClass(type);
        return new SearchValue<>(typeClass, "ObjectType." + typeClass.getSimpleName());
    }

    protected LoadableModel<MultiFunctinalButtonDto> loadMultiFunctionalButtonModel(boolean useDefaultObjectRelations) {

        return new LoadableModel<>(false) {

            @Override
            protected MultiFunctinalButtonDto load() {
                MultiFunctinalButtonDto multiFunctinalButtonDto = new MultiFunctinalButtonDto();

                DisplayType mainButtonDisplayType = getCreateMemberButtonDisplayType();
                CompositedIconBuilder builder = new CompositedIconBuilder();
                Map<IconCssStyle, IconType> layerIcons = WebComponentUtil.createMainButtonLayerIcon(mainButtonDisplayType);
                for (Map.Entry<IconCssStyle, IconType> icon : layerIcons.entrySet()) {
                    builder.appendLayerIcon(icon.getValue(), icon.getKey());
                }
                CompositedIconButtonDto mainButton = createCompositedIconButtonDto(mainButtonDisplayType, null, builder.build());
                multiFunctinalButtonDto.setMainButton(mainButton);

                List<AssignmentObjectRelation> loadedRelations = loadMemberRelationsList();
                if (CollectionUtils.isEmpty(loadedRelations) && useDefaultObjectRelations) {
                    loadedRelations.addAll(getDefaultNewMemberRelations());
                }
                List<CompositedIconButtonDto> additionalButtons = new ArrayList<>();
                if (CollectionUtils.isNotEmpty(loadedRelations)) {
                    List<AssignmentObjectRelation> relations = WebComponentUtil.divideAssignmentRelationsByAllValues(loadedRelations);
                    relations.forEach(relation -> {
                        DisplayType additionalButtonDisplayType = GuiDisplayTypeUtil.getAssignmentObjectRelationDisplayType(getPageBase(), relation,
                                "abstractRoleMemberPanel.menu.createMember");
                        CompositedIconButtonDto buttonDto = createCompositedIconButtonDto(additionalButtonDisplayType, relation, createCompositedIcon(relation, additionalButtonDisplayType));
                        additionalButtons.add(buttonDto);
                    });
                }
                multiFunctinalButtonDto.setAdditionalButtons(additionalButtons);
                return multiFunctinalButtonDto;
            }
        };
    }

    private CompositedIcon createCompositedIcon(AssignmentObjectRelation relation, DisplayType additionalButtonDisplayType) {
        CompositedIconBuilder builder = WebComponentUtil.getAssignmentRelationIconBuilder(getPageBase(), relation,
                additionalButtonDisplayType.getIcon(), WebComponentUtil.createIconType(GuiStyleConstants.CLASS_ADD_NEW_OBJECT, "green"));
        return builder.build();
    }

    protected List<AssignmentObjectRelation> getDefaultNewMemberRelations() {
        List<AssignmentObjectRelation> relationsList = new ArrayList<>();
        List<QName> newMemberObjectTypes = getNewMemberObjectTypes();
        if (newMemberObjectTypes != null) {
            newMemberObjectTypes.forEach(objectType -> {
                List<QName> supportedRelation = getSupportedRelations();
                if (!UserType.COMPLEX_TYPE.equals(objectType) && !OrgType.COMPLEX_TYPE.equals(objectType)) {
                    supportedRelation.remove(RelationTypes.APPROVER.getRelation());
                    supportedRelation.remove(RelationTypes.OWNER.getRelation());
                    supportedRelation.remove(RelationTypes.MANAGER.getRelation());
                }
                AssignmentObjectRelation assignmentObjectRelation = new AssignmentObjectRelation();
                assignmentObjectRelation.addRelations(supportedRelation);
                assignmentObjectRelation.addObjectTypes(Collections.singletonList(objectType));
                assignmentObjectRelation.getArchetypeRefs().addAll(archetypeReferencesListForType(objectType));
                relationsList.add(assignmentObjectRelation);
            });
        } else {
            AssignmentObjectRelation assignmentObjectRelation = new AssignmentObjectRelation();
            assignmentObjectRelation.addRelations(getSupportedRelations());
            relationsList.add(assignmentObjectRelation);
        }
        return relationsList;
    }

    private List<ObjectReferenceType> archetypeReferencesListForType(QName type) {
        List<CompiledObjectCollectionView> views =
                getPageBase().getCompiledGuiProfile().findAllApplicableArchetypeViews(type, OperationTypeType.ADD);
        List<ObjectReferenceType> archetypeRefs = new ArrayList<>();
        views.forEach(view -> {
            if (view.getCollection() != null && view.getCollection().getCollectionRef() != null) {
                archetypeRefs.add(view.getCollection().getCollectionRef());
            }
        });
        return archetypeRefs;
    }

    private AjaxIconButton createAssignButton(String buttonId) {
        AjaxIconButton assignButton = new AjaxIconButton(buttonId, new Model<>(GuiStyleConstants.EVO_ASSIGNMENT_ICON),
                createStringResource("TreeTablePanel.menu.addMembers")) {

            private static final long serialVersionUID = 1L;

            @Override
            public void onClick(AjaxRequestTarget target) {
                ChooseMemberPopup browser = new ChooseMemberPopup(AbstractRoleMemberPanel.this.getPageBase().getMainPopupBodyId(),
                        getMemberPanelStorage().getRelationSearchItem(), loadMultiFunctionalButtonModel(false)) {
                    private static final long serialVersionUID = 1L;

                    @Override
                    protected R getAssignmentTargetRefObject(){
                        return AbstractRoleMemberPanel.this.getModelObject();
                    }

                    @Override
                    protected List<QName> getAvailableObjectTypes(){
                        return null;
                    }

                    @Override
                    protected List<ObjectReferenceType> getArchetypeRefList(){
                        return new ArrayList<>(); //todo
                    }

                    @Override
                    protected boolean isOrgTreeVisible(){
                        return true;
                    }
                };
                browser.setOutputMarkupId(true);
                AbstractRoleMemberPanel.this.getPageBase().showMainPopup(browser, target);
            }
        };
        assignButton.add(AttributeAppender.append("class", "btn btn-default btn-sm"));
        return assignButton;
    }

    private CompositedIconButtonDto createCompositedIconButtonDto(DisplayType buttonDisplayType, AssignmentObjectRelation relation, CompositedIcon icon) {
        CompositedIconButtonDto compositedIconButtonDto = new CompositedIconButtonDto();
        compositedIconButtonDto.setAdditionalButtonDisplayType(buttonDisplayType);
        if (icon != null) {
            compositedIconButtonDto.setCompositedIcon(icon);
        } else {
            CompositedIconBuilder mainButtonIconBuilder = new CompositedIconBuilder();
            mainButtonIconBuilder.setBasicIcon(WebComponentUtil.getIconCssClass(buttonDisplayType), IconCssStyle.IN_ROW_STYLE)
                    .appendColorHtmlValue(WebComponentUtil.getIconColor(buttonDisplayType));
            compositedIconButtonDto.setCompositedIcon(mainButtonIconBuilder.build());
        }
        compositedIconButtonDto.setAssignmentObjectRelation(relation);
        return compositedIconButtonDto;
    }

    protected UserProfileStorage.TableId getTableId(QName complextType) {
        return TABLES_ID.get(complextType);
    }

    protected Map<String, String> getAuthorizations(QName complexType) {
        return AUTHORIZATIONS.get(complexType);
    }

    protected QName getComplexTypeQName() {
        return getModelObject().asPrismObject().getComplexTypeDefinition().getTypeName();
    }

    private DisplayType getCreateMemberButtonDisplayType() {
        return GuiDisplayTypeUtil.createDisplayType(GuiStyleConstants.CLASS_ADD_NEW_OBJECT, "green",
                AbstractRoleMemberPanel.this.createStringResource("abstractRoleMemberPanel.menu.createMember", "", "").getString());
    }

    protected List<InlineMenuItem> createRowActions() {
        List<InlineMenuItem> menu = new ArrayList<>();
        createAssignMemberRowAction(menu);
        createUnassignMemberRowAction(menu);
        createRecomputeMemberRowAction(menu);

        if (isAuthorized(GuiAuthorizationConstants.MEMBER_OPERATION_CREATE)) {
            menu.add(new InlineMenuItem(createStringResource("abstractRoleMemberPanel.menu.create")) {
                private static final long serialVersionUID = 1L;

                @Override
                public InlineMenuItemAction initAction() {
                    return new HeaderMenuAction(AbstractRoleMemberPanel.this) {
                        private static final long serialVersionUID = 1L;

                        @Override
                        public void onClick(AjaxRequestTarget target) {
                            createFocusMemberPerformed(target);
                        }
                    };
                }
            });
        }
        if (isAuthorized(GuiAuthorizationConstants.MEMBER_OPERATION_DELETE)) {
            menu.add(new InlineMenuItem(createStringResource("abstractRoleMemberPanel.menu.delete")) {
                private static final long serialVersionUID = 1L;

                @Override
                public InlineMenuItemAction initAction() {
                    return new HeaderMenuAction(AbstractRoleMemberPanel.this) {
                        private static final long serialVersionUID = 1L;

                        @Override
                        public void onClick(AjaxRequestTarget target) {
                            deleteMembersPerformed(target);
                        }
                    };
                }

            });
        }
        return menu;
    }

    protected void createAssignMemberRowAction(List<InlineMenuItem> menu) {
        if (isAuthorized(GuiAuthorizationConstants.MEMBER_OPERATION_ASSIGN)) {
            menu.add(new InlineMenuItem(createStringResource("abstractRoleMemberPanel.menu.assign")) {
                private static final long serialVersionUID = 1L;

                @Override
                public InlineMenuItemAction initAction() {
                    return new HeaderMenuAction(AbstractRoleMemberPanel.this) {
                        private static final long serialVersionUID = 1L;

                        @Override
                        public void onClick(AjaxRequestTarget target) {
                            MemberOperationsGuiHelper.assignMembers(getPageBase(), AbstractRoleMemberPanel.this.getModelObject(),
                                    target, getMemberPanelStorage().getRelationSearchItem(), null);
                        }
                    };
                }
            });
        }
    }

    protected void createUnassignMemberRowAction(List<InlineMenuItem> menu) {
        if (isAuthorized(GuiAuthorizationConstants.MEMBER_OPERATION_UNASSIGN)) {
            menu.add(new ButtonInlineMenuItem(createStringResource("abstractRoleMemberPanel.menu.unassign")) {
                private static final long serialVersionUID = 1L;

                @Override
                public InlineMenuItemAction initAction() {
                    return new HeaderMenuAction(AbstractRoleMemberPanel.this) {
                        private static final long serialVersionUID = 1L;

                        @Override
                        public void onClick(AjaxRequestTarget target) {
                            unassignMembersPerformed(target);
                        }
                    };

                }

                @Override
                public CompositedIconBuilder getIconCompositedBuilder() {
                    return getDefaultCompositedIconBuilder(GuiStyleConstants.CLASS_UNASSIGN);
                }
            });
        }
    }

    protected void createRecomputeMemberRowAction(List<InlineMenuItem> menu) {
        if (isAuthorized(GuiAuthorizationConstants.MEMBER_OPERATION_RECOMPUTE)) {
            menu.add(new ButtonInlineMenuItem(createStringResource("abstractRoleMemberPanel.menu.recompute")) {
                private static final long serialVersionUID = 1L;

                @Override
                public InlineMenuItemAction initAction() {
                    return new HeaderMenuAction(AbstractRoleMemberPanel.this) {
                        private static final long serialVersionUID = 1L;

                        @Override
                        public void onClick(AjaxRequestTarget target) {
                            recomputeMembersPerformed(target);
                        }
                    };
                }

                @Override
                public CompositedIconBuilder getIconCompositedBuilder() {
                    return getDefaultCompositedIconBuilder(GuiStyleConstants.CLASS_RECONCILE_MENU_ITEM);
                }

            });
        }
    }

    protected List<QName> getSupportedRelations() {
        if ("roleMembers".equals(getPanelConfiguration().getIdentifier()) || "serviceMembers".equals(getPanelConfiguration().getIdentifier())) {
            return getSupportedMembersTabRelations();
        }
        if ("roleGovernance".equals(getPanelConfiguration().getIdentifier()) || "serviceGovernance".equals(getPanelConfiguration().getIdentifier())) {
            return getSupportedGovernanceTabRelations();
        }
        return new ArrayList<>();
    }

    protected List<QName> getSupportedMembersTabRelations() {
        List<QName> relations = WebComponentUtil.getCategoryRelationChoices(AreaCategoryType.ADMINISTRATION, getPageBase());
        List<QName> governance = WebComponentUtil.getCategoryRelationChoices(AreaCategoryType.GOVERNANCE, getPageBase());
        governance.forEach(relations::remove);
        return relations;
//        return new AvailableRelationDto(relations, defaultRelationConfiguration);
    }

    protected List<QName> getSupportedGovernanceTabRelations() {
        return WebComponentUtil.getCategoryRelationChoices(AreaCategoryType.GOVERNANCE, getPageBase());
    }



    protected SearchBoxConfigurationType getAdditionalPanelConfig() {
        CompiledObjectCollectionView collectionViewFromPanelConfig = getCompiledCollectionViewFromPanelConfiguration();
        if (collectionViewFromPanelConfig != null) {
            return collectionViewFromPanelConfig.getSearchBoxConfiguration();
        }
        CompiledObjectCollectionView view = WebComponentUtil.getCollectionViewByObject(getModelObject(), getPageBase());
        if (view != null && view.getAdditionalPanels() != null) {
            GuiObjectListPanelConfigurationType config = view.getAdditionalPanels().getMemberPanel();
            return config == null ? new SearchBoxConfigurationType() : config.getSearchBoxConfiguration();
        }
        return new SearchBoxConfigurationType();
    }

    private boolean isAuthorized(String action) {
        Map<String, String> memberAuthz = getAuthorizations(getComplexTypeQName());
        return WebComponentUtil.isAuthorized(memberAuthz.get(action));
    }

    private List<AssignmentObjectRelation> loadMemberRelationsList() {
        AssignmentCandidatesSpecification spec = loadCandidateSpecification();
        return spec != null ? spec.getAssignmentObjectRelations() : new ArrayList<>();
    }

    private AssignmentCandidatesSpecification loadCandidateSpecification() {
        OperationResult result = new OperationResult(OPERATION_LOAD_MEMBER_RELATIONS);
        PrismObject<? extends AbstractRoleType> obj = getModelObject().asPrismObject();
        AssignmentCandidatesSpecification spec = null;
        try {
            spec = getPageBase().getModelInteractionService()
                    .determineAssignmentHolderSpecification(obj, result);
        } catch (Throwable ex) {
            result.recordPartialError(ex.getLocalizedMessage());
            LOGGER.error("Couldn't load member relations list for the object {} , {}", obj.getName(), ex.getLocalizedMessage());
        }
        return spec;
    }

    protected void assignMembers(AjaxRequestTarget target, RelationSearchItemConfigurationType relationConfig,
            List<QName> objectTypes, List<ObjectReferenceType> archetypeRefList, boolean isOrgTreePanelVisible) {
        MemberOperationsGuiHelper.assignMembers(getPageBase(), getModelObject(), target, relationConfig,
                objectTypes, archetypeRefList, isOrgTreePanelVisible);
    }

    private void unassignMembersPerformed(AjaxRequestTarget target) {
        QueryScope scope = getQueryScope();

        ChooseFocusTypeAndRelationDialogPanel chooseTypePopupContent = new ChooseFocusTypeAndRelationDialogPanel(getPageBase().getMainPopupBodyId(),
                createStringResource("abstractRoleMemberPanel.unassignAllMembersConfirmationLabel")) {
            private static final long serialVersionUID = 1L;

            @Override
            protected List<QName> getSupportedObjectTypes() {
                return AbstractRoleMemberPanel.this.getMemberPanelStorage().getSupportedObjectTypes();//getSupportedObjectTypes(true);
            }

            @Override
            protected List<QName> getSupportedRelations() {
                return AbstractRoleMemberPanel.this.getMemberPanelStorage().getSupportedRelations();
            }

            @Override
            protected List<QName> getDefaultRelations() {
                return getDefaultRelationsForActions();
            }

            @Override
            protected boolean isFocusTypeSelectorVisible() {
                return !QueryScope.SELECTED.equals(scope);
            }

            protected void okPerformed(QName type, Collection<QName> relations, AjaxRequestTarget target) {
                unassignMembersPerformed(type, getMemberPanelStorage().isSearchScope(SearchBoxScopeType.SUBTREE)
                        && QueryScope.ALL.equals(scope) ? QueryScope.ALL_DIRECT : scope, relations, target);
            }

            @Override
            protected PrismObject<TaskType> getTask(QName type, Collection<QName> relations, AjaxRequestTarget target) {
                if (checkRelationNotSelected(relations, "No relation was selected. Cannot perform unassign members", target)) {
                    return null;
                }
                Task task = MemberOperationsHelper.createUnassignMembersTask(
                        AbstractRoleMemberPanel.this.getModelObject(),
                        scope,
                        type,
                        getActionQuery(scope, relations),
                        relations,
                        target, getPageBase());

                if (task == null) {
                    return null;
                }
                return task.getRawTaskObjectClone();
            }

            @Override
            protected boolean isTaskConfigureButtonVisible() {
                return true;
            }

            @Override
            protected QName getDefaultObjectType() {
                if (QueryScope.SELECTED.equals(scope)) {
                    return FocusType.COMPLEX_TYPE;
                }
                return WebComponentUtil.classToQName(AbstractRoleMemberPanel.this.getPrismContext(),
                        AbstractRoleMemberPanel.this.getDefaultObjectType());
            }

            @Override
            protected IModel<String> getWarningMessageModel() {
                if (getMemberPanelStorage().isSearchScope(SearchBoxScopeType.SUBTREE)) {
                    return getPageBase().createStringResource("abstractRoleMemberPanel.unassign.warning.subtree");
                }
                return null;
            }

            @Override
            public int getHeight() {
                if (getMemberPanelStorage().isSearchScope(SearchBoxScopeType.SUBTREE)) {
                    return 325;
                }
                return 230;
            }
        };

        getPageBase().showMainPopup(chooseTypePopupContent, target);
    }

    private List<QName> getDefaultRelationsForActions() {
        List<QName> defaultRelations = new ArrayList<>();
        QName defaultRelation = AbstractRoleMemberPanel.this.getMemberPanelStorage().getDefaultRelation();
        if (defaultRelation != null) {
            defaultRelations.add(AbstractRoleMemberPanel.this.getMemberPanelStorage().getDefaultRelation());
        } else {
            defaultRelations.add(RelationTypes.MEMBER.getRelation());
        }
        return defaultRelations;
    }

    private void deleteMembersPerformed(AjaxRequestTarget target) {
        QueryScope scope = getQueryScope();
        StringResourceModel confirmModel;
        if (getMemberPanelStorage().isSearchScope(SearchBoxScopeType.SUBTREE)) {
            confirmModel = createStringResource("abstractRoleMemberPanel.deleteAllSubtreeMembersConfirmationLabel");
        } else {
            confirmModel = getMemberTable().getSelectedObjectsCount() > 0 ?
                    createStringResource("abstractRoleMemberPanel.deleteSelectedMembersConfirmationLabel")
                    : createStringResource("abstractRoleMemberPanel.deleteAllMembersConfirmationLabel");
        }
        ChooseFocusTypeAndRelationDialogPanel chooseTypePopupContent = new ChooseFocusTypeAndRelationDialogPanel(getPageBase().getMainPopupBodyId(),
                confirmModel) {
            private static final long serialVersionUID = 1L;

            @Override
            protected List<QName> getSupportedObjectTypes() {
                return AbstractRoleMemberPanel.this.getMemberPanelStorage().getSupportedObjectTypes();//getSupportedObjectTypes(true);
            }

            @Override
            protected List<QName> getSupportedRelations() {
                return AbstractRoleMemberPanel.this.getMemberPanelStorage().getSupportedRelations();
            }

            @Override
            protected List<QName> getDefaultRelations() {
                return getDefaultRelationsForActions();
            }

            protected void okPerformed(QName type, Collection<QName> relations, AjaxRequestTarget target) {
                deleteMembersPerformed(scope, relations, target);
            }

            @Override
            protected boolean isFocusTypeSelectorVisible() {
                return !QueryScope.SELECTED.equals(scope);
            }

            @Override
            protected QName getDefaultObjectType() {
                return WebComponentUtil.classToQName(AbstractRoleMemberPanel.this.getPrismContext(),
                        AbstractRoleMemberPanel.this.getDefaultObjectType());
            }
        };

        getPageBase().showMainPopup(chooseTypePopupContent, target);
    }

    protected void createFocusMemberPerformed(AjaxRequestTarget target) {
        createFocusMemberPerformed(target, null);
    }

    protected void createFocusMemberPerformed(AjaxRequestTarget target, AssignmentObjectRelation relationSpec) {
        if (relationSpec != null) {
            try {
                List<ObjectReferenceType> newReferences = new ArrayList<>();
                if (CollectionUtils.isEmpty(relationSpec.getRelations())) {
                    relationSpec.setRelations(
                            Collections.singletonList(RelationTypes.MEMBER.getRelation()));
                }
                ObjectReferenceType memberRef = ObjectTypeUtil.createObjectRef(AbstractRoleMemberPanel.this.getModelObject(), relationSpec.getRelations().get(0));
                newReferences.add(memberRef);
                if (CollectionUtils.isNotEmpty(relationSpec.getArchetypeRefs())) {
                    newReferences.add(relationSpec.getArchetypeRefs().get(0));
                }
                QName newMemberType = CollectionUtils.isNotEmpty(relationSpec.getObjectTypes()) ? relationSpec.getObjectTypes().get(0) :
                        getMemberPanelStorage().getSupportedObjectTypes().get(0); //getSupportedObjectTypes(false).get(0);
                WebComponentUtil.initNewObjectWithReference(AbstractRoleMemberPanel.this.getPageBase(), newMemberType, newReferences);
            } catch (SchemaException e) {
                throw new SystemException(e.getMessage(), e);
            }
        } else {
            ChooseFocusTypeAndRelationDialogPanel chooseTypePopupContent = new ChooseFocusTypeAndRelationDialogPanel(
                    getPageBase().getMainPopupBodyId()) {
                private static final long serialVersionUID = 1L;

                @Override
                protected List<QName> getSupportedObjectTypes() {
                    return AbstractRoleMemberPanel.this.getNewMemberObjectTypes();
                }

                @Override
                protected List<QName> getSupportedRelations() {
                    return AbstractRoleMemberPanel.this.getMemberPanelStorage().getSupportedRelations();
                }

                @Override
                protected List<QName> getDefaultRelations() {
                    return getDefaultRelationsForActions();
                }

                protected void okPerformed(QName type, Collection<QName> relations, AjaxRequestTarget target) {
                    if (type == null) {
                        getSession().warn("No type was selected. Cannot create member");
                        target.add(this);
                        target.add(getPageBase().getFeedbackPanel());
                        return;
                    }
                    if (checkRelationNotSelected(relations, "No relation was selected. Cannot create member", target)) {
                        return;
                    }
                    AbstractRoleMemberPanel.this.getPageBase().hideMainPopup(target);
                    try {
                        List<ObjectReferenceType> newReferences = new ArrayList<>();
                        for (QName relation : relations) {
                            newReferences.add(ObjectTypeUtil.createObjectRef(AbstractRoleMemberPanel.this.getModelObject(), relation));
                        }
                        WebComponentUtil.initNewObjectWithReference(AbstractRoleMemberPanel.this.getPageBase(), type, newReferences);
                    } catch (SchemaException e) {
                        throw new SystemException(e.getMessage(), e);
                    }

                }

                @Override
                protected QName getDefaultObjectType() {
                    if (relationSpec != null && CollectionUtils.isNotEmpty(relationSpec.getObjectTypes())) {
                        return relationSpec.getObjectTypes().get(0);
                    }
                    return super.getDefaultObjectType();
                }

                @Override
                protected boolean isFocusTypeSelectorVisible() {
                    return true;
                }
            };

            getPageBase().showMainPopup(chooseTypePopupContent, target);
        }
    }

    protected void deleteMembersPerformed(QueryScope scope, Collection<QName> relations, AjaxRequestTarget target) {
        if (checkRelationNotSelected(relations, "No relation was selected. Cannot perform delete members", target)) {
            return;
        }
        MemberOperationsHelper.createAndSubmitDeleteMembersTask(
                getModelObject(),
                scope,
                getActionQuery(scope, relations),
                target, getPageBase());
    }

    private boolean checkRelationNotSelected(Collection<QName> relations, String message, AjaxRequestTarget target) {
        if (CollectionUtils.isNotEmpty(relations)) {
            return false;
        }
        getSession().warn(message);
        target.add(this);
        target.add(getPageBase().getFeedbackPanel());
        return true;
    }

    protected void unassignMembersPerformed(QName type, QueryScope scope, Collection<QName> relations, AjaxRequestTarget target) {
        if (checkRelationNotSelected(relations, "No relation was selected. Cannot perform unassign members", target)) {
            return;
        }
        MemberOperationsHelper.createAndSubmitUnassignMembersTask(
                getModelObject(),
                scope,
                type,
                getActionQuery(scope, relations),
                relations,
                target, getPageBase());
        target.add(this);
    }

    protected ObjectQuery getActionQuery(QueryScope scope, @NotNull Collection<QName> relations) {
        switch (scope) {
            case ALL:
                return createAllMemberQuery(relations);
            case ALL_DIRECT:
                return MemberOperationsHelper.createDirectMemberQuery(
                        getModelObject(),
                        getSearchType(),
                        relations,
                        getMemberPanelStorage().getTenant(),
                        getMemberPanelStorage().getProject());
            case SELECTED:
                return MemberOperationsHelper.createSelectedObjectsQuery(
                        getMemberTable().getSelectedRealObjects());
        }

        return null;
    }

    protected List<QName> getDefaultSupportedObjectTypes(boolean includeAbstractTypes) {
        return WebComponentUtil.createFocusTypeList(includeAbstractTypes);
    }

    protected List<QName> getNewMemberObjectTypes() {
        return WebComponentUtil.createFocusTypeList();
    }

    protected MainObjectListPanel<FocusType> getMemberTable() {
        return (MainObjectListPanel<FocusType>) get(getPageBase().createComponentPath(ID_FORM, ID_CONTAINER_MEMBER, ID_MEMBER_TABLE));
    }

    protected QueryScope getQueryScope() {
        // TODO if all selected objects have OIDs we can eliminate getOids call
        if (CollectionUtils.isNotEmpty(
                ObjectTypeUtil.getOids(getMemberTable().getSelectedRealObjects()))) {
            return QueryScope.SELECTED;
        }

        if (getMemberPanelStorage().isIndirect()
                || getMemberPanelStorage().isSearchScope(SearchBoxScopeType.SUBTREE)) {
            return QueryScope.ALL;
        }

        return QueryScope.ALL_DIRECT;
    }

    protected void recomputeMembersPerformed(AjaxRequestTarget target) {

        StringResourceModel confirmModel;
        if (getMemberPanelStorage().isSearchScope(SearchBoxScopeType.SUBTREE)) {
            confirmModel = createStringResource("abstractRoleMemberPanel.recomputeAllSubtreeMembersConfirmationLabel");
        } else {
            confirmModel = getMemberTable().getSelectedObjectsCount() > 0 ?
                    createStringResource("abstractRoleMemberPanel.recomputeSelectedMembersConfirmationLabel")
                    : createStringResource("abstractRoleMemberPanel.recomputeAllMembersConfirmationLabel");
        }
        ConfigureTaskConfirmationPanel dialog = new ConfigureTaskConfirmationPanel(((PageBase) getPage()).getMainPopupBodyId(),
                confirmModel) {

            private static final long serialVersionUID = 1L;

            @Override
            protected PrismObject<TaskType> getTask(AjaxRequestTarget target) {
                Task task = MemberOperationsHelper.createRecomputeMembersTask(
                        getModelObject(),
                        getQueryScope(),
                        getActionQuery(getQueryScope(), getRelationsForRecomputeTask()),
                        target, getPageBase());
                if (task == null) {
                    return null;
                }
                PrismObject<TaskType> recomputeTask = task.getRawTaskObjectClone();
                TaskType recomputeTaskType = recomputeTask.asObjectable();
                recomputeTaskType.getAssignment().add(ObjectTypeUtil.createAssignmentTo(SystemObjectsType.ARCHETYPE_RECOMPUTATION_TASK.value(), ObjectTypes.ARCHETYPE, getPrismContext()));
                return recomputeTask;
            }

            @Override
            public StringResourceModel getTitle() {
                return createStringResource("pageUsers.message.confirmActionPopupTitle");
            }

            @Override
            public void yesPerformed(AjaxRequestTarget target) {
                MemberOperationsHelper.createAndSubmitRecomputeMembersTask(
                        getModelObject(),
                        getQueryScope(),
                        getActionQuery(getQueryScope(), getRelationsForRecomputeTask()),
                        target, getPageBase());
            }
        };
        ((PageBase) getPage()).showMainPopup(dialog, target);
    }

    protected @NotNull List<QName> getRelationsForRecomputeTask() {
        return getMemberPanelStorage().getSupportedRelations();
    }

    protected @NotNull QName getSearchType() {
        //noinspection unchecked
        return ObjectTypes.getObjectType(getMemberPanelStorage().getSearch().getTypeClass())
                .getTypeQName();
    }

    protected ObjectQuery createAllMemberQuery(Collection<QName> relations) {
        return getPrismContext().queryFor(FocusType.class)
                .item(FocusType.F_ROLE_MEMBERSHIP_REF).ref(MemberOperationsHelper.createReferenceValuesList(getModelObject(), relations))
                .build();
    }

    private Collection<SelectorOptions<GetOperationOptions>> getSearchOptions() {
        return SelectorOptions.createCollection(GetOperationOptions.createDistinct());
    }

    protected Class<? extends ObjectType> getDefaultObjectType() {
        return FocusType.class;
    }

    protected MemberPanelStorage getMemberPanelStorage() {
        String storageKey = createStorageKey();
        if (StringUtils.isEmpty(storageKey)) {
            return null;
        }
        PageStorage storage = getPageStorage(storageKey);
        if (storage == null) {
            SearchBoxConfigurationHelper searchBoxCofig = new SearchBoxConfigurationHelper(additionalPanelConfig);
            searchBoxCofig.setDefaultSupportedRelations(getSupportedRelations());
            searchBoxCofig.setDefaultSupportedObjectTypes(getDefaultSupportedObjectTypes(false));
            searchBoxCofig.setDefaultObjectType(WebComponentUtil.classToQName(getPrismContext(), getDefaultObjectType()));
            storage = getSessionStorage().initMemberStorage(storageKey, searchBoxCofig);
        }
        return (MemberPanelStorage) storage;
    }

    private PageStorage getPageStorage(String storageKey) {
        return getSessionStorage().getPageStorageMap().get(storageKey);
    }

    private SessionStorage getSessionStorage() {
        return getPageBase().getSessionStorage();
    }

    protected String getStorageKeyTabSuffix(){
        return getPanelConfiguration().getIdentifier();
    }

    public R getModelObject() {
        return getObjectDetailsModels().getObjectType();
    }
}
