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

import static org.hl7.fhir.r4.model.Patient.SP_DEATH_DATE;

import javax.annotation.Nonnull;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.rest.param.StringAndListParam;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenAndListParam;
import lombok.AccessLevel;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifierType;
import org.openmrs.Person;
import org.openmrs.module.fhir2.FhirConstants;
import org.openmrs.module.fhir2.api.dao.FhirPatientDao;
import org.openmrs.module.fhir2.api.search.param.SearchParameterMap;
import org.springframework.stereotype.Component;

@Component
@Setter(AccessLevel.PACKAGE)
public class FhirPatientDaoImpl extends BasePersonDao<Patient> implements FhirPatientDao {
	
	@Override
	public Patient getPatientById(@Nonnull Integer id) {
		EntityManager em = sessionFactory.getCurrentSession();
		CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
		CriteriaQuery<Patient> criteriaQuery = criteriaBuilder.createQuery(Patient.class);
		Root<Patient> root = criteriaQuery.from(Patient.class);
		
		criteriaQuery.select(root).where(criteriaBuilder.equal(root.get("patientId"), id));
		
		TypedQuery<Patient> query = em.createQuery(criteriaQuery);
		return query.getResultList().stream().findFirst().orElse(null);
	}
	
	@Override
	public List<Patient> getPatientsByIds(@Nonnull Collection<Integer> ids) {
		EntityManager em = sessionFactory.getCurrentSession();
		CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
		CriteriaQuery<Patient> criteriaQuery = criteriaBuilder.createQuery(Patient.class);
		Root<Patient> root = criteriaQuery.from(Patient.class);
		
		criteriaQuery.select(root);
		criteriaQuery.where(root.get("id").in(ids));
		return em.createQuery(criteriaQuery).getResultList();
	}
	
	@Override
	public PatientIdentifierType getPatientIdentifierTypeByNameOrUuid(String name, String uuid) {
		EntityManager em = sessionFactory.getCurrentSession();
		CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
		CriteriaQuery<PatientIdentifierType> cq = criteriaBuilder.createQuery(PatientIdentifierType.class);
		Root<PatientIdentifierType> rt = cq.from(PatientIdentifierType.class);
		
		cq.select(rt).where(criteriaBuilder.or(criteriaBuilder.and(criteriaBuilder.equal(rt.get("name"), name),
		    criteriaBuilder.equal(rt.get("retired"), false)), criteriaBuilder.equal(rt.get("uuid"), uuid)));
		List<PatientIdentifierType> identifierTypes = em.createQuery(cq).getResultList();
		
		if (identifierTypes.isEmpty()) {
			return null;
		} else {
			// favour uuid if one was supplied
			if (uuid != null) {
				try {
					return identifierTypes.stream().filter((idType) -> uuid.equals(idType.getUuid())).findFirst()
					        .orElse(identifierTypes.get(0));
				}
				catch (NoSuchElementException ignored) {}
			}
			
			return identifierTypes.get(0);
		}
	}
	
	@Override
	protected void setupSearchParams(CriteriaBuilder criteriaBuilder, SearchParameterMap theParams) {
		EntityManager em = sessionFactory.getCurrentSession();
		criteriaBuilder = em.getCriteriaBuilder();
		CriteriaQuery<Patient> criteriaQuery = criteriaBuilder.createQuery(Patient.class);
		Root<Patient> root = criteriaQuery.from(Patient.class);
		
		List<Predicate> predicates = new ArrayList<>();
		CriteriaBuilder finalCriteriaBuilder = criteriaBuilder;
		theParams.getParameters().forEach(entry -> {
			switch (entry.getKey()) {
				case FhirConstants.QUERY_SEARCH_HANDLER:
					entry.getValue()
					        .forEach(query -> handlePatientQuery(finalCriteriaBuilder, (StringAndListParam) query.getParam()));
					break;
				case FhirConstants.NAME_SEARCH_HANDLER:
					handleNames(finalCriteriaBuilder, entry.getValue());
					break;
				case FhirConstants.GENDER_SEARCH_HANDLER:
					entry.getValue().forEach(
					    p -> handleGender(p.getPropertyName(), (TokenAndListParam) p.getParam()).ifPresent(predicates::add));
					criteriaQuery.distinct(true).where(predicates.toArray(new Predicate[] {}));
					break;
				case FhirConstants.IDENTIFIER_SEARCH_HANDLER:
					entry.getValue().forEach(
					    identifier -> handleIdentifier(finalCriteriaBuilder, (TokenAndListParam) identifier.getParam()));
					break;
				case FhirConstants.DATE_RANGE_SEARCH_HANDLER:
					entry.getValue().forEach(dateRangeParam -> handleDateRange(dateRangeParam.getPropertyName(),
					    (DateRangeParam) dateRangeParam.getParam()).ifPresent(predicates::add));
					criteriaQuery.distinct(true).where(predicates.toArray(new Predicate[] {}));
					break;
				case FhirConstants.BOOLEAN_SEARCH_HANDLER:
					entry.getValue().forEach(b -> handleBoolean(b.getPropertyName(), (TokenAndListParam) b.getParam())
					        .ifPresent(predicates::add));
					criteriaQuery.distinct(true).where(predicates.toArray(new Predicate[] {}));
					break;
				case FhirConstants.ADDRESS_SEARCH_HANDLER:
					handleAddresses(finalCriteriaBuilder, entry);
					break;
				case FhirConstants.COMMON_SEARCH_HANDLER:
					handleCommonSearchParameters(entry.getValue()).ifPresent(predicates::add);
					criteriaQuery.distinct(true).where(predicates.toArray(new Predicate[] {}));
					break;
			}
		});
	}
	
