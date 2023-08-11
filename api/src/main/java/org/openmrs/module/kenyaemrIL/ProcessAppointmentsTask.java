package org.openmrs.module.kenyaemrIL;

import org.codehaus.jackson.map.ObjectMapper;
import org.openmrs.Encounter;
import org.openmrs.EncounterType;
import org.openmrs.GlobalProperty;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.api.EncounterService;
import org.openmrs.api.context.Context;
import org.openmrs.module.kenyaemrIL.api.ILPatientAppointments;
import org.openmrs.module.kenyaemrIL.api.KenyaEMRILService;
import org.openmrs.module.kenyaemrIL.il.ILMessage;
import org.openmrs.scheduler.tasks.AbstractTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Implementation of a task that processes appointment tasks and marks them for sending to IL.
 */
public class ProcessAppointmentsTask extends AbstractTask {

    // Logger
    private static final Logger log = LoggerFactory.getLogger(ProcessAppointmentsTask.class);
    private ObjectMapper mapper = new ObjectMapper();

    /**
     * @see AbstractTask#execute()
     */
    @Override
    public void execute() {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        log.info("Executing appointments task at " + new Date());

        Date fetchDate = null;
        GlobalProperty globalPropertyObject = Context.getAdministrationService().getGlobalPropertyObject("appointmentTask.lastFetchDateAndTime");

        try {
            String ts = globalPropertyObject.getValue().toString();
            fetchDate = formatter.parse(ts);
        } catch (Exception e) {
            e.printStackTrace();
        }
        EncounterType encounterTypeGreencard = Context.getEncounterService().getEncounterTypeByUuid("a0034eee-1940-4e35-847f-97537a35d05e");
        //Fetch all encounters
        List<EncounterType> encounterTypes = new ArrayList<>();
        encounterTypes.add(encounterTypeGreencard);
        List<Encounter> pendingAppointments = fetchPendingAppointments(encounterTypes, fetchDate);

        Integer patientTCAConcept = 5096;
        Integer patientRefillConcept = 162549;
        List<String> prepEncounterTypes = Arrays.asList("c4a2be28-6673-4c36-b886-ea89b0a42116", "706a8b12-c4ce-40e4-aec3-258b989bf6d3");
        for (Encounter e : pendingAppointments) {
            Patient p = e.getPatient();
            for (Obs obs : e.getObs()) {
                if (prepEncounterTypes.contains(e.getEncounterType().getUuid()) &&
                        obs.getConcept().getConceptId().equals(patientTCAConcept)) {
                    appointmentsEvent(p, e, patientTCAConcept);
                } else if (obs.getConcept().getConceptId().equals(patientRefillConcept)) {
                    appointmentsEvent(p, e, patientRefillConcept);
                } else if (e.getEncounterType().getUuid().equals("a0034eee-1940-4e35-847f-97537a35d05e") &&
                        obs.getConcept().getConceptId().equals(patientTCAConcept)) {
                    appointmentsEvent(p, e, patientTCAConcept);
                }
            }
        }
        Date nextProcessingDate = new Date();
        globalPropertyObject.setPropertyValue(formatter.format(nextProcessingDate));
        Context.getAdministrationService().saveGlobalProperty(globalPropertyObject);

    }

    private List<Encounter> fetchPendingAppointments(List<EncounterType> encounterTypes, Date date) {

        SimpleDateFormat sd = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String effectiveDate = sd.format(date);
        StringBuilder q = new StringBuilder(); //TODO: We should add extra filter on form since the encounter type uuid used is shared by a number of forms
        q.append("select e.encounter_id ");
        q.append("from encounter e inner join " +
                "( " +
                " select encounter_type_id, uuid, name from encounter_type where uuid in ( 'a0034eee-1940-4e35-847f-97537a35d05e', 'c4a2be28-6673-4c36-b886-ea89b0a42116', '706a8b12-c4ce-40e4-aec3-258b989bf6d3') " +
                " ) et on et.encounter_type_id = e.encounter_type " +
                " inner join obs o on o.encounter_id = e.encounter_id and o.voided = 0 " +
                " and o.concept_id = 5096 and date(o.value_datetime) >= curdate() ");
        q.append("where e.date_created >= '" + effectiveDate + "' or e.date_changed >= '" + effectiveDate + "'" + " or o.date_created >= '" + effectiveDate + "'");
        q.append(" and e.voided = 0  ");

        List<Encounter> encounters = new ArrayList<>();
        EncounterService encounterService = Context.getEncounterService();
        List<List<Object>> queryData = Context.getAdministrationService().executeSQL(q.toString(), true);
        for (List<Object> row : queryData) {
            Integer encounterId = (Integer) row.get(0);
            Encounter e = encounterService.getEncounter(encounterId);
            encounters.add(e);
        }
        return encounters;

    }

    private boolean appointmentsEvent(Patient patient, Encounter e, Integer appointmentType) {
        ILMessage ilMessage = ILPatientAppointments.iLPatientWrapper(patient, e, appointmentType);
        KenyaEMRILService service = Context.getService(KenyaEMRILService.class);
        return service.logAppointmentSchedule(ilMessage, e.getPatient());
    }



}