/*
 * Copyright (c) 2010-2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.common.refinery;

import static com.evolveum.midpoint.schema.constants.SchemaConstants.RI_ACCOUNT_OBJECT_CLASS;

import static com.evolveum.midpoint.schema.processor.ResourceSchemaTestUtil.findObjectTypeDefinitionRequired;
import static com.evolveum.midpoint.test.util.MidPointTestConstants.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.AssertJUnit.*;

import static com.evolveum.midpoint.prism.util.PrismTestUtil.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.xml.namespace.QName;

import com.evolveum.midpoint.schema.SchemaService;
import com.evolveum.midpoint.schema.processor.ObjectFactory;

import org.testng.Assert;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;
import org.xml.sax.SAXException;

import com.evolveum.midpoint.schema.processor.ResourceObjectPattern;
import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.impl.match.MatchingRuleRegistryFactory;
import com.evolveum.midpoint.prism.path.ItemName;
import com.evolveum.midpoint.prism.query.EqualFilter;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.prism.util.PrismAsserts;
import com.evolveum.midpoint.prism.util.PrismTestUtil;
import com.evolveum.midpoint.schema.MidPointPrismContextFactory;
import com.evolveum.midpoint.schema.constants.MidPointConstants;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.processor.*;
import com.evolveum.midpoint.schema.util.ShadowUtil;
import com.evolveum.midpoint.test.util.TestUtil;
import com.evolveum.midpoint.tools.testng.AbstractUnitTest;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.util.PrettyPrinter;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

/**
 * Tests parsing {@link ResourceSchema} objects: both raw (object class) definitions and refined (object type) definitions.
 *
 * @author semancik
 */
@Listeners({ com.evolveum.midpoint.tools.testng.AlphabeticalMethodInterceptor.class })
public class TestRefinedSchema extends AbstractUnitTest {

    private static final String TEST_DIR_NAME = "src/test/resources/refinery";

    /** Contains 2 object class definitions and 2 object type definitions. */
    private static final File RESOURCE_COMPLEX_FILE = new File(TEST_DIR_NAME, "resource-complex.xml");

    /** Contains only 2 object class definitions. */
    private static final File RESOURCE_SIMPLE_FILE = new File(TEST_DIR_NAME, "resource-simple.xml");

    /** Contains both raw and refined definitions. Includes auxiliary object class. */
    private static final File RESOURCE_POSIX_FILE = new File(TEST_DIR_NAME, "resource-ldap-posix.xml");

    private static final String ENTITLEMENT_GROUP_INTENT = "group";
    private static final String ENTITLEMENT_LDAP_GROUP_INTENT = "ldapGroup";

    @BeforeSuite
    public void setup() throws SchemaException, SAXException, IOException {
        PrettyPrinter.setDefaultNamespacePrefix(MidPointConstants.NS_MIDPOINT_PUBLIC_PREFIX);
        resetPrismContext(MidPointPrismContextFactory.FACTORY);
        SchemaService.init(
                getPrismContext(),
                new RelationRegistryDummyImpl(),
                MatchingRuleRegistryFactory.createRegistry());
    }

    /**
     * This resource has both raw and refined schema.
     */
    @Test
    public void test010ParseFromResourceComplex() throws Exception {
        given();
        PrismContext prismContext = createInitializedPrismContext();

        PrismObject<ResourceType> resource = prismContext.parseObject(RESOURCE_COMPLEX_FILE);

        when();
        ResourceSchema schema = ResourceSchemaFactory.parseCompleteSchema(resource.asObjectable());

        then();
        assertNotNull("Refined schema is null", schema);
        System.out.println("Refined schema");
        System.out.println(schema.debugDump());
        assertResourceSchema(schema, null, LayerType.MODEL, true);

        assertLayerRefinedSchema(schema, LayerType.SCHEMA, LayerType.SCHEMA);
        assertLayerRefinedSchema(schema, LayerType.MODEL, LayerType.MODEL);
        assertLayerRefinedSchema(schema, LayerType.PRESENTATION, LayerType.PRESENTATION);

        ResourceObjectDefinition accountDef = schema.findDefaultDefinitionForKindRequired(ShadowKindType.ACCOUNT);
        ResourceAttributeDefinition<?> userPasswordAttribute = accountDef.findAttributeDefinition("userPassword");
        assertNotNull("No userPassword attribute", userPasswordAttribute);
        assertTrue("userPassword not ignored", userPasswordAttribute.isIgnored());
    }

    /**
     * Creates layered views on the resource schema (using `sourceLayer`), and then validates the information,
     * trying to retrieve the information by looking at `validationLayer`.
     */
    private void assertLayerRefinedSchema(
            ResourceSchema schema,
            LayerType sourceLayer,
            LayerType validationLayer) throws SchemaException {
        System.out.println("Refined schema: layer=" + sourceLayer);
        ResourceSchema lrSchema = schema.forLayerImmutable(sourceLayer);
        System.out.println(lrSchema.debugDump());
        assertResourceSchema(lrSchema, sourceLayer, validationLayer, true);
    }

