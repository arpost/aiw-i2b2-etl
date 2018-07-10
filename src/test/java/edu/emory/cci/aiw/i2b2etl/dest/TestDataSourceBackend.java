/*
 * #%L
 * Protempa Test Suite
 * %%
 * Copyright (C) 2012 - 2013 Emory University
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package edu.emory.cci.aiw.i2b2etl.dest;

import java.io.IOException;

import org.protempa.backend.annotations.BackendInfo;
import org.protempa.backend.dsb.relationaldb.RelationalDbDataSourceBackend;
import org.protempa.backend.dsb.relationaldb.ColumnSpec;
import org.protempa.backend.dsb.relationaldb.Operator;
import org.protempa.backend.dsb.relationaldb.EntitySpec;
import org.protempa.backend.dsb.relationaldb.JDBCDateTimeTimestampDateValueFormat;
import org.protempa.backend.dsb.relationaldb.JDBCDateTimeTimestampPositionParser;
import org.protempa.backend.dsb.relationaldb.JDBCPositionFormat;
import org.protempa.backend.dsb.relationaldb.JoinSpec;
import org.protempa.backend.dsb.relationaldb.PropertySpec;
import org.protempa.backend.dsb.relationaldb.ReferenceSpec;
import org.protempa.backend.dsb.relationaldb.mappings.Mappings;
import org.protempa.backend.dsb.relationaldb.mappings.ResourceMappingsFactory;
import org.protempa.proposition.value.AbsoluteTimeGranularity;
import org.protempa.proposition.value.AbsoluteTimeGranularityFactory;
import org.protempa.proposition.value.AbsoluteTimeUnitFactory;
import org.protempa.proposition.value.GranularityFactory;
import org.protempa.proposition.value.UnitFactory;
import org.protempa.proposition.value.ValueType;

/**
 * Test data source backend (based on RegistryVM) .
 * 
 * @author Michel Mansour
 */
@BackendInfo(displayName = "Protempa Test Database")
public final class TestDataSourceBackend extends RelationalDbDataSourceBackend {

    private static final AbsoluteTimeUnitFactory absTimeUnitFactory = new AbsoluteTimeUnitFactory();
    private static final AbsoluteTimeGranularityFactory absTimeGranularityFactory = new AbsoluteTimeGranularityFactory();
    private static final JDBCPositionFormat dtPositionParser = new JDBCDateTimeTimestampPositionParser();

    /**
     * Initializes a new backend.
     */
    public TestDataSourceBackend() {
        setSchemaName("TEST");
        setDefaultKeyIdTable("PATIENT");
        setDefaultKeyIdColumn("PATIENT_KEY");
        setDefaultKeyIdJoinKey("PATIENT_KEY");
        setMappingsFactory(new ResourceMappingsFactory("/etc/mappings/", getClass()));
    }

    @Override
    public String getKeyType() {
        return "Patient";
    }

    @Override
    public String getKeyTypeDisplayName() {
        return "patient";
    }

