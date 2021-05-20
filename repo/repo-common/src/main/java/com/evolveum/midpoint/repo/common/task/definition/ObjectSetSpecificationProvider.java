/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.repo.common.task.definition;

import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectSetType;

/**
 * Work definition that can provide object set specification.
 */
public interface ObjectSetSpecificationProvider {

    ObjectSetType getObjectSetSpecification();
}