    @Test
    public void test020ParseFromResourceSimple() throws Exception {
        // GIVEN
        PrismContext prismContext = createInitializedPrismContext();

        PrismObject<ResourceType> resource = prismContext.parseObject(RESOURCE_SIMPLE_FILE);
        ResourceType resourceType = resource.asObjectable();

        // WHEN
        ResourceSchema rSchema = ResourceSchemaFactory.parseCompleteSchema(resourceType);

        // THEN
        assertNotNull("Refined schema is null", rSchema);
        System.out.println("Refined schema");
        System.out.println(rSchema.debugDump());

        assertResourceSchema(rSchema, null, LayerType.SCHEMA, false);
    }

    /**
     * Asserts the whole schema.
     *
     * @param sourceLayer Descriptive information: if not null, what is the current viewing layer (used to restrict the schema)?
     * @param validationLayer What values to assert? We expect different views on e.g. model or schema layers.
     * @param refinementsPresent Are there refinements (schemaHandling) definitions for the resource?
     */
    private void assertResourceSchema(
            ResourceSchema schema,
            LayerType sourceLayer,
            LayerType validationLayer,
            boolean refinementsPresent) throws SchemaException {

        Collection<ResourceObjectClassDefinition> objectClassDefinitions = schema.getObjectClassDefinitions();
        Collection<ResourceObjectTypeDefinition> objectTypeDefinitions = schema.getObjectTypeDefinitions();

        assertThat(objectClassDefinitions)
                .as("object class definitions")
                .hasSize(2);

        assertThat(objectTypeDefinitions)
                .as("object type definitions")
                .hasSize(refinementsPresent ? 2 : 0);

        // Checking object type definition for ACCOUNT
        if (refinementsPresent) {
            assertThat(schema.getObjectTypeDefinitions(ShadowKindType.ACCOUNT))
                    .as("definitions for ACCOUNT type")
                    .hasSize(1);

            ResourceObjectDefinition accountTypeDef = schema.findDefaultDefinitionForKind(ShadowKindType.ACCOUNT);
            assertAccountObjectDefinition(accountTypeDef, sourceLayer, validationLayer);
            assertAccountEntitlements(schema, accountTypeDef);
            assertRefinedToLayer(accountTypeDef, sourceLayer);
        }

        // Checking object class definition for AccountObjectClass.
        ResourceObjectClassDefinition accountClassDef = schema.findObjectClassDefinitionRequired(RI_ACCOUNT_OBJECT_CLASS);
        assertThat(accountClassDef).as("account object class definition").isNotNull();
        assertAccountObjectDefinition(accountClassDef, sourceLayer, LayerType.SCHEMA);
    }

    private void assertAccountEntitlements(ResourceSchema schema, ResourceObjectDefinition accountDef) {
        assertFalse("No entitlement definitions", schema.getObjectTypeDefinitions(ShadowKindType.ENTITLEMENT).isEmpty());
        ResourceObjectTypeDefinition rEntDef =
                findObjectTypeDefinitionRequired(schema, ShadowKindType.ENTITLEMENT, null);
        findObjectTypeDefinitionRequired(schema, ShadowKindType.ENTITLEMENT, ENTITLEMENT_GROUP_INTENT);

        assertEquals("Wrong kind", ShadowKindType.ENTITLEMENT, rEntDef.getKind());

        Collection<? extends ResourceAttributeDefinition<?>> entAttrDefs = rEntDef.getAttributeDefinitions();
        assertNotNull("Null attributeDefinitions", entAttrDefs);
        assertFalse("Empty attributeDefinitions", entAttrDefs.isEmpty());
        assertEquals("Unexpected number of attributeDefinitions", 11, entAttrDefs.size());
        // TODO

        ResourceAttributeDefinition<?> entDisplayNameAttributeDef = rEntDef.getDisplayNameAttribute();
        assertNotNull("No entitlement displayNameAttribute", entDisplayNameAttributeDef);
        assertEquals("Wrong entitlement displayNameAttribute", QNAME_CN, entDisplayNameAttributeDef.getItemName());

        assertEquals("Unexpected number of entitlement associations", 1, accountDef.getAssociationDefinitions().size());
    }