    @Override
    protected EntitySpec[] constantSpecs(String keyIdSchema, String keyIdTable, String keyIdColumn, String keyIdJoinKey) throws IOException {
        String schemaName = getSchemaName();
        EntitySpec[] constantSpecs = new EntitySpec[] {

                new EntitySpec(
                        "Patients",
                        null,
                        new String[] { "Patient" },
                        false,
                        new ColumnSpec(keyIdSchema, keyIdTable,
                                keyIdColumn),
                        new ColumnSpec[] { new ColumnSpec(keyIdSchema,
                                keyIdTable, keyIdColumn) },
                        null,
                        null,
                        new PropertySpec[] { new PropertySpec("patientId",
                                null, new ColumnSpec(keyIdSchema,
                                        keyIdTable, "PATIENT_KEY"),
                                ValueType.NOMINALVALUE) },
                        new ReferenceSpec[] {
                                new ReferenceSpec(
                                        "encounters",
                                        "Encounters",
                                        new ColumnSpec[] { new ColumnSpec(
                                                keyIdSchema,
                                                keyIdTable,
                                                new JoinSpec(
                                                        "PATIENT_KEY",
                                                        "PATIENT_KEY",
                                                        new ColumnSpec(
                                                                schemaName,
                                                                "ENCOUNTER",
                                                                "ENCOUNTER_KEY"))) },
                                        ReferenceSpec.Type.MANY),
                                new ReferenceSpec(
                                        "patientDetails",
                                        "Patient Details",
                                        new ColumnSpec[] { new ColumnSpec(
                                                keyIdSchema,
                                                keyIdTable, "PATIENT_KEY") },
                                        ReferenceSpec.Type.MANY) }, null, null,
                        null, null, null, null, null, null,
                        new ColumnSpec(keyIdSchema, keyIdTable, "CREATE_DATE"),
                        new ColumnSpec(keyIdSchema, keyIdTable, "UPDATE_DATE"),
                        new ColumnSpec(keyIdSchema, keyIdTable, "DELETE_DATE")),
                new EntitySpec(
                        "Patient Details",
                        null,
                        new String[] { "PatientDetails" },
                        true,
                        new ColumnSpec(keyIdSchema, keyIdTable,
                                keyIdColumn),
                        new ColumnSpec[] { new ColumnSpec(schemaName,
                                keyIdTable, "PATIENT_KEY") },
                        null,
                        null,
                        new PropertySpec[] {
                                new PropertySpec(
                                        "dateOfBirth",
                                        null,
                                        new ColumnSpec(keyIdSchema,
                                                keyIdTable, "DOB"),
                                        ValueType.DATEVALUE,
                                        new JDBCDateTimeTimestampDateValueFormat()),
                                new PropertySpec(
                                        "patientId",
                                        null,
                                        new ColumnSpec(keyIdSchema,
                                                keyIdTable, "PATIENT_KEY"),
                                        ValueType.NOMINALVALUE),
                                new PropertySpec("firstName", null,
                                        new ColumnSpec(schemaName, "PATIENT",
                                                "FIRST_NAME"),
                                        ValueType.NOMINALVALUE),
                                new PropertySpec("lastName", null,
                                        new ColumnSpec(schemaName, "PATIENT",
                                                "LAST_NAME"),
                                        ValueType.NOMINALVALUE),
                                new PropertySpec(
                                        "gender",
                                        null,
                                        new ColumnSpec(
                                                schemaName,
                                                "PATIENT",
                                                "GENDER",
                                                Operator.EQUAL_TO,
                                                getMappingsFactory()
                                                        .getInstance("gender_02232012.txt"),
                                                true), ValueType.NOMINALVALUE),
                                new PropertySpec(
                                        "maritalStatus",
                                        null,
                                        new ColumnSpec(
                                                schemaName,
                                                "PATIENT",
                                                "MARITAL_STATUS",
                                                Operator.EQUAL_TO,
                                                getMappingsFactory()
                                                        .getInstance("marital_status_02232012.txt"),
                                                true), ValueType.NOMINALVALUE),
                                new PropertySpec(
                                        "language",
                                        null,
                                        new ColumnSpec(
                                                schemaName,
                                                "PATIENT",
                                                "LANGUAGE",
                                                Operator.EQUAL_TO,
                                                getMappingsFactory()
                                                        .getInstance("language_08152012.txt"),
                                                true), ValueType.NOMINALVALUE),
                                new PropertySpec(
                                        "race",
                                        null,
                                        new ColumnSpec(
                                                schemaName,
                                                "PATIENT",
                                                "RACE",
                                                Operator.EQUAL_TO,
                                                getMappingsFactory()
                                                        .getInstance("race_02232012.txt"),
                                                true), ValueType.NOMINALVALUE),
                                new PropertySpec(
                                        "ethnicity",
                                        null,
                                        new ColumnSpec(
                                                schemaName,
                                                "PATIENT",
                                                "RACE",
                                                Operator.EQUAL_TO,
                                                getMappingsFactory()
                                                        .getInstance("ethnicity_02232012.txt"),
                                                true), ValueType.NOMINALVALUE) },
                        new ReferenceSpec[] {
                                new ReferenceSpec(
                                        "encounters",
                                        "Encounters",
                                        new ColumnSpec[] { new ColumnSpec(
                                                schemaName,
                                                "PATIENT",
                                                new JoinSpec(
                                                        "PATIENT_KEY",
                                                        "PATIENT_KEY",
                                                        new ColumnSpec(
                                                                schemaName,
                                                                "ENCOUNTER",
                                                                "ENCOUNTER_KEY"))) },
                                        ReferenceSpec.Type.MANY),
                                new ReferenceSpec("patient", "Patients",
                                        new ColumnSpec[] { new ColumnSpec(
                                                schemaName, "PATIENT",
                                                "PATIENT_KEY") },
                                        ReferenceSpec.Type.ONE) }, null, null,
                        null, null, null, null, null, null,
                        new ColumnSpec(keyIdSchema, keyIdTable, "CREATE_DATE"),
                        new ColumnSpec(keyIdSchema, keyIdTable, "UPDATE_DATE"),
                        new ColumnSpec(keyIdSchema, keyIdTable, "DELETE_DATE")),
                new EntitySpec(
                        "Providers",
                        null,
                        new String[] { "Provider" },
                        false,
                        new ColumnSpec(keyIdSchema, keyIdTable,
                                keyIdColumn, new JoinSpec("PATIENT_KEY",
                                        "PATIENT_KEY", new ColumnSpec(
                                                schemaName, "ENCOUNTER",
                                                new JoinSpec("PROVIDER_KEY",
                                                        "PROVIDER_KEY",
                                                        new ColumnSpec(
                                                                schemaName,
                                                                "PROVIDER"))))),
                        new ColumnSpec[] { new ColumnSpec(schemaName,
                                "PROVIDER", "PROVIDER_KEY") }, null, null,
                        new PropertySpec[] {
                                new PropertySpec("firstName", null,
                                        new ColumnSpec(schemaName, "PROVIDER",
                                                "FIRST_NAME"),
                                        ValueType.NOMINALVALUE),
                                new PropertySpec("lastName", null,
                                        new ColumnSpec(schemaName, "PROVIDER",
                                                "LAST_NAME"),
                                        ValueType.NOMINALVALUE) }, null, null,
                        null, null, null, null, null, null, null,
                        new ColumnSpec(keyIdSchema, keyIdTable, "CREATE_DATE"),
                        new ColumnSpec(keyIdSchema, keyIdTable, "UPDATE_DATE"),
                        new ColumnSpec(keyIdSchema, keyIdTable, "DELETE_DATE")), };
        return constantSpecs;
    }

