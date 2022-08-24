/*
 * Copyright (c) 2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.gui.impl.page.self.requestAccess;

import java.util.List;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;

import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.ajax.markup.html.form.AjaxSubmitLink;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

import com.evolveum.midpoint.gui.api.component.BasePanel;
import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.prism.xml.XmlTypeConverter;
import com.evolveum.midpoint.web.component.dialog.Popupable;
import com.evolveum.midpoint.web.component.util.EnableBehaviour;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ActivationStatusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ActivationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AssignmentType;

/**
 * Created by Viliam Repan (lazyman).
 */
public class ShoppingCartEditPanel extends BasePanel<ShoppingCartItem> implements Popupable {

    private static final long serialVersionUID = 1L;

    private static final String ID_BUTTONS = "buttons";
    private static final String ID_SAVE = "save";
    private static final String ID_CLOSE = "close";
    private static final String ID_RELATION = "relation";
    private static final String ID_ADMINISTRATIVE_STATUS = "administrativeStatus";
    private static final String ID_CUSTOM_VALIDITY = "customValidity";

    private Fragment footer;

    private IModel<RequestAccess> requestAccess;

    private IModel<List<QName>> relationChoices;

    private IModel<CustomValidity> customValidityModel;

    private boolean validitySettingsEnabled;

    public ShoppingCartEditPanel(IModel<ShoppingCartItem> model, IModel<RequestAccess> requestAccess, boolean validitySettingsEnabled) {
        super(Popupable.ID_CONTENT, model);

        this.requestAccess = requestAccess;
        this.validitySettingsEnabled = validitySettingsEnabled;

        initModels();
        initLayout();
        initFooter();
    }

    private void initModels() {
        relationChoices = new LoadableModel<>(false) {

            @Override
            protected List<QName> load() {
                return requestAccess.getObject().getAvailableRelations(getPageBase());
            }
        };

        customValidityModel = new LoadableModel<>(false) {

            @Override
            protected CustomValidity load() {
                ShoppingCartItem item = getModelObject();

                ActivationType activation = item.getAssignment().getActivation();
                if (activation == null) {
                    return new CustomValidity();
                }

                CustomValidity cv = new CustomValidity();
                cv.setFrom(XmlTypeConverter.toDate(activation.getValidFrom()));
                cv.setTo(XmlTypeConverter.toDate(activation.getValidTo()));

                return cv;
            }
        };
    }

    private void initLayout() {
        DropDownChoice relation = new DropDownChoice(ID_RELATION, () -> requestAccess.getObject().getRelation(), relationChoices,
                WebComponentUtil.getRelationChoicesRenderer());
        relation.add(new EnableBehaviour(() -> false));
        add(relation);

        IModel<ActivationStatusType> model = new Model<>() {

            @Override
            public ActivationStatusType getObject() {
                AssignmentType assignment = getModelObject().getAssignment();
                ActivationType activation = assignment.getActivation();
                if (activation == null) {
                    return null;
                }

                return activation.getAdministrativeStatus();
            }

            @Override
            public void setObject(ActivationStatusType status) {
                AssignmentType assignment = getModelObject().getAssignment();
                ActivationType activation = assignment.getActivation();
                if (activation == null) {
                    activation = new ActivationType();
                    assignment.setActivation(activation);
                }

                activation.setAdministrativeStatus(status);
            }
        };

        DropDownChoice administrativeStatus = new DropDownChoice(ID_ADMINISTRATIVE_STATUS, model,
                WebComponentUtil.createReadonlyModelFromEnum(ActivationStatusType.class),
                WebComponentUtil.getEnumChoiceRenderer(this));
        administrativeStatus.setNullValid(true);
        add(administrativeStatus);

        CustomValidityPanel customValidity = new CustomValidityPanel(ID_CUSTOM_VALIDITY, customValidityModel);
        customValidity.add(new EnableBehaviour(() -> validitySettingsEnabled));
        add(customValidity);
    }

    private void initFooter() {
        footer = new Fragment(Popupable.ID_FOOTER, ID_BUTTONS, this);
        footer.add(new AjaxSubmitLink(ID_SAVE) {

            @Override
            protected void onSubmit(AjaxRequestTarget target) {
                ShoppingCartItem item = getModelObject();
                AssignmentType assignment = item.getAssignment();
                ActivationType activation = assignment.getActivation();
                if (activation == null) {
                    activation = new ActivationType();
                    assignment.setActivation(activation);
                }

                CustomValidity cv = customValidityModel.getObject();
                XMLGregorianCalendar from = XmlTypeConverter.createXMLGregorianCalendar(cv.getFrom());
                XMLGregorianCalendar to = XmlTypeConverter.createXMLGregorianCalendar(cv.getTo());

                activation.validFrom(from).validTo(to);

                savePerformed(target, ShoppingCartEditPanel.this.getModel());
            }
        });
        footer.add(new AjaxLink<>(ID_CLOSE) {

            @Override
            public void onClick(AjaxRequestTarget target) {
                closePerformed(target, ShoppingCartEditPanel.this.getModel());
            }
        });
    }

    @Override
    public Component getFooter() {
        return footer;
    }

    @Override
    public int getWidth() {
        return 500;
    }

    @Override
    public int getHeight() {
        return 100;
    }

    @Override
    public String getWidthUnit() {
        return "px";
    }

    @Override
    public String getHeightUnit() {
        return "px";
    }

    @Override
    public IModel<String> getTitle() {
        return () -> getString("ShoppingCartEditPanel.title", getModelObject().getName());
    }

    @Override
    public Component getContent() {
        return this;
    }

    protected void savePerformed(AjaxRequestTarget target, IModel<ShoppingCartItem> model) {

    }

    protected void closePerformed(AjaxRequestTarget target, IModel<ShoppingCartItem> model) {

    }
}
