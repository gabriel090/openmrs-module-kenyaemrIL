/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 * <p>
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.kenyaemrIL.dmi.library;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.*;
import org.openmrs.api.ConceptService;
import org.openmrs.api.PatientService;
import org.openmrs.api.context.Context;
import org.openmrs.calculation.patient.PatientCalculationContext;
import org.openmrs.calculation.result.CalculationResultMap;
import org.openmrs.module.kenyacore.calculation.AbstractPatientCalculation;
import org.openmrs.module.kenyacore.calculation.BooleanResult;
import org.openmrs.module.kenyacore.calculation.Filters;
import org.openmrs.module.kenyaemr.metadata.CommonMetadata;
import org.openmrs.module.kenyaemr.metadata.HivMetadata;
import org.openmrs.module.kenyaemr.util.EmrUtils;
import org.openmrs.module.metadatadeploy.MetadataUtils;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Set;

/**
 * Calculates the eligibility for Cholera screening flag for  patients
 *
 * @should calculate person over 2 years Old
 * @should calculate  diarrhoea
 * @should calculate Vomiting
 * @should calculate no duration
 */
public class AcuteWateryDiarrhoealCalculation extends AbstractPatientCalculation {
	protected static final Log log = LogFactory.getLog(AcuteWateryDiarrhoealCalculation.class);

	public static final EncounterType triageEncType = MetadataUtils.existing(EncounterType.class, CommonMetadata._EncounterType.TRIAGE);
	public static final Form triageScreeningForm = MetadataUtils.existing(Form.class, CommonMetadata._Form.TRIAGE);
	public static final EncounterType consultationEncType = MetadataUtils.existing(EncounterType.class, CommonMetadata._EncounterType.CONSULTATION);
	public static final Form clinicalEncounterForm = MetadataUtils.existing(Form.class, CommonMetadata._Form.CLINICAL_ENCOUNTER);
	public static final EncounterType greenCardEncType = MetadataUtils.existing(EncounterType.class, HivMetadata._EncounterType.HIV_CONSULTATION);
	public static final Form greenCardForm = MetadataUtils.existing(Form.class, HivMetadata._Form.HIV_GREEN_CARD);

	Integer SCREENING_QUESTION = 5219;
	Integer NUMBER_OF_MOTIONS = 164456;
	Integer WATERY_DIARRHOEAL = 161887;
	Integer DURATION = 159368;



	@Override
	public CalculationResultMap evaluate(Collection<Integer> cohort, Map<String, Object> parameterValues, PatientCalculationContext context) {
		Set<Integer> alive = Filters.alive(cohort, context);
		PatientService patientService = Context.getPatientService();
		CalculationResultMap ret = new CalculationResultMap();
		for (Integer ptId : alive) {
			boolean result = false;
			Date dateCreated = null;
			Double duration = 0.0;
			Double motions = 0.0;
			Date currentDate = new Date();
			SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
			String todayDate = dateFormat.format(currentDate);
			Patient patient = patientService.getPatient(ptId);

			Encounter lastTriageEnc = EmrUtils.lastEncounter(patient, triageEncType, triageScreeningForm);
			Encounter lastClinicalEncounter = EmrUtils.lastEncounter(patient, consultationEncType, clinicalEncounterForm); //last clinical encounter form
			Encounter lastGreenCardEnc = EmrUtils.lastEncounter(patient, greenCardEncType, greenCardForm);   //last greencard followup form
			ConceptService cs = Context.getConceptService();
			Concept wateryDiarrhoealResult = cs.getConcept(WATERY_DIARRHOEAL);
			Concept screeningQuestion = cs.getConcept(SCREENING_QUESTION);

			boolean patientWateryDiarrhoealTriageEncResult = lastTriageEnc != null ? EmrUtils.encounterThatPassCodedAnswer(lastTriageEnc, screeningQuestion, wateryDiarrhoealResult) : false;
			boolean patientWateryDiarrhoealClinicalEncResult = lastClinicalEncounter != null ? EmrUtils.encounterThatPassCodedAnswer(lastClinicalEncounter, screeningQuestion, wateryDiarrhoealResult) : false;
			boolean patientWateryDiarrhoealGreenCardResult = lastGreenCardEnc != null ? EmrUtils.encounterThatPassCodedAnswer(lastGreenCardEnc, screeningQuestion, wateryDiarrhoealResult) : false;
				if (lastClinicalEncounter != null) {
					for (Obs obs : lastClinicalEncounter.getObs()) {
						if (patientWateryDiarrhoealClinicalEncResult) {
							dateCreated = obs.getDateCreated();
							if (obs.getConcept().getConceptId().equals(NUMBER_OF_MOTIONS)) {
								motions = obs.getValueNumeric();
							}
							if (obs.getConcept().getConceptId().equals(DURATION)) {
								duration = obs.getValueNumeric();
							}
							if (dateCreated != null) {
								String createdDate = dateFormat.format(dateCreated);
								if ((motions > 3) && (duration > 0.0 && duration < 14)) {
									if (createdDate.equals(todayDate)) {
										result = true;
										break;
									}
								}
							}
						}
					}
				}
				if (lastGreenCardEnc != null) {
					for (Obs obs : lastGreenCardEnc.getObs()) {
						if (patientWateryDiarrhoealGreenCardResult) {
							dateCreated = obs.getDateCreated();
							if (obs.getConcept().getConceptId().equals(NUMBER_OF_MOTIONS)) {
								motions = obs.getValueNumeric();
							}
							if (obs.getConcept().getConceptId().equals(DURATION)) {
								duration = obs.getValueNumeric();
							}
							if (dateCreated != null) {
								String createdDate = dateFormat.format(dateCreated);
								if ((motions > 3) && (duration > 0.0 && duration < 14)) {
									if (createdDate.equals(todayDate)) {
										result = true;
										break;
									}
								}
							}
						}
					}
				}
				if (lastTriageEnc != null) {
					for (Obs obs : lastTriageEnc.getObs()) {
						if (patientWateryDiarrhoealTriageEncResult) {
							dateCreated = obs.getDateCreated();
							if (obs.getConcept().getConceptId().equals(NUMBER_OF_MOTIONS)) {
								motions = obs.getValueNumeric();
							}
							if (obs.getConcept().getConceptId().equals(DURATION)) {
								duration = obs.getValueNumeric();
							}
							if (dateCreated != null) {
								String createdDate = dateFormat.format(dateCreated);
								if ((motions > 3) && (duration > 0.0 && duration < 14)) {
									if (createdDate.equals(todayDate)) {
										result = true;
										break;
									}
								}
							}
						}
					}
				}

			ret.put(ptId, new BooleanResult(result, this));
		} 
		return ret;
	}
}
