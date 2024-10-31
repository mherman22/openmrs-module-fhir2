/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.fhir2.api.dao.impl;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@RequiredArgsConstructor
public class OpenmrsFhirCriteriaSubquery<V, U> {
	
	@Getter
	@NonNull
	private final CriteriaBuilder criteriaBuilder;
	
	@Getter
	@Setter
	Expression<U> projection = null;
	
	@Getter
	@NonNull
	Subquery<U> subquery;
	
	@Getter
	@NonNull
	Root<V> root;
	
	private final List<Predicate> predicates = new ArrayList<>();
	
	public OpenmrsFhirCriteriaSubquery<V, U> addPredicate(Predicate predicate) {
		predicates.add(predicate);
		return this;
	}
	
	public Subquery<U> finalizeQuery() {
		if (projection != null) {
			subquery = subquery.select(projection);
		}
		
		return subquery.where(predicates.toArray(new Predicate[0]));
	}
}
