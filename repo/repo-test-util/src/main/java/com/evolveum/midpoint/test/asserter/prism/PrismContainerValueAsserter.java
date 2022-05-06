/*
 * Copyright (c) 2018-2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.test.asserter.prism;

import java.util.Iterator;
import java.util.List;

import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;

import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.prism.Item;
import com.evolveum.midpoint.prism.ItemDefinition;
import com.evolveum.midpoint.prism.PrismContainer;
import com.evolveum.midpoint.prism.PrismContainerValue;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismProperty;
import com.evolveum.midpoint.prism.PrismReference;
import com.evolveum.midpoint.prism.PrismReferenceValue;
import com.evolveum.midpoint.prism.PrismValue;
import com.evolveum.midpoint.prism.equivalence.EquivalenceStrategy;
import com.evolveum.midpoint.prism.path.ItemName;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.util.PrismAsserts;
import com.evolveum.midpoint.test.asserter.predicates.AssertionPredicate;
import com.evolveum.midpoint.test.asserter.predicates.AssertionPredicateEvaluation;
import com.evolveum.midpoint.test.util.TestUtil;
import com.evolveum.midpoint.util.CheckedConsumer;
import com.evolveum.midpoint.util.PrettyPrinter;
import com.evolveum.midpoint.util.QNameUtil;
import com.evolveum.midpoint.util.exception.CommonException;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectReferenceType;
import com.evolveum.prism.xml.ns._public.types_3.RawType;

import org.jetbrains.annotations.Nullable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.AssertJUnit.*;

/**
 * @author semancik
 */
public class PrismContainerValueAsserter<C extends Containerable, RA> extends PrismValueAsserter<PrismContainerValue<C>, RA> {

    public PrismContainerValueAsserter(PrismContainerValue<C> prismValue) {
        super(prismValue);
    }

    public PrismContainerValueAsserter(PrismContainerValue<C> prismValue, String detail) {
        super(prismValue, detail);
    }

    public PrismContainerValueAsserter(PrismContainerValue<C> prismValue, RA returnAsserter, String detail) {
        super(prismValue, returnAsserter, detail);
    }

    public PrismContainerValueAsserter<C,RA> assertSize(int expected) {
        assertEquals("Wrong number of items in "+desc(), expected, getPrismValue().size());
        return this;
    }

    public PrismContainerValueAsserter<C,RA> assertItemsExactly(QName... expectedItems) {
        assertItemsPresent(expectedItems);
        for (Item<?, ?> existingItem : getPrismValue().getItems()) {
            if (!QNameUtil.contains(expectedItems, existingItem.getElementName())) {
                fail("Unexpected item "+existingItem.getElementName()+" in "+desc()+". Expected items: "+QNameUtil.prettyPrint(expectedItems));
            }
        }
        return this;
    }

    public PrismContainerValueAsserter<C,RA> assertItemsPresent(QName... expectedItems) {
        for (QName expectedItem: expectedItems) {
            Item<PrismValue,ItemDefinition> item = getPrismValue().findItem(ItemName.fromQName(expectedItem));
            if (item == null) {
                fail("Expected item "+expectedItem+" in "+desc()+" but there was none. Items present: "+presentItemNames());
            }
        }
        return this;
    }

    public PrismContainerValueAsserter<C,RA> assertItemsAbsent(QName... names) {
        for (QName name : names) {
            Item<PrismValue,ItemDefinition> item = getPrismValue().findItem(ItemName.fromQName(name));
            if (item != null && item.hasAnyValue()) {
                fail("Expected that item "+name+" is not in "+desc()+" but there was one.");
            }
        }
        return this;
    }

    public PrismContainerValueAsserter<C,RA> assertAny() {
        assertNotNull("No container value in "+desc(), getPrismValue());
        assertFalse("No items in "+desc(), getPrismValue().isEmpty());
        return this;
    }

