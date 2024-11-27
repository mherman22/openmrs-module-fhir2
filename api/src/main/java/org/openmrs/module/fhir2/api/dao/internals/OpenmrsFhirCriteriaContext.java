/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.fhir2.api.dao.internals;

import javax.annotation.Nonnull;
import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import lombok.Getter;
import lombok.NonNull;

/**
 * {@code OpenmrsFhirCriteriaContext} is a holder object for building criteria queries in the FHIR2
 * DAO API. It is provided as a convenience since the old Hibernate Criteria API allowed us to
 * simply pass a Criteria object around, but the JPA2 Criteria API requires us to pass several
 * different classes around. <br/>
 * <br/>
 * Criteria queries are built up mostly by calling methods on this class to add various joins,
 * predicates, and sort orders which are built into a {@link CriteriaQuery<U>} by calling
 * {@link #finalizeQuery()}. {@link #finalizeQuery()} should only be called once the full query has
 * been built and only just before running the query if possible. <br/>
 * <br/>
 * The type {@code T} indicates the type of object that is the "root" of the query. For most
 * queries, the type {@code U}, which is the expected type of the result, will be the same as
 * {@code T}; however, for some queries, like those that count results, {@code U} will have a
 * different type.
 *
 * @param <T> The root type for the query
 * @param <U> The type for the result of the query
 */
public class OpenmrsFhirCriteriaContext<T, U> extends BaseFhirCriteriaHolder<T, U> {
	
	@Getter
	@NonNull
	private final EntityManager entityManager;
	
	@Getter
	@NonNull
	private final CriteriaQuery<U> criteriaQuery;
	
	private final List<Order> orders = new ArrayList<>();
	
	@Getter
	private final List<T> results = new ArrayList<>();
	
	public OpenmrsFhirCriteriaContext(@Nonnull EntityManager entityManager, @NonNull CriteriaBuilder criteriaBuilder,
	    @Nonnull CriteriaQuery<U> criteriaQuery, @NonNull Root<T> root) {
		super(criteriaBuilder, root);
		this.entityManager = entityManager;
		this.criteriaQuery = criteriaQuery;
	}
	
	public <V> OpenmrsFhirCriteriaSubquery<V, Integer> addSubquery(Class<V> fromType) {
		return addSubquery(fromType, Integer.class);
	}
	
	public <V, U> OpenmrsFhirCriteriaSubquery<V, U> addSubquery(Class<V> fromType, Class<U> resultType) {
		Subquery<U> subquery = criteriaQuery.subquery(resultType);
		return new OpenmrsFhirCriteriaSubquery<>(getCriteriaBuilder(), subquery, subquery.from(fromType));
	}
	
	@Override
	public OpenmrsFhirCriteriaContext<T, U> addPredicate(Predicate predicate) {
		return (OpenmrsFhirCriteriaContext<T, U>) super.addPredicate(predicate);
	}
	
	public OpenmrsFhirCriteriaContext<T, U> addOrder(Order order) {
		orders.add(order);
		return this;
	}
	
	public OpenmrsFhirCriteriaContext<T, U> addResults(T result) {
		results.add(result);
		return this;
	}
	
	public CriteriaQuery<U> finalizeQuery() {
		return criteriaQuery.where(getPredicates().toArray(new Predicate[0])).orderBy(orders);
	}
	
	public CriteriaQuery<U> finalizeIdQuery(String idProperty) {
		return criteriaQuery.select(getRoot().get(idProperty)).where(getPredicates().toArray(new Predicate[0]))
		        .distinct(true);
	}
	
	@SuppressWarnings("unchecked")
	public CriteriaQuery<U> finalizeWrapperQuery(String idProperty, Collection<Integer> ids) {
		// the unchecked cast here from Root<T> to Path<U> relies on Hibernate implementation details and may not
		// be safe long-term, but I can't figure out how to turn a Root into a Path
		return criteriaQuery.select((Path<U>) getRoot()).where(getRoot().get(idProperty).in(ids)).orderBy(orders);
	}
}
