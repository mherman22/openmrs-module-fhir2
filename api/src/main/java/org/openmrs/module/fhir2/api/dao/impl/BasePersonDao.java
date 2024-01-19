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

import static org.hl7.fhir.r4.model.Patient.SP_FAMILY;
import static org.hl7.fhir.r4.model.Patient.SP_GIVEN;
import static org.hl7.fhir.r4.model.Person.SP_ADDRESS_CITY;
import static org.hl7.fhir.r4.model.Person.SP_ADDRESS_COUNTRY;
import static org.hl7.fhir.r4.model.Person.SP_ADDRESS_POSTALCODE;
import static org.hl7.fhir.r4.model.Person.SP_ADDRESS_STATE;
import static org.hl7.fhir.r4.model.Person.SP_BIRTHDATE;
import static org.hl7.fhir.r4.model.Person.SP_NAME;

import javax.annotation.Nonnull;
import javax.persistence.criteria.From;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.Root;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import ca.uhn.fhir.rest.param.StringAndListParam;
import org.openmrs.Auditable;
import org.openmrs.OpenmrsObject;
import org.openmrs.Patient;
import org.openmrs.Person;
import org.openmrs.PersonName;
import org.openmrs.module.fhir2.FhirConstants;
import org.openmrs.module.fhir2.api.search.param.PropParam;

/**
 * Base class for Person-related DAO objects. This helps standardise the logic used to search for
 * and sort objects that represent a Person, including {@link Person}, {@link Patient}, but also
 * classes like {@link org.openmrs.Provider} and {@link org.openmrs.User}.
 *
 * @param <T> The OpenMRS object that represents a person.
 */
public abstract class BasePersonDao<T extends OpenmrsObject & Auditable> extends BaseFhirDao<T> {
	
	/**
	 * This is intended to be overridden by subclasses to provide the property that defines the Person
	 * for this object
	 *
	 * @return the property that points to the person for this object
	 */
	@SuppressWarnings("UnstableApiUsage")
	protected <V,U> From<?, ?> getPersonProperty(OpenmrsFhirCriteriaContext<V,U> criteriaContext) {
		Class<? super T> rawType = typeToken.getRawType();
		if (rawType.equals(Person.class) || rawType.equals(Patient.class)) {
			return criteriaContext.getRoot();
		}
		
		return criteriaContext.addJoin("person", "person");
	}
	
	@Override
	protected <V,U> Collection<Order> paramToProps(OpenmrsFhirCriteriaContext<V,U> criteriaContext,
	        @Nonnull SortState sortState) {
		String param = sortState.getParameter();
		
		if (param == null) {
			return null;
		}
		
		From<?, ?> person = getPersonProperty(criteriaContext);
		if (param.startsWith("address")) {
			criteriaContext.addJoin(person, "addresses", "pad");
		} else if (param.equals(SP_NAME) || param.equals(SP_GIVEN) || param.equals(SP_FAMILY)) {
			Join<?, ?> personNamesJoin = criteriaContext.addJoin(person, "names", "pn");
			
			Root<PersonName> subRoot = criteriaContext.getCriteriaQuery().subquery(Integer.class).from(PersonName.class);
			
			criteriaContext.addPredicate(criteriaContext.getCriteriaBuilder().and(
			    criteriaContext.getCriteriaBuilder().equal(criteriaContext.getRoot().get("pn").get("voided"), false),
			    criteriaContext.getCriteriaBuilder().or(
			        criteriaContext.getCriteriaBuilder().and(
			            criteriaContext.getCriteriaBuilder().equal(personNamesJoin.get("preferred"), true),
			            criteriaContext.getCriteriaBuilder().equal(personNamesJoin.get("personNameId"),
			                criteriaContext.getCriteriaQuery().subquery(Integer.class)
			                        .select(criteriaContext.getCriteriaBuilder()
			                                .min(criteriaContext.getRoot().get("pn1").get("personNameId")))
			                        .where(criteriaContext.getCriteriaBuilder().and(
			                            criteriaContext.getCriteriaBuilder().equal(subRoot.get("preferred"), true),
			                            criteriaContext.getCriteriaBuilder()
			                                    .equal(subRoot.get("person_id"), criteriaContext.getRoot()
			                                            .get("person_id")))))),
			        criteriaContext.getCriteriaBuilder().and(
			            criteriaContext.getCriteriaBuilder().not(criteriaContext.getCriteriaBuilder().exists(criteriaContext
			                    .getCriteriaQuery().subquery(Integer.class)
			                    .select(criteriaContext.getRoot().get("pn2").get("personNameId"))
			                    .where(criteriaContext.getCriteriaBuilder().and(
			                        criteriaContext.getCriteriaBuilder().equal(subRoot.get("pn2").get("preferred"), true),
			                        criteriaContext.getCriteriaBuilder().equal(
			                            subRoot.get("pn2").get("person").get("personId"),
			                            criteriaContext.getRoot().get("personId")))))),
			            criteriaContext.getCriteriaBuilder().equal(criteriaContext.getRoot().get("pn").get("personNameId"),
			                criteriaContext.getCriteriaQuery().subquery(Integer.class)
			                        .select(criteriaContext.getCriteriaBuilder()
			                                .min(criteriaContext.getRoot().get("pn3").get("personNameId")))
			                        .where(criteriaContext.getCriteriaBuilder().and(
			                            criteriaContext.getCriteriaBuilder().equal(subRoot.get("pn3").get("preferred"),
			                                false),
			                            criteriaContext.getCriteriaBuilder().equal(
			                                subRoot.get("pn3").get("person").get("personId"),
			                                criteriaContext.getRoot().get("personId")))))),
			        criteriaContext.getCriteriaBuilder().isNull(criteriaContext.getRoot().get("pn").get("personNameId")))));
			criteriaContext.finalizeQuery();
			
			String[] properties = null;
			switch (param) {
				case SP_NAME:
					properties = new String[] { "pn.familyName", "pn.familyName2", "pn.givenName", "pn.middleName",
					        "pn.familyNamePrefix", "pn.familyNameSuffix" };
					break;
				case SP_GIVEN:
					properties = new String[] { "pn.givenName" };
					break;
				case SP_FAMILY:
					properties = new String[] { "pn.familyName" };
					break;
			}
			
			List<Order> sortStateOrders = new ArrayList<>();
			switch (sortState.getSortOrder()) {
				case ASC:
					for (String property : properties) {
						sortStateOrders
						        .add(criteriaContext.getCriteriaBuilder().asc(criteriaContext.getRoot().get(property)));
					}
					break;
				case DESC:
					for (String property : properties) {
						sortStateOrders
						        .add(criteriaContext.getCriteriaBuilder().desc(criteriaContext.getRoot().get(property)));
					}
					break;
			}
			
			criteriaContext.getCriteriaQuery().orderBy(sortStateOrders);
			return sortStateOrders;
			
		}
		return super.paramToProps(criteriaContext, sortState);
	}
	
