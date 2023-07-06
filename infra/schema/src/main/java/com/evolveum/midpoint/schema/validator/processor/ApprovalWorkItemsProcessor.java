/*
 * Copyright (C) 2010-2023 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.schema.validator.processor;

import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.schema.validator.UpgradeObjectProcessor;
import com.evolveum.midpoint.schema.validator.UpgradePhase;
import com.evolveum.midpoint.schema.validator.UpgradePriority;
import com.evolveum.midpoint.schema.validator.UpgradeType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AssignmentHolderType;

public class ApprovalWorkItemsProcessor implements UpgradeObjectProcessor<AssignmentHolderType> {

    @Override
    public String getIdentifier() {
        return "ApprovalWorkItems";
    }

    @Override
    public UpgradePhase getPhase() {
        return UpgradePhase.AFTER;
    }

    @Override
    public UpgradePriority getPriority() {
        return UpgradePriority.OPTIONAL;
    }

    @Override
    public UpgradeType getType() {
        return UpgradeType.SEAMLESS;
    }

    @Override
    public boolean isApplicable(PrismObject<?> object, ItemPath path) {
        // todo implement
        return false;
    }

    @Override
    public boolean process(PrismObject<AssignmentHolderType> object, ItemPath path) {
        // todo implement
        return false;
    }
}