    private void assertAccountObjectDefinition(
            ResourceObjectDefinition accountDef,
            LayerType sourceLayer,
            LayerType validationLayer) {

        assertRObjectClassDef(accountDef, sourceLayer, validationLayer);
        System.out.println("Refined account definition:");
        System.out.println(accountDef.debugDump());

        if (accountDef instanceof ResourceObjectTypeDefinition) {
            assertEquals("Wrong kind", ShadowKindType.ACCOUNT, ((ResourceObjectTypeDefinition) accountDef).getKind());
        }

        Collection<? extends ResourceAttributeDefinition<?>> accAttrsDef = accountDef.getAttributeDefinitions();
        assertNotNull("Null attributeDefinitions", accAttrsDef);
        assertFalse("Empty attributeDefinitions", accAttrsDef.isEmpty());
        assertEquals("Unexpected number of attributeDefinitions", 55, accAttrsDef.size());

        ResourceAttributeDefinition<?> disabledAttribute = accountDef.findAttributeDefinition("ds-pwp-account-disabled");
        assertNotNull("No ds-pwp-account-disabled attribute", disabledAttribute);
        assertTrue("ds-pwp-account-disabled not ignored", disabledAttribute.isIgnored());

        ResourceAttributeDefinition<?> displayNameAttributeDef = accountDef.getDisplayNameAttribute();
        assertNotNull("No account displayNameAttribute", displayNameAttributeDef);
        assertEquals("Wrong account displayNameAttribute", QNAME_UID, displayNameAttributeDef.getItemName());

        // This is compatibility with PrismContainerDefinition, it should work well
        Collection<? extends ItemDefinition<?>> propertyDefinitions = accountDef.getDefinitions();
        assertNotNull("Null propertyDefinitions", propertyDefinitions);
        assertFalse("Empty propertyDefinitions", propertyDefinitions.isEmpty());
        assertEquals("Unexpected number of propertyDefinitions", 55, propertyDefinitions.size());

        ResourceAttributeContainerDefinition resAttrContainerDef = accountDef.toResourceAttributeContainerDefinition();
        assertNotNull("No ResourceAttributeContainerDefinition", resAttrContainerDef);
        System.out.println("\nResourceAttributeContainerDefinition (" + sourceLayer + ")");
        System.out.println(resAttrContainerDef.debugDump());

        ResourceObjectDefinition rComplexTypeDefinition = resAttrContainerDef.getComplexTypeDefinition();
        System.out.println("\nResourceAttributeContainerDefinition ComplexTypeDefinition (" + sourceLayer + ")");
        System.out.println(rComplexTypeDefinition.debugDump());
        if (accountDef instanceof ResourceObjectTypeDefinition) {
            assertRefinedToLayer(rComplexTypeDefinition, sourceLayer);
        }

        ResourceAttributeDefinition<?> riUidAttrDef = resAttrContainerDef.findAttributeDefinition(new ItemName(MidPointConstants.NS_RI, "uid"));
        assertNotNull("No ri:uid def in ResourceAttributeContainerDefinition", riUidAttrDef);
        System.out.println("\nri:uid def " + riUidAttrDef.getClass() + " (" + sourceLayer + ")");
        System.out.println(riUidAttrDef.debugDump());

        if (accountDef instanceof ResourceObjectTypeDefinition) {
            assertRefinedToLayer(riUidAttrDef, sourceLayer);

            if (validationLayer == LayerType.PRESENTATION) {
                assertFalse("Can update " + riUidAttrDef + " from ResourceAttributeContainerDefinition (" + sourceLayer + ")",
                        riUidAttrDef.canModify());
            } else {
                assertTrue("Cannot update " + riUidAttrDef + " from ResourceAttributeContainerDefinition (" + sourceLayer + ")",
                        riUidAttrDef.canModify());
            }

            Collection<? extends ResourceAttributeDefinition<?>> definitionsFromResAttrContainerDef =
                    resAttrContainerDef.getDefinitions();
            for (ResourceAttributeDefinition<?> definitionFromResAttrContainerDef : definitionsFromResAttrContainerDef) {
                assertRefinedToLayer(definitionFromResAttrContainerDef, sourceLayer);
            }
        }
    }

    private void assertRefinedToLayer(
            ResourceObjectDefinition ocDef,
            LayerType expectedLayer) {
        if (expectedLayer == null) {
            // This is OK, it may not be layer-bound.
            return;
        }
        assertEquals("Wrong layer in " + ocDef, expectedLayer, ocDef.getCurrentLayer());
    }

    private void assertRefinedToLayer(ResourceAttributeDefinition<?> attrDef, LayerType expectedLayer) {
        if (expectedLayer == null) {
            // This is OK, it may not be layer-bound.
            return;
        }
        assertEquals("Wrong layer in " + attrDef, expectedLayer, attrDef.getCurrentLayer());
    }

