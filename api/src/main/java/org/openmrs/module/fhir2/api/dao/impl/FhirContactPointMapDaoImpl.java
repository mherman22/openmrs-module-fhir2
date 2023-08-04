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

import lombok.AccessLevel;
import lombok.Setter;
import org.hibernate.SessionFactory;
import org.openmrs.LocationAttributeType;
import org.openmrs.PersonAttributeType;
import org.openmrs.ProviderAttributeType;
import org.openmrs.api.PersonService;
import org.openmrs.module.fhir2.api.dao.FhirContactPointMapDao;
import org.openmrs.module.fhir2.model.FhirContactPointMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@Setter(AccessLevel.PACKAGE)
public class FhirContactPointMapDaoImpl implements FhirContactPointMapDao {
	
	@Autowired
	@Qualifier("sessionFactory")
	private SessionFactory sessionFactory;
	
	@Autowired
	private PersonService personService;
	
	@Override
	public FhirContactPointMap getFhirContactPointMapForPersonAttributeType(PersonAttributeType attributeType) {
		return (FhirContactPointMap) sessionFactory.getCurrentSession().createQuery("from fhir_contact_point_map fcp where fcp.person_attribute_type_id = :person_attribute_type_id").setParameter("person_attribute_type_id", attributeType.getId()).uniqueResult();
	}
	
	@Override
	public FhirContactPointMap getFhirContactPointMapForLocationAttributeType(LocationAttributeType attributeType) {
		return (FhirContactPointMap) sessionFactory.getCurrentSession().createQuery("from fhir_contact_point_map fcp where fcp.location_attribute_type_id = :location_attribute_type_id").setParameter("location_attribute_type_id", attributeType.getId()).uniqueResult();
	}
	
	@Override
	public FhirContactPointMap getFhirContactPointMapForProviderAttributeType(ProviderAttributeType attributeType) {
		return (FhirContactPointMap) sessionFactory.getCurrentSession().createQuery("from fhir_contact_point_map fcp where fcp.provider_attribute_type_id = :provider_attribute_type_id").setParameter("provider_attribute_type_id", attributeType.getId()).uniqueResult();
	}
	
	@Override
	public PersonAttributeType getPersonAttributeTypeByUuid(String uuid) {
		return personService.getPersonAttributeTypeByUuid(uuid);
	}
}
