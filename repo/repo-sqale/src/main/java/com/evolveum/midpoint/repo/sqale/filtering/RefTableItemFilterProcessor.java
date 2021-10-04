/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale.filtering;

import com.querydsl.core.types.Predicate;
import com.querydsl.sql.SQLQuery;

import com.evolveum.midpoint.prism.query.RefFilter;
import com.evolveum.midpoint.repo.sqale.qmodel.ref.MReference;
import com.evolveum.midpoint.repo.sqale.qmodel.ref.QReference;
import com.evolveum.midpoint.repo.sqale.qmodel.ref.QReferenceMapping;
import com.evolveum.midpoint.repo.sqlbase.SqlQueryContext;
import com.evolveum.midpoint.repo.sqlbase.filtering.item.ItemValueFilterProcessor;
import com.evolveum.midpoint.repo.sqlbase.querydsl.FlexibleRelationalPathBase;

/**
 * Filter processor for reference item paths resolved via {@link QReference} tables.
 * This just joins the reference table and then delegates to {@link RefItemFilterProcessor}.
 *
 * @param <Q> type of entity path for the reference table
 * @param <R> row type related to the {@link Q}
 * @param <OQ> query type of the reference owner
 * @param <OR> row type of the reference owner
 */
public class RefTableItemFilterProcessor<Q extends QReference<R, OR>, R extends MReference,
        OQ extends FlexibleRelationalPathBase<OR>, OR>
        extends ItemValueFilterProcessor<RefFilter> {

    private final SqlQueryContext<?, OQ, OR> context;
    private final QReferenceMapping<Q, R, OQ, OR> referenceMapping;

    public RefTableItemFilterProcessor(
            SqlQueryContext<?, OQ, OR> context, QReferenceMapping<Q, R, OQ, OR> referenceMapping) {
        super(context);
        this.context = context;
        this.referenceMapping = referenceMapping;
    }

    @Override
    public Predicate process(RefFilter filter) {
        SqlQueryContext<?, Q, R> refContext = context.subquery(referenceMapping);
        SQLQuery<?> subquery = refContext.sqlQuery();
        Q ref = refContext.path();
        subquery = subquery
                .where(referenceMapping.correlationPredicate().apply(context.path(), ref))
                .where(new RefItemFilterProcessor(
                        context, ref.targetOid, ref.targetType, ref.relationId, null, null)
                        .process(filter));
        if (filter.getValues() == null) {
            // If values == null, we search for all items without reference
            return subquery.notExists();
        }
        return subquery.exists();
    }
}
