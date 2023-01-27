/*
 * Copyright (c) 2010-2023 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.gui.impl.page.admin.simulation;

import org.apache.wicket.RestartResponseException;
import org.apache.wicket.request.component.IRequestablePage;
import org.apache.wicket.request.mapper.parameter.PageParameters;

import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.api.util.WebModelServiceUtils;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.web.page.error.PageError404;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SimulationResultType;

/**
 * Created by Viliam Repan (lazyman).
 */
public interface SimulationPage extends IRequestablePage {

    String PAGE_PARAMETER_RESULT_OID = "RESULT_OID";
    String PAGE_PARAMETER_TAG_OID = "TAG_OID";
    String PAGE_PARAMETER_CONTAINER_ID = "CONTAINER_ID";

    String DOT_CLASS = SimulationPage.class.getName() + ".";

    String OPERATION_LOAD_RESULT = DOT_CLASS + "loadResult";

    default String getPageParameterResultOid() {
        PageParameters params = getPageParameters();
        return params.get(PAGE_PARAMETER_RESULT_OID).toString();
    }

    default String getPageParameterTagOid() {
        PageParameters params = getPageParameters();
        return params.get(PAGE_PARAMETER_TAG_OID).toString();
    }

    default String getPageParameterContainerId() {
        PageParameters params = getPageParameters();
        return params.get(PAGE_PARAMETER_CONTAINER_ID).toString();
    }

    default SimulationResultType loadSimulationResult(PageBase page) {
        String resultOid = getPageParameterResultOid();

        Task task = page.getPageTask();
        OperationResult result = task.getResult().createSubresult(OPERATION_LOAD_RESULT);

        PrismObject<SimulationResultType> object = WebModelServiceUtils.loadObject(
                SimulationResultType.class, resultOid, page, task, result);
        if (object == null) {
            throw new RestartResponseException(PageError404.class);
        }
        result.computeStatusIfUnknown();

        return object.asObjectable();
    }
}