    @Override
    protected EntitySpec[] eventSpecs(String keyIdSchema, String keyIdTable, String keyIdColumn, String keyIdJoinKey) throws IOException {
        String schemaName = getSchemaName();
        Mappings icd9DiagnosisMappings = getMappingsFactory().getInstance("icd9_diagnosis_02232012.txt");
        Mappings icd9ProcedureMappings = getMappingsFactory().getInstance("icd9_procedure_02232012.txt");
        Mappings medsMappings = getMappingsFactory().getInstance("meds_02232012.txt");
        Mappings dxPriorityMappings = getMappingsFactory().getInstance("icd9_diagnosis_position_07182011.txt");
        Mappings dxSourceMappings = getMappingsFactory().getInstance("icd9_diagnosis_source_04232015.txt");
        EntitySpec[] eventSpecs = new EntitySpec[] {
                new EntitySpec(
                        "Encounters",
                        null,
                        new String[] { "Encounter" },
                        true,
                        new ColumnSpec(keyIdSchema, keyIdTable,
                                keyIdColumn, new JoinSpec("PATIENT_KEY",
                                        "PATIENT_KEY", new ColumnSpec(
                                                schemaName, "ENCOUNTER"))),
                        new ColumnSpec[] { new ColumnSpec(schemaName,
                                "ENCOUNTER", "ENCOUNTER_KEY") },
                        new ColumnSpec(schemaName, "ENCOUNTER", "TS_START"),
                        new ColumnSpec(schemaName, "ENCOUNTER", "TS_END"),
                        new PropertySpec[] {
                                new PropertySpec("encounterId", null,
                                        new ColumnSpec(schemaName, "ENCOUNTER",
                                                "ENCOUNTER_KEY"),
                                        ValueType.NOMINALVALUE),
                                new PropertySpec(
                                        "type",
                                        null,
                                        new ColumnSpec(
                                                schemaName,
                                                "ENCOUNTER",
                                                "ENCOUNTER_TYPE",
                                                Operator.EQUAL_TO,
                                                getMappingsFactory()
                                                        .getInstance("type_encounter_02232012.txt"),
                                                true), ValueType.NOMINALVALUE),
                                new PropertySpec(
                                        "dischargeDisposition",
                                        null,
                                        new ColumnSpec(
                                                schemaName,
                                                "ENCOUNTER",
                                                "DISCHARGE_DISP",
                                                Operator.EQUAL_TO,
                                                getMappingsFactory()
                                                        .getInstance("disposition_discharge_02232012.txt"),
                                                true), ValueType.NOMINALVALUE), },
                        new ReferenceSpec[] {
                                new ReferenceSpec("patient", "Patients",
                                        new ColumnSpec[] { new ColumnSpec(
                                                schemaName, "ENCOUNTER",
                                                "PATIENT_KEY") },
                                        ReferenceSpec.Type.ONE),
                                new ReferenceSpec(
                                        "EK_LABS",
                                        "Labs",
                                        new ColumnSpec[] { new ColumnSpec(
                                                schemaName, "ENCOUNTER",
                                                new JoinSpec("ENCOUNTER_KEY",
                                                        "ENCOUNTER_KEY",
                                                        new ColumnSpec(
                                                                schemaName,
                                                                "LABS_EVENT",
                                                                "EVENT_KEY"))) },
                                        ReferenceSpec.Type.MANY),
                                new ReferenceSpec(
                                        "EK_MED_ORDERS",
                                        "Medication Orders",
                                        new ColumnSpec[] { new ColumnSpec(
                                                schemaName, "ENCOUNTER",
                                                new JoinSpec("ENCOUNTER_KEY",
                                                        "ENCOUNTER_KEY",
                                                        new ColumnSpec(
                                                                schemaName,
                                                                "MEDS_EVENT",
                                                                "EVENT_KEY"))) },
                                        ReferenceSpec.Type.MANY),
                                new ReferenceSpec(
                                        "EK_VITALS",
                                        "Vitals",
                                        new ColumnSpec[] { new ColumnSpec(
                                                schemaName, "ENCOUNTER",
                                                new JoinSpec("ENCOUNTER_KEY",
                                                        "ENCOUNTER_KEY",
                                                        new ColumnSpec(
                                                                schemaName,
                                                                "VITALS_EVENT",
                                                                "EVENT_KEY"))) },
                                        ReferenceSpec.Type.MANY),
                                new ReferenceSpec(
                                        "EK_ICD9D",
                                        "Diagnosis Codes",
                                        new ColumnSpec[] { new ColumnSpec(
                                                schemaName, "ENCOUNTER",
                                                new JoinSpec("ENCOUNTER_KEY",
                                                        "ENCOUNTER_KEY",
                                                        new ColumnSpec(
                                                                schemaName,
                                                                "ICD9D_EVENT",
                                                                "EVENT_KEY"))) },
                                        ReferenceSpec.Type.MANY),
                                new ReferenceSpec(
                                        "EK_ICD9P",
                                        "ICD9 Procedure Codes",
                                        new ColumnSpec[] { new ColumnSpec(
                                                schemaName, "ENCOUNTER",
                                                new JoinSpec("ENCOUNTER_KEY",
                                                        "ENCOUNTER_KEY",
                                                        new ColumnSpec(
                                                                schemaName,
                                                                "ICD9P_EVENT",
                                                                "EVENT_KEY"))) },
                                        ReferenceSpec.Type.MANY),
                                new ReferenceSpec("provider", "Providers",
                                        new ColumnSpec[] { new ColumnSpec(
                                                schemaName, "ENCOUNTER",
                                                "PROVIDER_KEY") },
                                        ReferenceSpec.Type.ONE),
                                new ReferenceSpec("patientDetails",
                                        "Patient Details",
                                        new ColumnSpec[] { new ColumnSpec(
                                                schemaName, "ENCOUNTER",
                                                "PATIENT_KEY") },
                                        ReferenceSpec.Type.ONE), }, null,
                        null, null, null, null, AbsoluteTimeGranularity.MINUTE,
                        dtPositionParser, null,
                        new ColumnSpec(keyIdSchema, keyIdTable, "CREATE_DATE"),
                        new ColumnSpec(keyIdSchema, keyIdTable, "UPDATE_DATE"),
                        new ColumnSpec(keyIdSchema, keyIdTable, "DELETE_DATE")),
                new EntitySpec(
                        "Diagnosis Codes",
                        null,
                        icd9DiagnosisMappings.readTargets(),
                        true,
                        new ColumnSpec(
                                keyIdSchema,
                                keyIdTable,
                                keyIdColumn,
                                new JoinSpec(
                                        "PATIENT_KEY",
                                        "PATIENT_KEY",
                                        new ColumnSpec(schemaName, "ENCOUNTER",
                                                new JoinSpec("ENCOUNTER_KEY",
                                                        "ENCOUNTER_KEY",
                                                        new ColumnSpec(
                                                                schemaName,
                                                                "ICD9D_EVENT"))))),
                        new ColumnSpec[] { new ColumnSpec(schemaName,
                                "ICD9D_EVENT", "EVENT_KEY") },
                        new ColumnSpec(schemaName, "ICD9D_EVENT", "TS_OBX"),
                        null,
                        new PropertySpec[] { 
                            new PropertySpec(
                                "code",
                                null,
                                new ColumnSpec(
                                        schemaName,
                                        "ICD9D_EVENT",
                                        "ENTITY_ID",
                                        Operator.EQUAL_TO,
                                        icd9DiagnosisMappings),
                                ValueType.NOMINALVALUE), 
                            new PropertySpec(
                                "DXSOURCE",
                                null,
                                new ColumnSpec(
                                        schemaName,
                                        "ICD9D_EVENT",
                                        "SOURCE",
                                        Operator.EQUAL_TO,
                                        dxSourceMappings),
                                ValueType.NOMINALVALUE), 
                            new PropertySpec(
                                "DXPRIORITY",
                                null,
                                new ColumnSpec(
                                        schemaName,
                                        "ICD9D_EVENT",
                                        "RANK",
                                        Operator.EQUAL_TO,
                                        dxPriorityMappings),
                                ValueType.NOMINALVALUE), 
                        },
                        null,
                        null,
                        new ColumnSpec(
                                schemaName,
                                "ICD9D_EVENT",
                                "ENTITY_ID",
                                Operator.EQUAL_TO,
                                icd9DiagnosisMappings,
                                true), null, null, null,
                        AbsoluteTimeGranularity.MINUTE, dtPositionParser, null,
                        new ColumnSpec(keyIdSchema, keyIdTable, "CREATE_DATE"),
                        new ColumnSpec(keyIdSchema, keyIdTable, "UPDATE_DATE"),
                        new ColumnSpec(keyIdSchema, keyIdTable, "DELETE_DATE")),
                new EntitySpec(
                        "ICD9 Procedure Codes",
                        null,
                        icd9ProcedureMappings.readTargets(),
                        true,
                        new ColumnSpec(
                                keyIdSchema,
                                keyIdTable,
                                keyIdColumn,
                                new JoinSpec(
                                        "PATIENT_KEY",
                                        "PATIENT_KEY",
                                        new ColumnSpec(schemaName, "ENCOUNTER",
                                                new JoinSpec("ENCOUNTER_KEY",
                                                        "ENCOUNTER_KEY",
                                                        new ColumnSpec(
                                                                schemaName,
                                                                "ICD9P_EVENT"))))),
                        new ColumnSpec[] { new ColumnSpec(schemaName,
                                "ICD9P_EVENT", "EVENT_KEY") },
                        new ColumnSpec(schemaName, "ICD9P_EVENT", "TS_OBX"),
                        null,
                        new PropertySpec[] { new PropertySpec(
                                "code",
                                null,
                                new ColumnSpec(
                                        schemaName,
                                        "ICD9P_EVENT",
                                        "ENTITY_ID",
                                        Operator.EQUAL_TO,
                                        icd9ProcedureMappings),
                                ValueType.NOMINALVALUE) },
                        null,
                        null,
                        new ColumnSpec(
                                schemaName,
                                "ICD9P_EVENT",
                                "ENTITY_ID",
                                Operator.EQUAL_TO,
                                icd9ProcedureMappings,
                                true), null, null, null,
                        AbsoluteTimeGranularity.MINUTE, dtPositionParser, null,
                        new ColumnSpec(keyIdSchema, keyIdTable, "CREATE_DATE"),
                        new ColumnSpec(keyIdSchema, keyIdTable, "UPDATE_DATE"),
                        new ColumnSpec(keyIdSchema, keyIdTable, "DELETE_DATE")),
                new EntitySpec(
                        "Medication Orders",
                        null,
                        medsMappings.readTargets(),
                        true,
                        new ColumnSpec(
                                keyIdSchema,
                                keyIdTable,
                                keyIdColumn,
                                new JoinSpec("PATIENT_KEY", "PATIENT_KEY",
                                        new ColumnSpec(schemaName, "ENCOUNTER",
                                                new JoinSpec("ENCOUNTER_KEY",
                                                        "ENCOUNTER_KEY",
                                                        new ColumnSpec(
                                                                schemaName,
                                                                "MEDS_EVENT"))))),
                        new ColumnSpec[] { new ColumnSpec(schemaName,
                                "MEDS_EVENT", "EVENT_KEY") },
                        new ColumnSpec(schemaName, "MEDS_EVENT", "TS_OBX"),
                        null,
                        new PropertySpec[] {},
                        null,
                        null,
                        new ColumnSpec(
                                schemaName,
                                "MEDS_EVENT",
                                "ENTITY_ID",
                                Operator.EQUAL_TO,
                                medsMappings,
                                true), null, null, null,
                        AbsoluteTimeGranularity.MINUTE, dtPositionParser, null,
                        new ColumnSpec(keyIdSchema, keyIdTable, "CREATE_DATE"),
                        new ColumnSpec(keyIdSchema, keyIdTable, "UPDATE_DATE"),
                        new ColumnSpec(keyIdSchema, keyIdTable, "DELETE_DATE")), };
        return eventSpecs;
    }