    @Test
    public void test100ParseAccount() throws Exception {
        // GIVEN
        PrismContext prismContext = createInitializedPrismContext();

        PrismObject<ResourceType> resource = prismContext.parseObject(RESOURCE_COMPLEX_FILE);
        ResourceType resourceType = resource.asObjectable();

        ResourceSchema rSchema = ResourceSchemaFactory.parseCompleteSchema(resourceType);
        assertThat(rSchema).isNotNull();
        ResourceObjectDefinition defaultAccountDefinition = rSchema.findDefaultDefinitionForKind(ShadowKindType.ACCOUNT);
        assertNotNull("No refined default account definition in " + rSchema, defaultAccountDefinition);

        PrismObject<ShadowType> accObject = prismContext.parseObject(new File(TEST_DIR_NAME, "account-jack.xml"));

        // WHEN

        PrismObjectDefinition<ShadowType> objectDefinition = defaultAccountDefinition.getPrismObjectDefinition();

        System.out.println("Refined account definition:");
        System.out.println(objectDefinition.debugDump());

        accObject.applyDefinition(objectDefinition);

        // THEN

        System.out.println("Parsed account:");
        System.out.println(accObject.debugDump());

        assertAccountShadow(accObject, resource);
    }

    @Test
    public void test110ApplyAttributeDefinition() throws Exception {
        // GIVEN
        PrismContext prismContext = createInitializedPrismContext();

        PrismObject<ResourceType> resource = prismContext.parseObject(RESOURCE_COMPLEX_FILE);

        ResourceSchema rSchema = ResourceSchemaFactory.parseCompleteSchema(resource.asObjectable());
        System.out.println("Refined schema:");
        System.out.println(rSchema.debugDump(1));
        ResourceObjectDefinition defaultAccountDefinition = rSchema.findDefaultDefinitionForKind(ShadowKindType.ACCOUNT);
        assertNotNull("No refined default account definition in " + rSchema, defaultAccountDefinition);
        System.out.println("Refined account definition:");
        System.out.println(defaultAccountDefinition.debugDump(1));

        PrismObject<ShadowType> accObject = prismContext.parseObject(new File(TEST_DIR_NAME, "account-jack.xml"));
        PrismContainer<Containerable> attributesContainer = accObject.findContainer(ShadowType.F_ATTRIBUTES);
        System.out.println("Attributes container:");
        System.out.println(attributesContainer.debugDump(1));

        // WHEN
        //noinspection unchecked,rawtypes
        attributesContainer.applyDefinition(
                (PrismContainerDefinition) defaultAccountDefinition.toResourceAttributeContainerDefinition(), true);

        // THEN
        System.out.println("Parsed account:");
        System.out.println(accObject.debugDump(1));

        assertAccountShadow(accObject, resource);
    }

    private void assertAccountShadow(PrismObject<ShadowType> accObject, PrismObject<ResourceType> resource) throws SchemaException {
        PrismAsserts.assertPropertyValue(accObject, ShadowType.F_NAME, createPolyString("jack"));
        PrismAsserts.assertPropertyValue(accObject, ShadowType.F_OBJECT_CLASS, (QName) RI_ACCOUNT_OBJECT_CLASS);
        PrismAsserts.assertPropertyValue(accObject, ShadowType.F_INTENT, SchemaConstants.INTENT_DEFAULT);

        PrismContainer<?> attributes = accObject.findOrCreateContainer(SchemaConstants.C_ATTRIBUTES);
        assertEquals("Wrong type of <attributes> definition in account", ResourceAttributeContainerDefinitionImpl.class, attributes.getDefinition().getClass());
        ResourceAttributeContainerDefinition attrDef = (ResourceAttributeContainerDefinition) attributes.getDefinition();
        assertAttributeDefs(attrDef, null, LayerType.MODEL);

        PrismAsserts.assertPropertyValue(attributes, SchemaConstants.ICFS_NAME, "uid=jack,ou=People,dc=example,dc=com");
        PrismAsserts.assertPropertyValue(attributes, QNAME_CN, "Jack Sparrow");
        PrismAsserts.assertPropertyValue(attributes, QNAME_GIVEN_NAME, "Jack");
        PrismAsserts.assertPropertyValue(attributes, QNAME_SN, "Sparrow");
        PrismAsserts.assertPropertyValue(attributes, QNAME_UID, "jack");

        assertEquals("JAXB class name doesn't match (1)", ShadowType.class, accObject.getCompileTimeClass());

        accObject.checkConsistence();

        ShadowType accObjectType = accObject.asObjectable();
        assertEquals("Wrong JAXB name", createPolyStringType("jack"), accObjectType.getName());
        assertEquals("Wrong JAXB objectClass", RI_ACCOUNT_OBJECT_CLASS, accObjectType.getObjectClass());
        ShadowAttributesType attributesType = accObjectType.getAttributes();
        assertNotNull("null ResourceObjectShadowAttributesType (JAXB)", attributesType);
        List<Object> attributeElements = attributesType.getAny();
        TestUtil.assertElement(attributeElements, SchemaConstants.ICFS_NAME, "uid=jack,ou=People,dc=example,dc=com");
        TestUtil.assertElement(attributeElements, QNAME_CN, "Jack Sparrow");
        TestUtil.assertElement(attributeElements, QNAME_GIVEN_NAME, "Jack");
        TestUtil.assertElement(attributeElements, QNAME_SN, "Sparrow");
        TestUtil.assertElement(attributeElements, QNAME_UID, "jack");

        String accString = PrismTestUtil.serializeObjectToString(accObjectType.asPrismObject());
        System.out.println("Result of JAXB marshalling:\n" + accString);

        accObject.checkConsistence(true, true, ConsistencyCheckScope.THOROUGH);
    }

