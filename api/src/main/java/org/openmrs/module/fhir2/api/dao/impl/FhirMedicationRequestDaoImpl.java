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

import javax.annotation.Nonnull;
import javax.persistence.criteria.Predicate;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import ca.uhn.fhir.rest.param.ReferenceAndListParam;
import ca.uhn.fhir.rest.param.TokenAndListParam;
import lombok.AccessLevel;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.openmrs.DrugOrder;
import org.openmrs.Order;
import org.openmrs.module.fhir2.FhirConstants;
import org.openmrs.module.fhir2.api.dao.FhirMedicationRequestDao;
import org.openmrs.module.fhir2.api.search.param.SearchParameterMap;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Setter(AccessLevel.PACKAGE)
public class FhirMedicationRequestDaoImpl extends BaseFhirDao<DrugOrder> implements FhirMedicationRequestDao {
	
	@Override
	@Transactional(readOnly = true)
	public DrugOrder get(@Nonnull String uuid) {
		DrugOrder result = super.get(uuid);
		return result != null && result.getAction() != null && result.getAction() != Order.Action.DISCONTINUE ? result
		        : null;
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<DrugOrder> get(@Nonnull Collection<String> uuids) {
		List<DrugOrder> results = super.get(uuids);
		if (results == null) {
			return results;
		} else {
			return results.stream()
			        .filter(order -> order.getAction() == null || order.getAction() != Order.Action.DISCONTINUE)
			        .collect(Collectors.toList());
		}
	}
	
	@Override
	protected void setupSearchParams(OpenmrsFhirCriteriaContext<DrugOrder> criteriaContext, SearchParameterMap theParams) {
		theParams.getParameters().forEach(entry -> {
			switch (entry.getKey()) {
				case FhirConstants.FULFILLER_STATUS_SEARCH_HANDLER:
					entry.getValue()
					        .forEach(param -> handleFulfillerStatus(criteriaContext, (TokenAndListParam) param.getParam())
					                .ifPresent(criteriaContext::addPredicate));
					criteriaContext.finalizeQuery();
					break;
				case FhirConstants.ENCOUNTER_REFERENCE_SEARCH_HANDLER:
					entry.getValue().forEach(
					    e -> handleEncounterReference(criteriaContext, (ReferenceAndListParam) e.getParam(), "e"));
					break;
				case FhirConstants.PATIENT_REFERENCE_SEARCH_HANDLER:
					entry.getValue().forEach(patientReference -> handlePatientReference(criteriaContext,
					    (ReferenceAndListParam) patientReference.getParam(), "patient"));
					break;
				case FhirConstants.CODED_SEARCH_HANDLER:
					entry.getValue()
					        .forEach(code -> handleCodedConcept(criteriaContext, (TokenAndListParam) code.getParam()));
					break;
				case FhirConstants.PARTICIPANT_REFERENCE_SEARCH_HANDLER:
					entry.getValue().forEach(participantReference -> handleProviderReference(criteriaContext,
					    (ReferenceAndListParam) participantReference.getParam()));
					break;
				case FhirConstants.MEDICATION_REFERENCE_SEARCH_HANDLER:
					entry.getValue().forEach(
					    d -> handleMedicationReference(criteriaContext, "d", (ReferenceAndListParam) d.getParam())
					            .ifPresent(c -> {
						            criteriaContext.getRoot().join("drug").alias("d");
						            criteriaContext.addPredicate(c);
						            criteriaContext.finalizeQuery();
					            }));
					break;
				case FhirConstants.STATUS_SEARCH_HANDLER:
					entry.getValue().forEach(param -> handleStatus(criteriaContext, (TokenAndListParam) param.getParam())
					        .ifPresent(criteriaContext::addPredicate));
					criteriaContext.finalizeQuery();
				case FhirConstants.COMMON_SEARCH_HANDLER:
					handleCommonSearchParameters(criteriaContext, entry.getValue()).ifPresent(criteriaContext::addPredicate);
					criteriaContext.finalizeQuery();
					break;
			}
		});
		
		excludeDiscontinueOrders(criteriaContext);
	}
	
	private <T> Optional<Predicate> handleStatus(OpenmrsFhirCriteriaContext<T> criteriaContext,
	        TokenAndListParam tokenAndListParam) {
		return handleAndListParam(tokenAndListParam, token -> {
			if (token.getValue() != null) {
				try {
					// currently only handles "ACTIVE"
					if (MedicationRequest.MedicationRequestStatus.ACTIVE.toString().equals(token.getValue().toUpperCase())) {
						return Optional.of(generateActiveOrderQuery(criteriaContext));
					}
				}
				catch (IllegalArgumentException e) {
					return Optional.empty();
				}
			}
			
			return Optional.empty();
		});
	}
	
	private <T> Optional<Predicate> handleFulfillerStatus(OpenmrsFhirCriteriaContext<T> criteriaContext,
	        TokenAndListParam tokenAndListParam) {
		return handleAndListParam(tokenAndListParam, token -> {
			if (token.getValue() != null) {
				return Optional.of(generateFulfillerStatusRestriction(criteriaContext,
				    Order.FulfillerStatus.valueOf(token.getValue().toUpperCase())));
			}
			return Optional.empty();
		});
	}
	
	protected <T> Predicate generateFulfillerStatusRestriction(OpenmrsFhirCriteriaContext<T> criteriaContext,
	        Order.FulfillerStatus fulfillerStatus) {
		return generateFulfillerStatusRestriction(criteriaContext, "", fulfillerStatus);
	}
	
	protected <T> Predicate generateFulfillerStatusRestriction(OpenmrsFhirCriteriaContext<T> criteriaContext, String path,
	        Order.FulfillerStatus fulfillerStatus) {
		if (StringUtils.isNotBlank(path)) {
			path = path + ".";
		}
		
		return criteriaContext.getCriteriaBuilder().equal(criteriaContext.getRoot().get(path + "fulfillerStatus"),
		    fulfillerStatus);
	}
	
	private void handleCodedConcept(OpenmrsFhirCriteriaContext<DrugOrder> criteriaContext, TokenAndListParam code) {
		if (code != null) {
			if (lacksAlias(criteriaContext, "c")) {
				criteriaContext.getRoot().join("concept").alias("c");
			}
			
			handleCodeableConcept(criteriaContext, code, "c", "cm", "crt").ifPresent(criteriaContext::addPredicate);
			criteriaContext.finalizeQuery();
		}
	}
	
	private void excludeDiscontinueOrders(OpenmrsFhirCriteriaContext<DrugOrder> criteriaContext) {
		// exclude "discontinue" orders, see: https://issues.openmrs.org/browse/FM2-532
		criteriaContext.getCriteriaBuilder().and(criteriaContext.getCriteriaBuilder()
		        .notEqual(criteriaContext.getRoot().get("action"), Order.Action.DISCONTINUE));
	}
}