	@Override
	protected <V,U> String paramToProp(OpenmrsFhirCriteriaContext<V,U> criteriaContext, @Nonnull String param) {
		switch (param) {
			case SP_BIRTHDATE:
				return "birthdate";
			case SP_ADDRESS_CITY:
				return "pad.cityVillage";
			case SP_ADDRESS_STATE:
				return "pad.stateProvince";
			case SP_ADDRESS_POSTALCODE:
				return "pad.postalCode";
			case SP_ADDRESS_COUNTRY:
				return "pad.country";
			default:
				return null;
		}
	}
	
	protected <U> void handleAddresses(OpenmrsFhirCriteriaContext<T,U> criteriaContext,
	        Map.Entry<String, List<PropParam<?>>> entry) {
		StringAndListParam city = null;
		StringAndListParam country = null;
		StringAndListParam postalCode = null;
		StringAndListParam state = null;
		for (PropParam<?> param : entry.getValue()) {
			switch (param.getPropertyName()) {
				case FhirConstants.CITY_PROPERTY:
					city = ((StringAndListParam) param.getParam());
					break;
				case FhirConstants.STATE_PROPERTY:
					state = ((StringAndListParam) param.getParam());
					break;
				case FhirConstants.POSTAL_CODE_PROPERTY:
					postalCode = ((StringAndListParam) param.getParam());
					break;
				case FhirConstants.COUNTRY_PROPERTY:
					country = ((StringAndListParam) param.getParam());
					break;
			}
		}
		
		From<?, ?> person = getPersonProperty(criteriaContext);
		criteriaContext.addJoin(person, "addresses", "pad");
		handlePersonAddress(criteriaContext, "pad", city, state, postalCode, country).ifPresent(c -> {
			criteriaContext.addPredicate(c);
			criteriaContext.finalizeQuery();
		});
	}
	
	protected <U> void handleNames(OpenmrsFhirCriteriaContext<T,U> criteriaContext, List<PropParam<?>> params) {
		StringAndListParam name = null;
		StringAndListParam given = null;
		StringAndListParam family = null;
		
		for (PropParam<?> param : params) {
			switch (param.getPropertyName()) {
				case FhirConstants.NAME_PROPERTY:
					name = (StringAndListParam) param.getParam();
					break;
				case FhirConstants.GIVEN_PROPERTY:
					given = (StringAndListParam) param.getParam();
					break;
				case FhirConstants.FAMILY_PROPERTY:
					family = (StringAndListParam) param.getParam();
					break;
			}
		}
		
		handleNames(criteriaContext, name, given, family, getPersonProperty(criteriaContext));
	}
}
