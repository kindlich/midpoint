/*
 * Copyright (c) 2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.impl.page.admin.focus.component;

import com.evolveum.midpoint.gui.api.prism.wrapper.PrismObjectWrapper;
import com.evolveum.midpoint.gui.impl.page.admin.AbstractObjectMainPanel;
import com.evolveum.midpoint.gui.impl.page.admin.assignmentholder.FocusDetailsModels;
import com.evolveum.midpoint.gui.impl.prism.panel.SingleContainerPanel;
import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.prism.path.ItemName;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.application.PanelDisplay;
import com.evolveum.midpoint.web.application.PanelInstance;
import com.evolveum.midpoint.web.application.PanelType;
import com.evolveum.midpoint.web.model.PrismContainerWrapperModel;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import org.apache.wicket.model.IModel;

@PanelType(name = "password")
@PanelInstance(identifier = "password",
        applicableForType = UserType.class,
        display = @PanelDisplay(label = "prismPropertyPanel.name.credentials.password", order = 10))
public class FocusPasswordPanel<F extends FocusType, FDM extends FocusDetailsModels<F>> extends AbstractObjectMainPanel<F, FDM> {
    private static final long serialVersionUID = 1L;

    private static final Trace LOGGER = TraceManager.getTrace(FocusPasswordPanel.class);
    private static final String ID_PANEL = "panel";

    private static final String DOT_CLASS = FocusPasswordPanel.class.getName() + ".";

    public FocusPasswordPanel(String id, FDM model, ContainerPanelConfigurationType config) {
        super(id, model, config);
    }

    @Override
    protected void initLayout() {
        SingleContainerPanel panel =
                new SingleContainerPanel<>(ID_PANEL,
                        PrismContainerWrapperModel.fromContainerWrapper(getObjectWrapperModel(), ItemPath.create(FocusType.F_CREDENTIALS, CredentialsType.F_PASSWORD)),
                        PasswordType.COMPLEX_TYPE);
        add(panel);
    }
}