    private ItemName getAttrQName(PrismObject<ResourceType> resource, String localPart) {
        return new ItemName(MidPointConstants.NS_RI, localPart);
    }

    @Test
    public void test120CreateShadow() throws Exception {
        // GIVEN
        PrismContext prismContext = createInitializedPrismContext();

        PrismObject<ResourceType> resource = prismContext.parseObject(RESOURCE_COMPLEX_FILE);
        ResourceType resourceType = resource.asObjectable();

        ResourceSchema rSchema = ResourceSchemaFactory.parseCompleteSchema(resourceType);
        assertNotNull("Refined schema is null", rSchema);
        assertFalse("No account definitions", rSchema.getObjectTypeDefinitions(ShadowKindType.ACCOUNT).isEmpty());
        ResourceObjectTypeDefinition rAccount = findObjectTypeDefinitionRequired(rSchema, ShadowKindType.ACCOUNT, null);

        // WHEN
        PrismObject<ShadowType> blankShadow = rAccount.createBlankShadow(resource.getOid(), "foo");

        // THEN
        assertNotNull("No blank shadow", blankShadow);
        assertNotNull("No prism context in blank shadow", blankShadow.getPrismContext());
        PrismObjectDefinition<ShadowType> objectDef = blankShadow.getDefinition();
        assertNotNull("Blank shadow has no definition", objectDef);
        PrismContainerDefinition<?> attrDef = objectDef.findContainerDefinition(ShadowType.F_ATTRIBUTES);
        assertNotNull("Blank shadow has no definition for attributes", attrDef);
        assertTrue("Wrong class for attributes definition: " + attrDef.getClass(), attrDef instanceof ResourceAttributeContainerDefinition);
        assertEquals("Wrong tag", "foo", blankShadow.asObjectable().getTag());

    }

    @Test
    public void test130ProtectedAccount() throws Exception {
        // GIVEN
        PrismContext prismContext = createInitializedPrismContext();
        PrismObject<ResourceType> resource = prismContext.parseObject(RESOURCE_COMPLEX_FILE);
        ResourceType resourceType = resource.asObjectable();
        ResourceSchema rSchema = ResourceSchemaFactory.parseCompleteSchema(resourceType);
        assertNotNull("Refined schema is null", rSchema);
        assertFalse("No account definitions", rSchema.getObjectTypeDefinitions(ShadowKindType.ACCOUNT).isEmpty());
        ResourceObjectTypeDefinition rAccount = findObjectTypeDefinitionRequired(rSchema, ShadowKindType.ACCOUNT, null);

        // WHEN
        Collection<ResourceObjectPattern> protectedAccounts = rAccount.getProtectedObjectPatterns();

        // THEN
        assertNotNull("Null protectedAccounts", protectedAccounts);
        assertFalse("Empty protectedAccounts", protectedAccounts.isEmpty());
        assertEquals("Unexpected number of protectedAccounts", 2, protectedAccounts.size());
        Iterator<ResourceObjectPattern> iterator = protectedAccounts.iterator();
        assertProtectedAccount("first protected account", iterator.next(), "uid=idm,ou=Administrators,dc=example,dc=com", rAccount);
        assertProtectedAccount("second protected account", iterator.next(), "uid=root,ou=Administrators,dc=example,dc=com", rAccount);
    }

    private void assertAttributeDefs(ResourceAttributeContainerDefinition attrsDef, LayerType sourceLayer, LayerType validationLayer) {
        assertNotNull("Null account definition", attrsDef);
        assertEquals("AccountObjectClass", attrsDef.getComplexTypeDefinition().getTypeName().getLocalPart());
        assertEquals("Wrong objectclass in the definition of <attributes> definition in account", ResourceObjectTypeDefinitionImpl.class, attrsDef.getComplexTypeDefinition().getClass());
        ResourceObjectTypeDefinition rAccount = (ResourceObjectTypeDefinition) attrsDef.getComplexTypeDefinition();
        assertRObjectClassDef(rAccount, sourceLayer, validationLayer);
    }

