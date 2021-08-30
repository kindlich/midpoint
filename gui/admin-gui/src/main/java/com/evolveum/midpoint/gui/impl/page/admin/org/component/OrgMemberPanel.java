/*
 * Copyright (c) 2015-2018 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.impl.page.admin.org.component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.xml.namespace.QName;

import com.evolveum.midpoint.web.application.PanelDisplay;
import com.evolveum.midpoint.web.application.PanelInstance;
import com.evolveum.midpoint.web.application.PanelInstances;
import com.evolveum.midpoint.web.application.PanelType;

import org.apache.cxf.common.util.CollectionUtils;
import org.apache.wicket.ajax.AjaxRequestTarget;

import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.impl.page.admin.abstractrole.component.AbstractRoleMemberPanel;
import com.evolveum.midpoint.gui.impl.page.admin.assignmentholder.FocusDetailsModels;
import com.evolveum.midpoint.prism.PrismConstants;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.page.admin.roles.MemberOperationsHelper;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

@PanelType(name = "orgMembers")
@PanelInstances(instances = {
        @PanelInstance(identifier = "orgMembers", applicableFor = OrgType.class,
                display = @PanelDisplay(label = "Members", order = 60)),
        @PanelInstance(identifier = "orgGovernance", applicableFor = OrgType.class,
                display = @PanelDisplay(label = "Governance", order = 60))
})
public class OrgMemberPanel extends AbstractRoleMemberPanel<OrgType> {
    private static final long serialVersionUID = 1L;

    private static final Trace LOGGER = TraceManager.getTrace(OrgMemberPanel.class);

    public OrgMemberPanel(String id, FocusDetailsModels<OrgType> model, ContainerPanelConfigurationType config) {
        super(id, model, config);
    }

    @Override
    protected ObjectQuery getActionQuery(QueryScope scope, Collection<QName> relations) {
        if (getMemberPanelStorage().isSearchScope(SearchBoxScopeType.ONE_LEVEL) ||
                (getMemberPanelStorage().isSearchScope(SearchBoxScopeType.SUBTREE)
                        && !QueryScope.ALL.equals(scope))) {
            return super.getActionQuery(scope, relations);
        } else {
            String oid = getModelObject().getOid();

            ObjectReferenceType ref = MemberOperationsHelper.createReference(getModelObject(), getMemberPanelStorage().getDefaultRelation());
            ObjectQuery query = getPageBase().getPrismContext().queryFor(getSearchTypeClass())
                    .type(getSearchTypeClass())
                    .isChildOf(ref.asReferenceValue()).build();

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Searching members of org {} with query:\n{}", oid, query.debugDump());
            }
            return query;
        }
    }

    @Override
    protected ObjectQuery createAllMemberQuery(Collection<QName> relations) {
        return getPrismContext().queryFor(AssignmentHolderType.class)
                .item(AssignmentHolderType.F_ROLE_MEMBERSHIP_REF).ref(MemberOperationsHelper.createReferenceValuesList(getModelObject(), relations))
                .build();
    }

    @Override
    protected Class<? extends ObjectType> getChoiceForAllTypes() {
        return AssignmentHolderType.class;
    }

    @Override
    protected void assignMembers(AjaxRequestTarget target, RelationSearchItemConfigurationType relationConfig,
            List<QName> objectTypes, List<ObjectReferenceType> archetypeRefList, boolean isOrgTreePanelVisible) {
        MemberOperationsHelper.assignOrgMembers(getPageBase(), getModelObject(), target, relationConfig, objectTypes, archetypeRefList);
    }

    @Override
    protected List<QName> getDefaultSupportedObjectTypes(boolean includeAbstractTypes) {
        List<QName> objectTypes = WebComponentUtil.createAssignmentHolderTypeQnamesList();
        objectTypes.remove(ShadowType.COMPLEX_TYPE);
        objectTypes.remove(ObjectType.COMPLEX_TYPE);
        if (!includeAbstractTypes) {
            objectTypes.remove(AssignmentHolderType.COMPLEX_TYPE);
        }
        return objectTypes;
    }

    @Override
    protected List<QName> getNewMemberObjectTypes() {
        List<QName> objectTypes = WebComponentUtil.createFocusTypeList();
        objectTypes.add(ResourceType.COMPLEX_TYPE);
        return objectTypes;
    }

    @Override
    protected Class<? extends ObjectType> getDefaultObjectType() {
        return UserType.class;
    }

    @Override
    protected List<QName> getSupportedRelations() {
        if (getPanelConfiguration() == null) {
            return WebComponentUtil.getCategoryRelationChoices(AreaCategoryType.ORGANIZATION, getPageBase());
        }
        if ("orgMembers".equals(getPanelConfiguration().getIdentifier())) {
            return getSupportedMembersTabRelations();
        }
        if ("orgGovernance".equals(getPanelConfiguration().getIdentifier())) {
            return getSupportedGovernanceTabRelations();
        }

        return WebComponentUtil.getCategoryRelationChoices(AreaCategoryType.ORGANIZATION, getPageBase());
    }

    private Class<? extends AssignmentHolderType> getSearchTypeClass() {
        return getMemberPanelStorage().getSearch().getTypeClass();
    }

    @Override
    protected String getStorageKeyTabSuffix() {
        if (getPanelConfiguration() == null) {
            return "orgTreeMembers";
        }
        if ("orgMembers".equals(getPanelConfiguration().getIdentifier())) {
            return "orgMembers";
        }
        if ("orgGovernance".equals(getPanelConfiguration().getIdentifier())) {
            return "orgGovernance";
        }
        return "orgTreeMembers";
    }

    @Override
    protected List<QName> getRelationsForRecomputeTask() {
        if (CollectionUtils.isEmpty(getMemberPanelStorage().getSupportedRelations())) {
            return Collections.singletonList(PrismConstants.Q_ANY);
        }
        return super.getRelationsForRecomputeTask();
    }

}
