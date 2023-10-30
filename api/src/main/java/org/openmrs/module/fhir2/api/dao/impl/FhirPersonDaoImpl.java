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

import static org.hibernate.criterion.Restrictions.eq;

import javax.annotation.Nonnull;
import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.rest.param.TokenAndListParam;
import lombok.AccessLevel;
import lombok.Setter;
import org.hibernate.sql.JoinType;
import org.openmrs.Person;
import org.openmrs.PersonAttribute;
import org.openmrs.Provider;
import org.openmrs.module.fhir2.FhirConstants;
import org.openmrs.module.fhir2.api.dao.FhirPersonDao;
import org.openmrs.module.fhir2.api.search.param.SearchParameterMap;
import org.springframework.stereotype.Component;

@Component
@Setter(AccessLevel.PACKAGE)
public class FhirPersonDaoImpl extends BasePersonDao<Person> implements FhirPersonDao {
	
	private List<Predicate> predicateList = new ArrayList<>();
	
	@Override
	@SuppressWarnings("unchecked")
	public List<PersonAttribute> getActiveAttributesByPersonAndAttributeTypeUuid(@Nonnull Person person,
	        @Nonnull String personAttributeTypeUuid) {
		return (List<PersonAttribute>) getSessionFactory().getCurrentSession().createCriteria(PersonAttribute.class)
		        .createAlias("person", "p", JoinType.INNER_JOIN, eq("p.id", person.getId()))
		        .createAlias("attributeType", "pat").add(eq("pat.uuid", personAttributeTypeUuid)).add(eq("voided", false))
		        .list();
	}
	
	@Override
	protected void setupSearchParams(CriteriaBuilder criteriaBuilder, SearchParameterMap theParams) {
		EntityManager em = sessionFactory.getCurrentSession();
		criteriaBuilder = em.getCriteriaBuilder();
		CriteriaQuery<Person> criteriaQuery = criteriaBuilder.createQuery(Person.class);
		
		CriteriaBuilder finalCriteriaBuilder = criteriaBuilder;
		theParams.getParameters().forEach(entry -> {
			switch (entry.getKey()) {
				case FhirConstants.NAME_SEARCH_HANDLER:
					entry.getValue().forEach(param -> handleNames(finalCriteriaBuilder, entry.getValue()));
					break;
				case FhirConstants.GENDER_SEARCH_HANDLER:
					entry.getValue().forEach(
					    param -> handleGender("gender", (TokenAndListParam) param.getParam()).ifPresent(predicateList::add));
					criteriaQuery.distinct(true).where(predicateList.toArray(new Predicate[] {}));
					break;
				case FhirConstants.DATE_RANGE_SEARCH_HANDLER:
					entry.getValue().forEach(param -> handleDateRange("birthdate", (DateRangeParam) param.getParam())
					        .ifPresent(predicateList::add));
					criteriaQuery.distinct(true).where(predicateList.toArray(new Predicate[] {}));
					break;
				case FhirConstants.ADDRESS_SEARCH_HANDLER:
					handleAddresses(finalCriteriaBuilder, entry);
					break;
				case FhirConstants.COMMON_SEARCH_HANDLER:
					handleCommonSearchParameters(entry.getValue()).ifPresent(predicateList::add);
					criteriaQuery.distinct(true).where(predicateList.toArray(new Predicate[] {}));
					break;
			}
		});
	}
	
	@Override
	protected Optional<Predicate> handleLastUpdated(DateRangeParam param) {
		EntityManager em = sessionFactory.getCurrentSession();
		CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
		CriteriaQuery<Person> criteriaQuery = criteriaBuilder.createQuery(Person.class);
		Root<Person> root = criteriaQuery.from(Person.class);
		
		return Optional.of(criteriaBuilder.or(toCriteriaArray(handleDateRange("personDateChanged", param),
		    Optional.of(criteriaBuilder
		            .and(toCriteriaArray(Stream.of(Optional.of(criteriaBuilder.isNull(root.get("personDateChanged"))),
		                handleDateRange("personDateCreated", param))))))));
	}
	
	@Override
	protected String getSqlAlias() {
		return "this_";
	}
	
	@Override
	protected void handleVoidable(CriteriaBuilder criteriaBuilder) {
		EntityManager em = sessionFactory.getCurrentSession();
		criteriaBuilder = em.getCriteriaBuilder();
		CriteriaQuery<Person> criteriaQuery = criteriaBuilder.createQuery(Person.class);
		Root<Person> root = criteriaQuery.from(Person.class);
		
		criteriaBuilder.and(criteriaBuilder.equal(root.get("personVoided"), false));
	}
	
}