    private void assertRObjectClassDef(ResourceObjectDefinition rAccount, LayerType sourceLayer, LayerType validationLayer) {
        if (rAccount instanceof ResourceObjectTypeDefinition) {
            assertTrue(((ResourceObjectTypeDefinition) rAccount).isDefaultForKind());
        }

        Collection<? extends ResourceAttributeDefinition<?>> attrs = rAccount.getAttributeDefinitions();
        assertFalse(attrs.isEmpty());

        assertAttributeDef(attrs, SchemaConstants.ICFS_NAME, DOMUtil.XSD_STRING, 1, 1, "Distinguished Name", 110,
                true, false,
                true, true, validationLayer == LayerType.SCHEMA, // Access: create, read, update
                sourceLayer, validationLayer);

        assertAttributeDef(attrs, SchemaConstants.ICFS_UID, DOMUtil.XSD_STRING, 1, 1, "Entry UUID", 100,
                false, false,
                false, true, false, // Access: create, read, update
                sourceLayer, validationLayer);

        assertAttributeDef(attrs, QNAME_CN, DOMUtil.XSD_STRING,
                1, (validationLayer == LayerType.MODEL || validationLayer == LayerType.PRESENTATION) ? 1 : -1, "Common Name", 1,
                true, validationLayer == LayerType.PRESENTATION,
                true, true, true, // Access: create, read, update
                sourceLayer, validationLayer);

        assertAttributeDef(attrs, QNAME_UID,
                DOMUtil.XSD_STRING,
                validationLayer == LayerType.SCHEMA ? 0 : 1, // minOccurs
                validationLayer == LayerType.SCHEMA ? -1 : 1, // maxOccurs
                "Login Name", 2,
                true, false,
                true, true, validationLayer != LayerType.PRESENTATION, // Access: create, read, update
                sourceLayer, validationLayer);

        assertAttributeDef(attrs, QNAME_EMPLOYEE_NUMBER,
                DOMUtil.XSD_STRING, 0, 1, null, null,
                false, false,
                true, true, true, // Access: create, read, update
                sourceLayer, validationLayer);
    }

    private void assertAttributeDef(Collection<? extends ResourceAttributeDefinition<?>> attrDefs, QName name,
            QName typeName, int minOccurs, int maxOccurs, String displayName, Integer displayOrder,
            boolean hasOutbound, boolean ignore, boolean canCreate, boolean canRead, boolean canUpdate,
            LayerType sourceLayer, LayerType validationLayer) {
        for (ResourceAttributeDefinition<?> def : attrDefs) {
            if (def.getItemName().equals(name)) {
                assertEquals("Attribute " + name + " (" + sourceLayer + ") type mismatch", typeName, def.getTypeName());
                assertEquals("Attribute " + name + " (" + sourceLayer + ") minOccurs mismatch", minOccurs, def.getMinOccurs());
                assertEquals("Attribute " + name + " (" + sourceLayer + ") maxOccurs mismatch", maxOccurs, def.getMaxOccurs());
                if (validationLayer == LayerType.MODEL || validationLayer == LayerType.PRESENTATION) {
                    assertEquals("Attribute " + name + " (" + sourceLayer + ") displayName mismatch", displayName, def.getDisplayName());
                    assertEquals("Attribute " + name + " (" + sourceLayer + ") displayOrder mismatch", displayOrder, def.getDisplayOrder());
                    assertEquals("Attribute " + name + " (" + sourceLayer + ") outbound mismatch", hasOutbound, def.getOutboundMappingBean() != null);
                }
                assertEquals("Attribute " + name + " (" + sourceLayer + ") ignored flag mismatch", ignore, def.isIgnored());
                assertEquals("Attribute " + name + " (" + sourceLayer + ") canCreate mismatch", canCreate, def.canAdd());
                assertEquals("Attribute " + name + " (" + sourceLayer + ") canRead mismatch", canRead, def.canRead());
                assertEquals("Attribute " + name + " (" + sourceLayer + ") canUpdate mismatch", canUpdate, def.canModify());
                return;
            }
        }
        Assert.fail("Attribute " + name + " not found");
    }

