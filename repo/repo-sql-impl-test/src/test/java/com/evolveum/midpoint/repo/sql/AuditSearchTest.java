/*
 * Copyright (c) 2010-2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.repo.sql;

import static org.assertj.core.api.Assertions.assertThat;

import static com.evolveum.midpoint.schema.constants.SchemaConstants.CHANNEL_REST_URI;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.testng.annotations.Test;

import com.evolveum.midpoint.audit.api.AuditEventRecord;
import com.evolveum.midpoint.audit.api.AuditEventStage;
import com.evolveum.midpoint.audit.api.AuditEventType;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.schema.SearchResultList;
import com.evolveum.midpoint.schema.result.OperationResultStatus;
import com.evolveum.midpoint.task.api.test.NullTaskImpl;
import com.evolveum.midpoint.tools.testng.UnusedTestElement;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.audit_3.AuditEventRecordType;
import com.evolveum.midpoint.xml.ns._public.common.audit_3.AuditEventStageType;
import com.evolveum.midpoint.xml.ns._public.common.audit_3.AuditEventTypeType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.OperationResultStatusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;

@UnusedTestElement
@ContextConfiguration(locations = { "../../../../../ctx-test.xml" })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class AuditSearchTest extends BaseSQLRepoTest {

    public static final long TIMESTAMP_1 = 1577836800000L; // 2020-01-01
    public static final long TIMESTAMP_2 = 1580515200000L; // 2020-02-01
    public static final long TIMESTAMP_3 = 1583020800000L; // 2020-03-01

    @Autowired
    private DataSourceFactory dataSourceFactory;

    private String initiatorOid;

    @Override
    public void initSystem() throws Exception {
        PrismObject<UserType> initiator = new UserType(prismContext)
                .name("initiator")
                .asPrismObject();
        initiatorOid = repositoryService.addObject(initiator, null, createOperationResult());

        AuditEventRecord record1 = new AuditEventRecord();
        record1.addPropertyValue("prop", "val1");
        record1.setTimestamp(TIMESTAMP_1);
        record1.setEventType(AuditEventType.ADD_OBJECT);
        record1.setMessage("record1");
        record1.setOutcome(OperationResultStatus.SUCCESS);
        record1.setResult("result1");
        auditService.audit(record1, NullTaskImpl.INSTANCE);

        AuditEventRecord record2 = new AuditEventRecord();
        record2.addPropertyValue("prop", "val2");
        record2.setTimestamp(TIMESTAMP_2);
        record2.setEventType(AuditEventType.MODIFY_OBJECT);
        record2.setEventStage(AuditEventStage.EXECUTION);
        record2.setMessage("record2");
        record2.setOutcome(OperationResultStatus.UNKNOWN);
        record2.setInitiator(initiator);
        auditService.audit(record2, NullTaskImpl.INSTANCE);

        AuditEventRecord record3 = new AuditEventRecord();
        record3.addPropertyValue("prop", "val3-1");
        record3.addPropertyValue("prop", "val3-2");
        record3.addPropertyValue("prop", "val3-3");
        record3.setTimestamp(TIMESTAMP_3);
        record3.setEventType(AuditEventType.MODIFY_OBJECT);
        record3.setEventStage(AuditEventStage.EXECUTION);
        record3.setMessage("RECORD THREE");
        record3.setChannel(CHANNEL_REST_URI);
        // null outcome is kinda like "unknown", but not quite, filter must handle it
        auditService.audit(record3, NullTaskImpl.INSTANCE);

        // TODO remove after done
        try (Connection conn = dataSourceFactory.getDataSource().getConnection();
                PreparedStatement stmt = conn.prepareStatement(
                        "select id, message, timestampValue from M_AUDIT_EVENT"
                                + " WHERE timestampValue <= ?")) {
            stmt.setTimestamp(1, new Timestamp(TIMESTAMP_2));

            ResultSet res = stmt.executeQuery();
            while (res.next()) {
                Timestamp timestampValue = res.getTimestamp("timestampValue");
                System.out.println(res.getLong("id") + " - " + timestampValue + ": " + res.getString("message"));
                System.out.println("timestampValue = " + timestampValue.getTime());
            }
        }
    }

    @Test
    public void test100SearchAllAuditEvents() throws SchemaException {
        when("Searching audit with query without any conditions");
        ObjectQuery query = prismContext.queryFor(AuditEventRecordType.class).build();
        SearchResultList<AuditEventRecordType> result =
                auditService.searchObjects(query, null, null);

        then("All audit events are returned");
        assertThat(result).hasSize(3);
    }

    @Test
    public void test110SearchByEventType() throws SchemaException {
        when("searching audit filtered by event type");
        ObjectQuery query = prismContext.queryFor(AuditEventRecordType.class)
                .item(AuditEventRecordType.F_EVENT_TYPE).eq(AuditEventTypeType.ADD_OBJECT)
                .build();
        SearchResultList<AuditEventRecordType> result =
                auditService.searchObjects(query, null, null);

        then("only audit events of the specified type are returned");
        assertThat(result).hasSize(1);
        assertThat(result).allMatch(aer -> aer.getEventType() == AuditEventTypeType.ADD_OBJECT);
    }

    @Test
    public void test112SearchByEventStage() throws SchemaException {
        when("searching audit filtered by event stage");
        ObjectQuery query = prismContext.queryFor(AuditEventRecordType.class)
                .item(AuditEventRecordType.F_EVENT_STAGE).eq(AuditEventStageType.EXECUTION)
                .build();
        SearchResultList<AuditEventRecordType> result =
                auditService.searchObjects(query, null, null);

        then("only audit events with the specified stage are returned");
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(aer -> aer.getEventStage() == AuditEventStageType.EXECUTION);
    }

    @Test
    public void test114SearchByOutcome() throws SchemaException {
        when("searching audit filtered by outcome");
        ObjectQuery query = prismContext.queryFor(AuditEventRecordType.class)
                .item(AuditEventRecordType.F_OUTCOME).eq(OperationResultStatusType.UNKNOWN)
                .build();
        SearchResultList<AuditEventRecordType> result =
                auditService.searchObjects(query, null, null);

        then("only audit events with the specified outcome are returned");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOutcome()).isEqualTo(OperationResultStatusType.UNKNOWN);
    }

    @Test
    public void test115SearchByOutcomeIsNull() throws SchemaException {
        when("searching audit filtered by null outcome (enum)");
        ObjectQuery query = prismContext.queryFor(AuditEventRecordType.class)
                .item(AuditEventRecordType.F_OUTCOME).isNull()
                .build();
        SearchResultList<AuditEventRecordType> result =
                auditService.searchObjects(query, null, null);

        then("only audit events without any outcome are returned");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOutcome()).isNull();
    }

    @Test
    public void test118SearchByResultIsNull() throws SchemaException {
        when("searching audit filtered by null result (string)");
        ObjectQuery query = prismContext.queryFor(AuditEventRecordType.class)
                .item(AuditEventRecordType.F_RESULT).isNull()
                .build();
        SearchResultList<AuditEventRecordType> result =
                auditService.searchObjects(query, null, null);

        then("only audit events without any result are returned");
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(aer -> aer.getResult() == null);
    }

    @Test
    public void test120SearchByMessageEquals() throws SchemaException {
        when("searching audit filtered by message equal to value");
        ObjectQuery query = prismContext.queryFor(AuditEventRecordType.class)
                .item(AuditEventRecordType.F_MESSAGE).eq("record1")
                .build();
        SearchResultList<AuditEventRecordType> result =
                auditService.searchObjects(query, null, null);

        then("only audit events with exactly the same message are returned");
        assertThat(result).hasSize(1);
        assertThat(result).allMatch(aer -> aer.getMessage().equals("record1"));
    }

    @Test
    public void test121SearchByMessageEqualsIgnoreCase() throws SchemaException {
        when("searching audit filtered by message equal to value with ignore-case matcher");
        ObjectQuery query = prismContext.queryFor(AuditEventRecordType.class)
                .item(AuditEventRecordType.F_MESSAGE).eq("ReCoRd1").matchingCaseIgnore()
                .build();
        SearchResultList<AuditEventRecordType> result =
                auditService.searchObjects(query, null, null);

        then("only audit events with the same message ignoring case are returned");
        assertThat(result).hasSize(1);
        assertThat(result).allMatch(aer -> aer.getMessage().equals("record1"));
    }

    @Test
    public void test125SearchByMessageContains() throws SchemaException {
        when("searching audit filtered by message containing a string");
        ObjectQuery query = prismContext.queryFor(AuditEventRecordType.class)
                .item(AuditEventRecordType.F_MESSAGE).contains("ord")
                .build();
        SearchResultList<AuditEventRecordType> result =
                auditService.searchObjects(query, null, null);

        then("only audit events with the message containing the specified value are returned");
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(aer -> aer.getMessage().contains("ord"));
    }

    @Test
    public void test126SearchByMessageContainsIgnoreCase() throws SchemaException {
        when("searching audit filtered by message containing a string with ignore-case matcher");
        ObjectQuery query = prismContext.queryFor(AuditEventRecordType.class)
                .item(AuditEventRecordType.F_MESSAGE).contains("ord").matchingCaseIgnore()
                .build();
        SearchResultList<AuditEventRecordType> result =
                auditService.searchObjects(query, null, null);

        then("only audit events with the message containing the specified value ignoring case are returned");
        assertThat(result).hasSize(3);
    }

    @Test
    public void test130SearchByMessageStartsWith() throws SchemaException {
        when("searching audit filtered by message starting with a string");
        ObjectQuery query = prismContext.queryFor(AuditEventRecordType.class)
                .item(AuditEventRecordType.F_MESSAGE).startsWith("rec")
                .build();
        SearchResultList<AuditEventRecordType> result =
                auditService.searchObjects(query, null, null);

        then("only audit events with the message starting with the specified value are returned");
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(aer -> aer.getMessage().startsWith("rec"));
    }

    @Test
    public void test131SearchByMessageStartsWithIgnoreCase() throws SchemaException {
        when("searching audit filtered by message starting with a string with ignore-case matcher");
        ObjectQuery query = prismContext.queryFor(AuditEventRecordType.class)
                .item(AuditEventRecordType.F_MESSAGE).startsWith("rec").matchingCaseIgnore()
                .build();
        SearchResultList<AuditEventRecordType> result =
                auditService.searchObjects(query, null, null);

        then("only audit events with the message starting with the specified value ignoring case are returned");
        assertThat(result).hasSize(3);
    }

    @Test
    public void test135SearchByMessageEndsWith() throws SchemaException {
        when("searching audit filtered by message ending with a string");
        ObjectQuery query = prismContext.queryFor(AuditEventRecordType.class)
                .item(AuditEventRecordType.F_MESSAGE).endsWith("THREE")
                .build();
        SearchResultList<AuditEventRecordType> result =
                auditService.searchObjects(query, null, null);

        then("only audit events with the message ending with the specified value are returned");
        assertThat(result).hasSize(1);
        assertThat(result).allMatch(aer -> aer.getMessage().endsWith("THREE"));
    }

    @Test
    public void test136SearchByMessageEndsWithIgnoreCase() throws SchemaException {
        when("searching audit filtered by message ending with a string with ignore-case matcher");
        ObjectQuery query = prismContext.queryFor(AuditEventRecordType.class)
                .item(AuditEventRecordType.F_MESSAGE).endsWith("three").matchingCaseIgnore()
                .build();
        SearchResultList<AuditEventRecordType> result =
                auditService.searchObjects(query, null, null);

        then("only audit events with the message ending with the specified value ignoring case are returned");
        assertThat(result).hasSize(1);
    }

    @Test
    public void test140SearchByTimestampLessOrEqual() throws SchemaException {
        when("searching audit filtered by timestamp up to specified time");
        ObjectQuery query = prismContext.queryFor(AuditEventRecordType.class)
                .item(AuditEventRecordType.F_TIMESTAMP)
                .le(MiscUtil.asXMLGregorianCalendar(TIMESTAMP_2))
                .build();
        SearchResultList<AuditEventRecordType> result =
                auditService.searchObjects(query, null, null);

        then("only audit events with the timestamp less or equal to specified time are returned");
        assertThat(result).hasSize(2);
    }

    @Test
    public void test141SearchByTimestampEqual() throws SchemaException {
        when("searching audit filtered by timestamp equal to specified time");
        ObjectQuery query = prismContext.queryFor(AuditEventRecordType.class)
                .item(AuditEventRecordType.F_TIMESTAMP)
                .eq(MiscUtil.asXMLGregorianCalendar(TIMESTAMP_2))
                .build();
        SearchResultList<AuditEventRecordType> result =
                auditService.searchObjects(query, null, null);

        then("only audit events with the timestamp equal to are returned");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMessage()).isEqualTo("record2");
    }

    @Test
    public void test142SearchByTimestampGreater() throws SchemaException {
        when("searching audit filtered by timestamp up to specified time");
        ObjectQuery query = prismContext.queryFor(AuditEventRecordType.class)
                .item(AuditEventRecordType.F_TIMESTAMP)
                .gt(MiscUtil.asXMLGregorianCalendar(TIMESTAMP_2))
                .build();
        SearchResultList<AuditEventRecordType> result =
                auditService.searchObjects(query, null, null);

        then("only audit events with the timestamp less or equal are returned");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMessage()).isEqualTo("RECORD THREE");
    }

    @Test
    public void test150SearchByInitiator() throws SchemaException {
        when("searching audit filtered by initiator (reference by OID)");
        ObjectQuery query = prismContext.queryFor(AuditEventRecordType.class)
                .item(AuditEventRecordType.F_INITIATOR_REF)
                .ref(initiatorOid)
                .build();
        SearchResultList<AuditEventRecordType> result =
                auditService.searchObjects(query, null, null);

        then("only audit events with the specific initiator are returned");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMessage()).isEqualTo("record2");
        // TODO check mapping of initiator, see TODO in AuditEventRecordSqlTransformer#toAuditEventRecordType
    }

    // if this works, all other operations work too based on message related tests
    @Test
    public void test160SearchByChannel() throws SchemaException {
        when("searching audit filtered by channel equal to value");
        ObjectQuery query = prismContext.queryFor(AuditEventRecordType.class)
                .item(AuditEventRecordType.F_CHANNEL).eq(CHANNEL_REST_URI)
                .build();
        SearchResultList<AuditEventRecordType> result =
                auditService.searchObjects(query, null, null);

        then("only audit events with exactly the same channel are returned");
        assertThat(result).hasSize(1);
        assertThat(result).allMatch(aer -> aer.getChannel().equals(CHANNEL_REST_URI));
    }

    // complex filters with AND and/or OR

    @Test
    public void test500SearchByTwoTimestampConditions() throws SchemaException {
        when("searching audit filtered by timestamp AND timestamp condition");
        ObjectQuery query = prismContext.queryFor(AuditEventRecordType.class)
                .item(AuditEventRecordType.F_TIMESTAMP)
                .gt(MiscUtil.asXMLGregorianCalendar(TIMESTAMP_1)) // matches records 2 and 3
                .and()
                .item(AuditEventRecordType.F_TIMESTAMP)
                .lt(MiscUtil.asXMLGregorianCalendar(TIMESTAMP_3)) // matches records 1 and 2
                .build();
        SearchResultList<AuditEventRecordType> result =
                auditService.searchObjects(query, null, null);

        then("only audit events matching both timestamp conditions are returned");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMessage()).isEqualTo("record2");
    }

    @Test
    public void test501SearchByMessageAndTimestamp() throws SchemaException {
        when("searching audit filtered by timestamp AND message condition");
        ObjectQuery query = prismContext.queryFor(AuditEventRecordType.class)
                .item(AuditEventRecordType.F_TIMESTAMP)
                .gt(MiscUtil.asXMLGregorianCalendar(TIMESTAMP_1)) // matches records 2 and 3
                .and()
                .item(AuditEventRecordType.F_MESSAGE)
                .startsWith("rec") // matches records 1 and 2
                .build();
        SearchResultList<AuditEventRecordType> result =
                auditService.searchObjects(query, null, null);

        then("only audit events matching both conditions are returned");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMessage()).isEqualTo("record2");
    }

    @Test
    public void test510SearchByMessageOrTimestamp() throws SchemaException {
        when("searching audit filtered by timestamp AND message condition");
        ObjectQuery query = prismContext.queryFor(AuditEventRecordType.class)
                .item(AuditEventRecordType.F_TIMESTAMP)
                .lt(MiscUtil.asXMLGregorianCalendar(TIMESTAMP_2)) // matches only record 1
                .or()
                .item(AuditEventRecordType.F_MESSAGE)
                .endsWith("three").matchingCaseIgnore() // matches only record 3
                .build();
        SearchResultList<AuditEventRecordType> result =
                auditService.searchObjects(query, null, null);

        then("only audit events matching both conditions are returned");
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(aer ->
                aer.getMessage().equals("record1") || aer.getMessage().equals("RECORD THREE"));
    }

}
