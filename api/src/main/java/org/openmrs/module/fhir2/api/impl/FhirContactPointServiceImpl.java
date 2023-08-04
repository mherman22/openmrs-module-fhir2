/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.fhir2.api.impl;

import lombok.AccessLevel;
import lombok.Setter;
import org.openmrs.LocationAttributeType;
import org.openmrs.PersonAttributeType;
import org.openmrs.ProviderAttributeType;
import org.openmrs.module.fhir2.api.FhirContactPointService;
import org.openmrs.module.fhir2.api.dao.FhirContactPointMapDao;
import org.openmrs.module.fhir2.model.FhirContactPointMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Nonnull;

@Component
@Transactional
@Setter(AccessLevel.PACKAGE)
public class FhirContactPointServiceImpl implements FhirContactPointService {
	
	@Autowired
	private FhirContactPointMapDao dao;
	
	@Override
	public FhirContactPointMap getFhirContactPointMapForPersonAttributeType(PersonAttributeType attributeType) {
		return dao.getFhirContactPointMapForPersonAttributeType(attributeType);
	}
	
	@Override
	public FhirContactPointMap getFhirContactPointMapForLocationAttributeType(LocationAttributeType attributeType) {
		return dao.getFhirContactPointMapForLocationAttributeType(attributeType);
	}
	
	@Override
	public FhirContactPointMap getFhirContactPointMapForProviderAttributeType(ProviderAttributeType attributeType) {
		return dao.getFhirContactPointMapForProviderAttributeType(attributeType);
	}
	
	@Override
	public PersonAttributeType getPersonAttributeTypeByUuid(@Nonnull String uuid) {
		return dao.getPersonAttributeTypeByUuid(uuid);
	}
}