    @Override
    protected EntitySpec[] primitiveParameterSpecs(String keyIdSchema, String keyIdTable, String keyIdColumn, String keyIdJoinKey) throws IOException {
        String schemaName = getSchemaName();
        Mappings labsMappings = getMappingsFactory().getInstance("labs_02232012.txt");
        Mappings vitalsMappings = getMappingsFactory().getInstance("vitals_result_types_02232012.txt");
        EntitySpec[] primitiveParameterSpecs = new EntitySpec[] {
                new EntitySpec(
                        "Labs",
                        null,
                        labsMappings.readTargets(),
                        true,
                        new ColumnSpec(
                                keyIdSchema,
                                keyIdTable,
                                keyIdColumn,
                                new JoinSpec("PATIENT_KEY", "PATIENT_KEY",
                                        new ColumnSpec(schemaName, "ENCOUNTER",
                                                new JoinSpec("ENCOUNTER_KEY",
                                                        "ENCOUNTER_KEY",
                                                        new ColumnSpec(
                                                                schemaName,
                                                                "LABS_EVENT"))))),
                        new ColumnSpec[] { new ColumnSpec(schemaName,
                                "LABS_EVENT", "EVENT_KEY") },
                        new ColumnSpec(schemaName, "LABS_EVENT", "TS_OBX"),
                        null,
                        new PropertySpec[] {
                                new PropertySpec("unitOfMeasure", null,
                                        new ColumnSpec(schemaName,
                                                "LABS_EVENT", "UNITS"),
                                        ValueType.NOMINALVALUE),
                                new PropertySpec("interpretation", null,
                                        new ColumnSpec(schemaName,
                                                "LABS_EVENT", "FLAG"),
                                        ValueType.NOMINALVALUE) },
                        null,
                        null,
                        new ColumnSpec(
                                schemaName,
                                "LABS_EVENT",
                                "ENTITY_ID",
                                Operator.EQUAL_TO,
                                labsMappings,
                                true), null, new ColumnSpec(schemaName,
                                "LABS_EVENT", "RESULT_STR"), ValueType.VALUE,
                        AbsoluteTimeGranularity.MINUTE, dtPositionParser, null,
                        new ColumnSpec(keyIdSchema, keyIdTable, "CREATE_DATE"),
                        new ColumnSpec(keyIdSchema, keyIdTable, "UPDATE_DATE"),
                        new ColumnSpec(keyIdSchema, keyIdTable, "DELETE_DATE")),
                new EntitySpec(
                        "Vitals",
                        null,
                        vitalsMappings.readTargets(),
                        true,
                        new ColumnSpec(
                                keyIdSchema,
                                keyIdTable,
                                keyIdColumn,
                                new JoinSpec(
                                        "PATIENT_KEY",
                                        "PATIENT_KEY",
                                        new ColumnSpec(
                                                schemaName,
                                                "ENCOUNTER",
                                                new JoinSpec("ENCOUNTER_KEY",
                                                        "ENCOUNTER_KEY",
                                                        new ColumnSpec(
                                                                schemaName,
                                                                "VITALS_EVENT"))))),
                        new ColumnSpec[] { new ColumnSpec(schemaName,
                                "VITALS_EVENT", "EVENT_KEY") },
                        new ColumnSpec(schemaName, "VITALS_EVENT", "TS_OBX"),
                        null,
                        new PropertySpec[] {
                                new PropertySpec("unitOfMeasure", null,
                                        new ColumnSpec(schemaName,
                                                "VITALS_EVENT", "UNITS"),
                                        ValueType.NOMINALVALUE),
                                new PropertySpec("interpretation", null,
                                        new ColumnSpec(schemaName,
                                                "VITALS_EVENT", "FLAG"),
                                        ValueType.NOMINALVALUE) },
                        null,
                        null,
                        new ColumnSpec(
                                schemaName,
                                "VITALS_EVENT",
                                "ENTITY_ID",
                                Operator.EQUAL_TO,
                                vitalsMappings,
                                true), null, new ColumnSpec(schemaName,
                                "VITALS_EVENT", "RESULT_STR"), ValueType.VALUE,
                        AbsoluteTimeGranularity.MINUTE, dtPositionParser, null,
                        new ColumnSpec(keyIdSchema, keyIdTable, "CREATE_DATE"),
                        new ColumnSpec(keyIdSchema, keyIdTable, "UPDATE_DATE"),
                        new ColumnSpec(keyIdSchema, keyIdTable, "DELETE_DATE")),

        };
        return primitiveParameterSpecs;
    }

    @Override
    public GranularityFactory getGranularityFactory() {
        return absTimeGranularityFactory;
    }

    @Override
    public UnitFactory getUnitFactory() {
        return absTimeUnitFactory;
    }
}
