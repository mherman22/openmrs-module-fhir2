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

import static org.openmrs.module.fhir2.FhirConstants.ENCOUNTER_TYPE_REFERENCE_SEARCH_HANDLER;

import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Predicate;

import java.util.HashSet;
import java.util.Set;

import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.rest.param.HasAndListParam;
import ca.uhn.fhir.rest.param.HasParam;
import ca.uhn.fhir.rest.param.ReferenceAndListParam;
import ca.uhn.fhir.rest.param.TokenAndListParam;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.openmrs.Auditable;
import org.openmrs.DrugOrder;
import org.openmrs.Encounter;
import org.openmrs.OpenmrsObject;
import org.openmrs.Order;
import org.openmrs.module.fhir2.FhirConstants;
import org.openmrs.module.fhir2.api.search.param.SearchParameterMap;

@Slf4j
public abstract class BaseEncounterDao<T extends OpenmrsObject & Auditable> extends BaseFhirDao<T> {
	
	@Override
	protected void setupSearchParams(OpenmrsFhirCriteriaContext<T> criteriaContext, SearchParameterMap theParams) {
		theParams.getParameters().forEach(entry -> {
			switch (entry.getKey()) {
				case FhirConstants.DATE_RANGE_SEARCH_HANDLER:
					entry.getValue().forEach(param -> handleDate(criteriaContext, (DateRangeParam) param.getParam()));
					break;
				case FhirConstants.LOCATION_REFERENCE_SEARCH_HANDLER:
					entry.getValue().forEach(param -> {
						handleLocationReference(criteriaContext, "l", (ReferenceAndListParam) param.getParam())
						        .ifPresent(l -> {
							        criteriaContext.getRoot().join("location", JoinType.INNER).alias("l");
							        criteriaContext.addPredicate(l);
							        criteriaContext.finalizeQuery();
						        });
					});
					break;
				case FhirConstants.PARTICIPANT_REFERENCE_SEARCH_HANDLER:
					entry.getValue()
					        .forEach(param -> handleParticipant(criteriaContext, (ReferenceAndListParam) param.getParam()));
					break;
				case FhirConstants.PATIENT_REFERENCE_SEARCH_HANDLER:
					entry.getValue().forEach(
					    param -> handlePatientReference(criteriaContext, (ReferenceAndListParam) param.getParam()));
					break;
				case ENCOUNTER_TYPE_REFERENCE_SEARCH_HANDLER:
					entry.getValue()
					        .forEach(param -> handleEncounterType(criteriaContext, (TokenAndListParam) param.getParam()));
					break;
				case FhirConstants.COMMON_SEARCH_HANDLER:
					handleCommonSearchParameters(criteriaContext, entry.getValue()).ifPresent(criteriaContext::addPredicate);
					criteriaContext.finalizeQuery();
					break;
				case FhirConstants.HAS_SEARCH_HANDLER:
					entry.getValue()
					        .forEach(param -> handleHasAndListParam(criteriaContext, (HasAndListParam) param.getParam()));
					break;
			}
		});
	}
	
