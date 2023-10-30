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

import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import lombok.AccessLevel;
import lombok.Setter;
import org.hibernate.SessionFactory;
import org.openmrs.GlobalProperty;
import org.openmrs.api.APIException;
import org.openmrs.module.fhir2.api.dao.FhirGlobalPropertyDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@Setter(AccessLevel.PACKAGE)
public class FhirGlobalPropertyDaoImpl implements FhirGlobalPropertyDao {
	
	@Autowired
	@Qualifier("sessionFactory")
	private SessionFactory sessionFactory;
	
	@Override
	public String getGlobalProperty(String property) throws APIException {
		GlobalProperty globalProperty = sessionFactory.getCurrentSession().get(GlobalProperty.class, property);
		return globalProperty == null ? null : globalProperty.getPropertyValue();
	}
	
	@Override
	public GlobalProperty getGlobalPropertyObject(String property) {
		EntityManager entityManager = sessionFactory.getCurrentSession();
		CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
		CriteriaQuery<GlobalProperty> criteriaQuery = criteriaBuilder.createQuery(GlobalProperty.class);
		Root<GlobalProperty> root = criteriaQuery.from(GlobalProperty.class);
		
		criteriaQuery.where(criteriaBuilder.equal(root.get("property"), property));
		return entityManager.createQuery(criteriaQuery).getResultList().stream().findFirst().orElse(null);
	}
	
	@Override
	public Map<String, String> getGlobalProperties(String... properties) {
		Map<String, String> globalPropertiesMap = new HashMap<>();
		
		EntityManager entityManager = sessionFactory.getCurrentSession();
		CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
		CriteriaQuery<GlobalProperty> criteriaQuery = criteriaBuilder.createQuery(GlobalProperty.class);
		
		criteriaQuery.where(criteriaQuery.from(GlobalProperty.class).get("property").in((Object[]) properties));
		Collection<GlobalProperty> globalProperties = entityManager.createQuery(criteriaQuery).getResultList();
		
		for (GlobalProperty property : globalProperties) {
			globalPropertiesMap.put(property.getProperty(), property.getPropertyValue());
		}
		
		return globalPropertiesMap;
	}
}
