/*
 * Copyright (c) 2010-2018 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.model.intest.manual;

import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;
import org.testng.AssertJUnit;
import org.testng.annotations.Listeners;
import org.w3c.dom.Element;

import java.io.File;

/**
 * MID-4347
 *
 * @author Radovan Semancik
 */
@ContextConfiguration(locations = {"classpath:ctx-model-intest-test-main.xml"})
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
@Listeners({ com.evolveum.midpoint.tools.testng.AlphabeticalMethodInterceptor.class })
public class TestManualGrouping extends AbstractGroupingManualResourceTest {

    @Override
    protected String getResourceOid() {
        return RESOURCE_MANUAL_GROUPING_OID;
    }

    @Override
    protected File getResourceFile() {
        return RESOURCE_MANUAL_GROUPING_FILE;
    }

    @Override
    protected String getRoleOneOid() {
        return ROLE_ONE_MANUAL_GROUPING_OID;
    }

    @Override
    protected File getRoleOneFile() {
        return ROLE_ONE_MANUAL_GROUPING_FILE;
    }

    @Override
    protected String getRoleTwoOid() {
        return ROLE_TWO_MANUAL_GROUPING_OID;
    }

    @Override
    protected File getRoleTwoFile() {
        return ROLE_TWO_MANUAL_GROUPING_FILE;
    }

    @Override
    protected String getPropagationTaskOid() {
        return TASK_PROPAGATION_MANUAL_GROUPING_OID;
    }

    @Override
    protected File getPropagationTaskFile() {
        return TASK_PROPAGATION_MANUAL_GROUPING_FILE;
    }

    @Override
    protected void assertResourceSchemaBeforeTest(Element resourceXsdSchemaElementBefore) {
        AssertJUnit.assertNotNull("No schema before test connection. Bad test setup?", resourceXsdSchemaElementBefore);
    }

}
