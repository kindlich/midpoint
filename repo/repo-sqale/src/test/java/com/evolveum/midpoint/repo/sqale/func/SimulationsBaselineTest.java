package com.evolveum.midpoint.repo.sqale.func;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;

import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.repo.sqale.SqaleRepoBaseTest;
import com.evolveum.midpoint.schema.SearchResultList;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.util.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;

public class SimulationsBaselineTest extends SqaleRepoBaseTest {


    @Test
    public void test100CreateSimulation() throws ObjectAlreadyExistsException, SchemaException, ObjectNotFoundException {
        OperationResult result = createOperationResult();

        given("simulation result with a dummy system configuration object");
        SystemConfigurationType systemConfiguration = new SystemConfigurationType()
                .name("System Configuration")
                .description("dummy one");

        SimulationResultType obj = new SimulationResultType()
                .name("Test Simulation Result")
                .processedObject(new SimulationResultProcessedObjectType()
                    .oid("00000000-0000-0000-0000-000000000001")
                    .name("System Configuration")
                    .state(ObjectProcessingStateType.UNMODIFIED)
                    .metricIdentifier("disabled")
                    .metricIdentifier("business")
                    .before(systemConfiguration.clone())
                 );

        when("result is added to the repository");
        @NotNull String oid = repositoryService.addObject(obj.asPrismObject(), null, result);

        and("result is read back (as an object)");
        @NotNull PrismObject<SimulationResultType> resultReadBack =
                repositoryService.getObject(SimulationResultType.class, oid, null, result);

        then("result is OK but empty - processed objects should are available only via search");
        assertNotNull(resultReadBack);
        assertTrue(resultReadBack.asObjectable().getProcessedObject().isEmpty());

        when("processed objects are retrieved explicitly");
        SearchResultList<SimulationResultProcessedObjectType> processedObjects =
                repositoryService.searchContainers(SimulationResultProcessedObjectType.class, null, null, result);

        then("they are present");
        assertNotNull(processedObjects);
        assertThat(processedObjects).as("processed objects").hasSize(1);

        and("can be parsed");
        // TODO this should work, shouldn't it?
        //ObjectType objectBefore = processedObjects.get(0).getBefore();
        //assertThat(objectBefore).as("'object before' from result").isEqualTo(systemConfiguration);
    }

    @Test
    public void test110createTag() throws Exception {
        OperationResult result = createOperationResult();
        TagType obj = new TagType().name("testOfTest");
        String oid = repositoryService.addObject(obj.asPrismObject(), null, result);

        @NotNull
        PrismObject<TagType> readed = repositoryService.getObject(TagType.class, oid, null, result);
        assertNotNull(readed);
    }
}