    private void assertProtectedAccount(String message, ResourceObjectPattern protectedPattern, String identifierValue, ResourceObjectTypeDefinition rAccount) throws SchemaException {
        ObjectFilter filter = protectedPattern.getObjectFilter();
        assertNotNull("Null objectFilter in " + message, filter);
        assertTrue("Wrong filter class " + filter.getClass().getSimpleName() + " in " + message, filter instanceof EqualFilter);
        assertNotNull("Null filter path in " + message, ((EqualFilter) filter).getPath());
        assertEquals("Wrong filter value in " + message, identifierValue, ((EqualFilter<String>) filter).getValues().iterator().next().getValue());

        // Try matching
        PrismObject<ShadowType> shadow = rAccount.getPrismObjectDefinition().instantiate();
        ResourceAttributeContainer attributesContainer = ShadowUtil.getOrCreateAttributesContainer(shadow, rAccount);
        ResourceAttribute<String> confusingAttr1 = createStringAttribute(new QName("http://whatever.com", "confuseMe"), "HowMuchWoodWouldWoodchuckChuckIfWoodchuckCouldChudkWood");
        attributesContainer.add(confusingAttr1);
        ResourceAttribute<String> nameAttr = createStringAttribute(SchemaConstants.ICFS_NAME, identifierValue);
        attributesContainer.add(nameAttr);
        ResourceAttribute<String> confusingAttr2 = createStringAttribute(new QName("http://whatever.com", "confuseMeAgain"), "WoodchuckWouldChuckNoWoodAsWoodchuckCannotChuckWood");
        attributesContainer.add(confusingAttr2);

        assertTrue("Test attr not matched in " + message, protectedPattern.matches(shadow.asObjectable()));
        nameAttr.setRealValue("huhulumululul");
        assertFalse("Test attr nonsense was matched in " + message, protectedPattern.matches(shadow.asObjectable()));
    }

    private ResourceAttribute<String> createStringAttribute(QName attrName, String value) {
        RawResourceAttributeDefinition<String> testAttrDef =
                ObjectFactory.createResourceAttributeDefinition(attrName, DOMUtil.XSD_STRING);
        ResourceAttribute<String> testAttr = testAttrDef.instantiate();
        testAttr.setRealValue(value);
        return testAttr;
    }