	private void handlePatientQuery(CriteriaBuilder criteriaBuilder, @Nonnull StringAndListParam query) {
		EntityManager em = sessionFactory.getCurrentSession();
		criteriaBuilder = em.getCriteriaBuilder();
		CriteriaQuery<Patient> criteriaQuery = criteriaBuilder.createQuery(Patient.class);
		Root<Patient> root = criteriaQuery.from(Patient.class);
		
		List<Predicate> predicates = new ArrayList<>();
		if (query == null) {
			return;
		}
		
		if (lacksAlias(criteriaBuilder, "pn")) {
			root.join("names");
		}
		
		if (lacksAlias(criteriaBuilder, "pi")) {
			root.join("identifiers");
		}
		
		CriteriaBuilder finalCriteriaBuilder = criteriaBuilder;
		handleAndListParam(query, q -> {
			List<Optional<? extends Predicate>> arrayList = new ArrayList<>();
			
			for (String token : StringUtils.split(q.getValueNotNull(), " \t,")) {
				StringParam param = new StringParam(token).setContains(q.isContains()).setExact(q.isExact());
				arrayList.add(propertyLike("pn.givenName", param)
				        .map(c -> finalCriteriaBuilder.and(c, finalCriteriaBuilder.equal(root.get("pn.voided"), false))));
				arrayList.add(propertyLike("pn.middleName", param)
				        .map(c -> finalCriteriaBuilder.and(c, finalCriteriaBuilder.equal(root.get("pn.voided"), false))));
				arrayList.add(propertyLike("pn.familyName", param)
				        .map(c -> finalCriteriaBuilder.and(c, finalCriteriaBuilder.equal(root.get("pn.voided"), false))));
			}
			
			arrayList.add(propertyLike("pi.identifier",
			    new StringParam(q.getValueNotNull()).setContains(q.isContains()).setExact(q.isExact()))
			            .map(c -> finalCriteriaBuilder.and(c, finalCriteriaBuilder.equal(root.get("pi.voided"), false))));
			
			return Optional.of(finalCriteriaBuilder.or(toCriteriaArray(arrayList)));
		}).ifPresent(predicates::add);
		criteriaQuery.distinct(true).where(predicates.toArray(new Predicate[] {}));
	}
	
	protected void handleIdentifier(CriteriaBuilder criteriaBuilder, TokenAndListParam identifier) {
		EntityManager em = sessionFactory.getCurrentSession();
		criteriaBuilder = em.getCriteriaBuilder();
		CriteriaQuery<Patient> criteriaQuery = criteriaBuilder.createQuery(Patient.class);
		Root<Patient> root = criteriaQuery.from(Patient.class);
		
		List<Predicate> predicates = new ArrayList<>();
		if (identifier == null) {
			return;
		}
		
		root.join("identifiers", javax.persistence.criteria.JoinType.INNER).alias("pi");
		criteriaBuilder.equal(root.get("pi.voided"), false);
		
		CriteriaBuilder finalCriteriaBuilder = criteriaBuilder;
		handleAndListParamBySystem(identifier, (system, tokens) -> {
			if (system.isEmpty()) {
				return Optional.of(finalCriteriaBuilder.in(root.get("pi.identifier")).value(tokensToList(tokens)));
			} else {
				if (lacksAlias(finalCriteriaBuilder, "pit")) {
					root.join("pi.identifierType", javax.persistence.criteria.JoinType.INNER).alias("pit");
					finalCriteriaBuilder.equal(root.get("pit.retired"), false);
				}
				
				return Optional.of(finalCriteriaBuilder.and(finalCriteriaBuilder.equal(root.get("pit.name"), system),
				    finalCriteriaBuilder.in(root.get("pi.identifier")).value(tokensToList(tokens))));
			}
		}).ifPresent(predicates::add);
		criteriaQuery.distinct(true).where(predicates.toArray(new Predicate[] {}));
	}
	
	@Override
	protected String getSqlAlias() {
		return "this_1_";
	}
	
	@Override
	protected String paramToProp(@Nonnull String param) {
		if (SP_DEATH_DATE.equalsIgnoreCase(param)) {
			return "deathDate";
		}
		
		return super.paramToProp(param);
	}
	
	@Override
	public boolean hasDistinctResults() {
		return false;
	}
}