	/**
	 * Handle _has parameters that are passed in to constrain the Encounter resource on properties of
	 * dependent resources
	 */
	@SuppressWarnings("UnstableApiUsage")
	protected void handleHasAndListParam(OpenmrsFhirCriteriaContext<T> criteriaContext, HasAndListParam hasAndListParam) {
		if (hasAndListParam != null) {
			log.debug("Handling hasAndListParam");
			hasAndListParam.getValuesAsQueryTokens().forEach(hasOrListParam -> {
				if (!hasOrListParam.getValuesAsQueryTokens().isEmpty()) {
					
					log.debug("Handling hasOrListParam");
					// Making the assumption that any "orListParams" match everything except for the value
					HasParam hasParam = hasOrListParam.getValuesAsQueryTokens().get(0);
					Set<String> values = new HashSet<>();
					hasOrListParam.getValuesAsQueryTokens().forEach(orParam -> values.add(orParam.getParameterValue()));
					
					log.debug("Handling hasParam = " + hasParam.getQueryParameterQualifier());
					log.debug("With value in " + values);
					
					boolean handled = false;
					
					// Support constraining encounter resources to those that contain only certain Medication Requests
					if (FhirConstants.MEDICATION_REQUEST.equals(hasParam.getTargetResourceType())) {
						if (MedicationRequest.SP_ENCOUNTER.equals(hasParam.getReferenceFieldName())) {
							if (lacksAlias(criteriaContext, "orders")) {
								if (Encounter.class.isAssignableFrom(typeToken.getRawType())) {
									criteriaContext.addJoin("orders", "orders");
								} else {
									if (lacksAlias(criteriaContext, "en")) {
										criteriaContext.addJoin("encounters", "en");
									}
									criteriaContext.addJoin("en.orders", "orders");
								}
							}
							// Constrain only on non-voided Drug Orders
							criteriaContext.addPredicate(criteriaContext.getCriteriaBuilder()
							        .equal(criteriaContext.getRoot().get("orders.class"), DrugOrder.class));
							// TODO Do these criteria still work?
							criteriaContext.addPredicate(criteriaContext.getCriteriaBuilder()
							        .equal(criteriaContext.getRoot().get("orders.voided"), false));
							criteriaContext.addPredicate(criteriaContext.getCriteriaBuilder()
							        .notEqual(criteriaContext.getRoot().get("orders.action"), Order.Action.DISCONTINUE));
							
							String paramName = hasParam.getParameterName();
							String paramValue = hasParam.getParameterValue();
							if (MedicationRequest.SP_INTENT.equals(paramName)) {
								if (values.contains(MedicationRequest.MedicationRequestIntent.ORDER.toCode())) {
									// No additional constraints needed, all Orders are given/assumed intent=order
									handled = true;
								}
							} else if (MedicationRequest.SP_STATUS.equals(paramName)) {
								// only supports ACTIVE at this time
								if (paramValue != null) {
									if (MedicationRequest.MedicationRequestStatus.ACTIVE.toString()
									        .equalsIgnoreCase(paramValue)) {
										criteriaContext.getCriteriaBuilder()
										        .and(generateActiveOrderQuery(criteriaContext, "orders"));
									}
								}
								handled = true;
							} else if ((MedicationRequest.SP_STATUS + ":not").equalsIgnoreCase(paramName)) {
								if (paramValue != null) {
									if (MedicationRequest.MedicationRequestStatus.CANCELLED.toString()
									        .equalsIgnoreCase(paramValue)) {
										criteriaContext.getCriteriaBuilder()
										        .and(generateNotCancelledOrderQuery(criteriaContext, "orders"));
									}
									if (MedicationRequest.MedicationRequestStatus.COMPLETED.toString()
									        .equalsIgnoreCase(paramValue)) {
										Predicate notCompletedCriterion = generateNotCompletedOrderQuery("orders");
										if (notCompletedCriterion != null) {
											criteriaContext.getCriteriaBuilder().and(notCompletedCriterion);
										}
									}
								}
								handled = true;
							} else if ((FhirConstants.SP_FULFILLER_STATUS).equalsIgnoreCase(paramName)) {
								if (paramValue != null) {
									criteriaContext.getCriteriaBuilder()
									        .and(generateFulfillerStatusRestriction("orders", paramValue));
								}
								handled = true;
							} else if ((FhirConstants.SP_FULFILLER_STATUS + ":not").equalsIgnoreCase(paramName)) {
								if (paramValue != null) {
									criteriaContext.getCriteriaBuilder()
									        .and(generateNotFulfillerStatusRestriction("orders", paramValue));
								}
								handled = true;
							}
							criteriaContext.finalizeQuery();
						}
					}
					if (!handled) {
						log.warn("_has parameter not supported: " + hasParam.getQueryParameterQualifier());
					}
					
				}
			});
		}
	}
	
	protected abstract void handleDate(OpenmrsFhirCriteriaContext<T> criteriaContext, DateRangeParam dateRangeParam);
	
	protected abstract void handleEncounterType(OpenmrsFhirCriteriaContext<T> criteriaContext,
	        TokenAndListParam tokenAndListParam);
	
	protected abstract void handleParticipant(OpenmrsFhirCriteriaContext<T> criteriaContext,
	        ReferenceAndListParam referenceAndListParam);
	
	protected Predicate generateNotCompletedOrderQuery(String path) {
		// not implemented in Core until 2.2; see override in FhirEncounterDaoImpl_2_2
		return null;
	}
	
	protected Predicate generateFulfillerStatusRestriction(String path, String fulfillerStatus) {
		// not implemented in Core until 2.2; see override in FhirEncounterDaoImpl_2_2
		return null;
	}
	
	protected Predicate generateNotFulfillerStatusRestriction(String path, String fulfillerStatus) {
		// not implemented in Core until 2.2; see override in FhirEncounterDaoImpl_2_2
		return null;
	}
	
}