    @Test
    public void test140ParseFromResourcePosix() throws Exception {
        // GIVEN
        PrismContext prismContext = createInitializedPrismContext();

        PrismObject<ResourceType> resource = prismContext.parseObject(RESOURCE_POSIX_FILE);
        ResourceType resourceType = resource.asObjectable();

        // WHEN
        when();
        ResourceSchema rSchema = ResourceSchemaFactory.parseCompleteSchema(resourceType);

        // THEN
        when();
        assertNotNull("Refined schema is null", rSchema);
        System.out.println("Refined schema");
        System.out.println(rSchema.debugDump());

        assertFalse("No account definitions", rSchema.getObjectTypeDefinitions(ShadowKindType.ACCOUNT).isEmpty());

        // ### default account objectType

        ResourceObjectTypeDefinition rAccountDef = findObjectTypeDefinitionRequired(rSchema, ShadowKindType.ACCOUNT, null);

        assertTrue(rAccountDef.isDefaultForKind());

        Collection<? extends ResourceAttributeDefinition<?>> rAccountAttrs = rAccountDef.getAttributeDefinitions();
        assertFalse(rAccountAttrs.isEmpty());

        assertAttributeDef(rAccountAttrs, QNAME_DN,
                DOMUtil.XSD_STRING, 1, 1, "Distinguished Name", 110,
                true, false,
                true, true, true, // Access: create, read, update
                LayerType.SCHEMA, LayerType.PRESENTATION);

        assertAttributeDef(rAccountAttrs, QNAME_ENTRY_UUID,
                DOMUtil.XSD_STRING, 0, 1, "entryUUID", 100,
                false, false,
                false, true, false, // Access: create, read, update
                LayerType.SCHEMA, LayerType.PRESENTATION);

        assertAttributeDef(rAccountAttrs, QNAME_CN,
                DOMUtil.XSD_STRING, 1, -1, "Common Name", 590,
                true, false,
                true, true, true, // Access: create, read, update
                LayerType.SCHEMA, LayerType.PRESENTATION);

        assertAttributeDef(rAccountAttrs, QNAME_UID,
                DOMUtil.XSD_STRING, 0, -1, "Login Name", 300,
                true, false,
                true, true, true, // Access: create, read, update
                LayerType.SCHEMA, LayerType.PRESENTATION);

        assertAttributeDef(rAccountAttrs, QNAME_EMPLOYEE_NUMBER,
                DOMUtil.XSD_STRING, 0, 1, "employeeNumber", 140,
                false, false,
                true, true, true, // Access: create, read, update
                LayerType.SCHEMA, LayerType.PRESENTATION);

        System.out.println("Refined account definitionn:");
        System.out.println(rAccountDef.debugDump());

        assertEquals("Wrong kind", ShadowKindType.ACCOUNT, rAccountDef.getKind());

        Collection<? extends ResourceAttributeDefinition<?>> accAttrsDef = rAccountDef.getAttributeDefinitions();
        assertNotNull("Null attributeDefinitions", accAttrsDef);
        assertFalse("Empty attributeDefinitions", accAttrsDef.isEmpty());
        assertEquals("Unexpected number of attributeDefinitions", 53, accAttrsDef.size());

        ResourceAttributeDefinition<?> disabledAttribute = rAccountDef.findAttributeDefinition("ds-pwp-account-disabled");
        assertNotNull("No ds-pwp-account-disabled attribute", disabledAttribute);
        assertTrue("ds-pwp-account-disabled not ignored", disabledAttribute.isIgnored());

        ResourceAttributeDefinition<?> displayNameAttributeDef = rAccountDef.getDisplayNameAttribute();
        assertNotNull("No account displayNameAttribute", displayNameAttributeDef);
        assertEquals("Wrong account displayNameAttribute", QNAME_DN,
                displayNameAttributeDef.getItemName());

        // This is compatibility with PrismContainerDefinition, it should work well
        Collection<? extends ItemDefinition> propertyDefinitions = rAccountDef.getDefinitions();
        assertNotNull("Null propertyDefinitions", propertyDefinitions);
        assertFalse("Empty propertyDefinitions", propertyDefinitions.isEmpty());
        assertEquals("Unexpected number of propertyDefinitions", 53, propertyDefinitions.size());

        assertFalse("No entitlement definitions", rSchema.getObjectTypeDefinitions(ShadowKindType.ENTITLEMENT).isEmpty());
        ResourceObjectTypeDefinition rEntDef = findObjectTypeDefinitionRequired(rSchema, ShadowKindType.ENTITLEMENT, null);
        findObjectTypeDefinitionRequired(rSchema, ShadowKindType.ENTITLEMENT, ENTITLEMENT_LDAP_GROUP_INTENT);

        assertEquals("Wrong kind", ShadowKindType.ENTITLEMENT, rEntDef.getKind());

        Collection<? extends ResourceAttributeDefinition<?>> entAttrDefs = rEntDef.getAttributeDefinitions();
        assertNotNull("Null attributeDefinitions", entAttrDefs);
        assertFalse("Empty attributeDefinitions", entAttrDefs.isEmpty());
        assertEquals("Unexpected number of attributeDefinitions", 12, entAttrDefs.size());

        ResourceAttributeDefinition<?> entDisplayNameAttributeDef = rEntDef.getDisplayNameAttribute();
        assertNotNull("No entitlement displayNameAttribute", entDisplayNameAttributeDef);
        assertEquals("Wrong entitlement displayNameAttribute", QNAME_DN,
                entDisplayNameAttributeDef.getItemName());

        assertEquals("Unexpected number of entitlement associations", 1, rAccountDef.getAssociationDefinitions().size());

        ResourceAttributeContainerDefinition resAttrContainerDef = rAccountDef.toResourceAttributeContainerDefinition();
        assertNotNull("No ResourceAttributeContainerDefinition", resAttrContainerDef);
        System.out.println("\nResourceAttributeContainerDefinition");
        System.out.println(resAttrContainerDef.debugDump());

        ResourceObjectDefinition rComplexTypeDefinition = resAttrContainerDef.getComplexTypeDefinition();
        System.out.println("\nResourceAttributeContainerDefinition ComplexTypeDefinition");
        System.out.println(rComplexTypeDefinition.debugDump());

        ResourceAttributeDefinition<?> riUidAttrDef = resAttrContainerDef.findAttributeDefinition(
                new ItemName(MidPointConstants.NS_RI, "uid"));
        assertNotNull("No ri:uid def in ResourceAttributeContainerDefinition", riUidAttrDef);
        System.out.println("\nri:uid def " + riUidAttrDef.getClass());
        System.out.println(riUidAttrDef.debugDump());

    }

    // MID-5648
    @Test
    public void test200ParseFromResourceMultithreaded() throws Exception {
        int THREADS = 50;

        // GIVEN
        PrismContext prismContext = createInitializedPrismContext();

        PrismObject<ResourceType> resource = prismContext.parseObject(RESOURCE_COMPLEX_FILE);
        ResourceType resourceType = resource.asObjectable();

        // WHEN
        when();

        AtomicInteger errors = new AtomicInteger(0);
        List<Thread> threads = new ArrayList<>(THREADS);
        for (int i = 0; i < THREADS; i++) {
            Thread thread = new Thread(() -> {
                try {
                    ResourceSchemaFactory.parseCompleteSchema(resourceType);
                } catch (Throwable t) {
                    errors.incrementAndGet();
                    throw new AssertionError("Got exception: " + t.getMessage(), t);
                }
            });
            thread.setName("Executor #" + i);
            thread.start();
            threads.add(thread);
        }

        // THEN
        when();
        TestUtil.waitForCompletion(threads, 20000);

        assertEquals("Wrong # of errors", 0, errors.get());
        // TODO some asserts on correct parsing maybe
    }
}