    private String presentItemNames() {
        StringBuilder sb = new StringBuilder();
        Iterator<Item<?, ?>> iterator = getPrismValue().getItems().iterator();
        while (iterator.hasNext()) {
            sb.append(PrettyPrinter.prettyPrint(iterator.next().getElementName()));
            if (iterator.hasNext()) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    private <T> PrismProperty<T> findProperty(ItemPath path) {
        return getPrismValue().findProperty(path);
    }

    private <CC extends Containerable> PrismContainer<CC> findContainer(QName attrName) {
        return getPrismValue().findContainer(ItemName.fromQName(attrName));
    }

    private <T> Item<PrismValue,ItemDefinition> findItem(ItemPath itemPath) {
        return getPrismValue().findItem(itemPath);
    }

    public <T> PrismContainerValueAsserter<C,RA> assertPropertyEquals(ItemPath path, T expected) {
        PrismProperty<T> prop = findProperty(path);
        if (prop == null && expected == null) {
            return this;
        }
        assertNotNull("No property "+path+" in "+desc(), prop);
        T realValue = prop.getRealValue();
        assertNotNull("No value in "+path+" in "+desc(), realValue);
        assertEquals("Wrong "+path+" in "+desc(), expected, realValue);
        return this;
    }

    public <T> PrismContainerValueAsserter<C,RA> assertItemValueSatisfies(ItemPath path, AssertionPredicate<T> predicate) {
        T value = getItemRealValue(path);
        AssertionPredicateEvaluation evaluation = predicate.evaluate(value);
        if (evaluation.hasFailed()) {
            fail("Item " + path + " value of '" + value + "' does not satisfy predicate: " + evaluation.getFailureDescription());
        }
        return this;
    }

    @Nullable
    private <T> T getItemRealValue(ItemPath path) {
        Item<?, ?> item = findItem(path);
        //noinspection unchecked
        return item != null ? (T) item.getRealValue() : null;
    }

    public <T> PrismContainerValueAsserter<C,RA> sendItemValue(ItemPath path, CheckedConsumer<T> consumer) {
        T value = getItemRealValue(path);
        try {
            consumer.accept(value);
        } catch (CommonException e) {
            throw new SystemException(e);
        }
        return this;
    }

    public <T> PrismContainerValueAsserter<C,RA> assertPropertyValuesEqual(ItemPath path, T... expectedValues) {
        PrismProperty<T> property = findProperty(path);
        assertNotNull("No property "+ path +" in "+desc(), property);
        PrismAsserts.assertPropertyValueDesc(property, desc(), expectedValues);
        return this;
    }

    public <T> PrismContainerValueAsserter<C,RA> assertPropertyValuesEqualRaw(ItemPath path, T... expectedValues) {
        PrismProperty<T> property = findProperty(path);
        assertNotNull("No attribute "+ path +" in "+desc(), property);
        RawType[] expectedRaw = rawize(path, getPrismContext(), expectedValues);
        PrismAsserts.assertPropertyValueDesc(property, desc(), (T[])expectedRaw);
        return this;
    }

    private <T> RawType[] rawize(ItemPath path, PrismContext prismContext, T[] expectedValues) {
        RawType[] raws = new RawType[expectedValues.length];
        for(int i = 0; i < expectedValues.length; i++) {
            raws[i] = new RawType(prismContext.itemFactory().createPropertyValue(expectedValues[i]), path.lastName(), prismContext);
        }
        return raws;
    }

    public <T> PrismContainerValueAsserter<C,RA> assertRefEquals(QName refName, String expectedOid) {
        PrismReference ref = getPrismValue().findReference(refName);
        if (ref == null && expectedOid == null) {
            return this;
        }
        assertNotNull("No reference "+refName.getLocalPart()+" in "+desc(), ref);
        List<PrismReferenceValue> refVals = ref.getValues();
        if (refVals.isEmpty()) {
            fail("No values in reference "+refName.getLocalPart()+" in "+desc());
        }
        if (refVals.size() > 1) {
            fail("Too many values in reference "+refName.getLocalPart()+" in "+desc());
        }
        PrismReferenceValue refVal = refVals.get(0);
        assertNotNull("null value in "+refName.getLocalPart()+" in "+desc(), refVal);
        assertEquals("Wrong "+refName.getLocalPart()+" in "+desc(), expectedOid, refVal.getOid());
        return this;
    }

    public <T> PrismContainerValueAsserter<C,RA> assertRefEquals(QName refName, ObjectReferenceType expected) {
        return assertRefEquals(refName, expected, EquivalenceStrategy.REAL_VALUE);
    }

    public <T> PrismContainerValueAsserter<C,RA> assertRefEquals(QName refName, ObjectReferenceType expected, EquivalenceStrategy strategy) {
        PrismReference ref = getPrismValue().findReference(refName);
        if (ref == null && expected == null) {
            return this;
        }
        assertNotNull("No reference "+refName.getLocalPart()+" in "+desc(), ref);
        List<PrismReferenceValue> refVals = ref.getValues();
        if (refVals.isEmpty()) {
            fail("No values in reference "+refName.getLocalPart()+" in "+desc());
        }
        if (refVals.size() > 1) {
            fail("Too many values in reference "+refName.getLocalPart()+" in "+desc());
        }
        PrismReferenceValue refVal = refVals.get(0);
        assertNotNull("null value in "+refName.getLocalPart()+" in "+desc(), refVal);
        assertTrue("Wrong " + refName.getLocalPart() + " in " + desc() + ", expected: " + expected + ", real: " + refVal,
                expected.asReferenceValue().equals(refVal, strategy));
        return this;
    }

    public <T> PrismContainerValueAsserter<C,RA> assertNoItem(ItemPath itemName) {
        Item<PrismValue,ItemDefinition> item = findItem(itemName);
        assertNull("Unexpected item "+itemName+" in "+desc()+": "+item, item);
        return this;
    }

    public PrismContainerValueAsserter<C,RA> assertTimestampBetween(ItemPath path, XMLGregorianCalendar startTs, XMLGregorianCalendar endTs) {
        PrismProperty<XMLGregorianCalendar> property = findProperty(path);
        assertNotNull("No property "+path+" in "+desc(), property);
        XMLGregorianCalendar timestamp = property.getRealValue();
        assertNotNull("No value of property "+path+" in "+desc(), timestamp);
        TestUtil.assertBetween("Wrong value of property "+path+" in "+desc(), startTs, endTs, timestamp);
        return this;
    }

    public <CC extends Containerable> PrismContainerValueAsserter<CC,? extends PrismContainerValueAsserter<C,RA>> containerSingle(QName subcontainerQName) {
        PrismContainer<CC> container = findContainer(subcontainerQName);
        assertNotNull("No container "+subcontainerQName+" in "+desc(), container);
        PrismContainerValue<CC> pval = container.getValue();
        PrismContainerValueAsserter<CC,PrismContainerValueAsserter<C,RA>> asserter = new PrismContainerValueAsserter<>(pval, this, subcontainerQName.getLocalPart() + " in " + desc());
        copySetupTo(asserter);
        return asserter;
    }

    public <CC extends Containerable> PrismContainerAsserter<CC,? extends PrismContainerValueAsserter<C,RA>> container(QName subcontainerQName) {
        PrismContainer<CC> container = findContainer(subcontainerQName);
        assertNotNull("No container "+subcontainerQName+" in "+desc(), container);
        PrismContainerAsserter<CC,PrismContainerValueAsserter<C,RA>> asserter = new PrismContainerAsserter<>(container, this, subcontainerQName.getLocalPart() + " in " + desc());
        copySetupTo(asserter);
        return asserter;
    }

    public <T> PrismPropertyAsserter<T,? extends PrismContainerValueAsserter<C,RA>> property(ItemPath path) {
        PrismProperty<T> property = findProperty(path);
        assertNotNull("No property "+ path +" in "+desc(), property);
        PrismPropertyAsserter<T,? extends PrismContainerValueAsserter<C,RA>> asserter = new PrismPropertyAsserter<>(property, this, path + " in " + desc());
        copySetupTo(asserter);
        return asserter;
    }

    public PrismContainerValueAsserter<C, RA> assertAllItemsHaveCompleteDefinition() {
        assertThat(getPrismValue().hasCompleteDefinition())
                .withFailMessage("Some items have no complete definition") // we should tell which ones (some day)
                .isTrue();
        return this;
    }

    // TODO

    protected String desc() {
        return getDetails();
    }

}
