package edu.emory.cci.aiw.i2b2etl.ksb;

/*
 * #%L
 * AIW i2b2 ETL
 * %%
 * Copyright (C) 2012 - 2015 Emory University
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
import au.com.bytecode.opencsv.CSVReader;
import edu.emory.cci.aiw.i2b2etl.ksb.QuerySupport.QuerySupportBuilder;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import org.apache.commons.lang3.ArrayUtils;
import org.arp.javautil.sql.DatabaseAPI;
import org.protempa.AbstractionDefinition;
import org.protempa.ContextDefinition;
import org.protempa.EventDefinition;
import org.protempa.KnowledgeSourceReadException;
import org.protempa.PropositionDefinition;
import org.protempa.TemporalPropositionDefinition;
import org.protempa.backend.AbstractCommonsKnowledgeSourceBackend;
import org.protempa.backend.annotations.BackendInfo;
import org.protempa.backend.annotations.BackendProperty;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.arp.javautil.arrays.Arrays;
import org.arp.javautil.collections.Collections;
import org.arp.javautil.io.IOUtil;
import org.arp.javautil.sql.DatabaseProduct;
import org.arp.javautil.sql.InvalidConnectionSpecArguments;
import org.protempa.AbstractPropositionDefinition;
import org.protempa.Attribute;
import org.protempa.ConstantDefinition;
import org.protempa.PrimitiveParameterDefinition;
import org.protempa.PropertyDefinition;
import org.protempa.ReferenceDefinition;
import org.protempa.valueset.ValueSet;
import org.protempa.valueset.ValueSetElement;
import org.protempa.backend.BackendInitializationException;
import org.protempa.backend.BackendInstanceSpec;
import org.protempa.backend.BackendSourceIdFactory;
import org.protempa.backend.KnowledgeSourceBackendInitializationException;
import org.protempa.dest.deid.DeidAttributes;
import org.protempa.proposition.value.BooleanValue;
import org.protempa.proposition.value.NominalValue;
import org.protempa.proposition.value.ValueType;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

/**
 * Implements using an <a href="http://www.i2b2.org">i2b2</a> metadata schema to
 * store proposition definitions.
 */
@BackendInfo(displayName = "I2b2 Knowledge Source Backend")
public class I2b2KnowledgeSourceBackend extends AbstractCommonsKnowledgeSourceBackend {

    private static final Logger LOGGER = Logger.getLogger(I2b2KnowledgeSourceBackend.class.getName());
    private static final char DEFAULT_DELIMITER = '\t';
    private static final Properties visitPropositionProperties;
    private static final Properties patientAliasPropositionProperties;
    private static final Properties patientPropositionProperties;
    private static final Properties patientDetailsPropositionProperties;
    private static final Properties providerPropositionProperties;
    private final static String[] VALUE_TYPE_CDS = {"LAB", "DOC"};
    private static final String defaultPatientPropositionId;
    private static final String defaultPatientAliasPropositionId;
    private static final String defaultPatientDetailsPropositionId;
    private static final ValueSet defaultLanguagePropertyValueSet;
    private static final ValueSet defaultMaritalStatusPropertyValueSet;
    private static final ValueSet defaultReligionPropertyValueSet;
    private static final ValueSet defaultGenderPropertyValueSet;
    private static final ValueSet defaultRacePropertyValueSet;
    private static final ValueSet defaultInoutPropertyValueSet;
    private static final String defaultVisitPropositionId;
    private static final Date DIMENSION_PROP_DEFS_CREATED_DATE;

    static {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(2015, Calendar.FEBRUARY, 8, 22, 13, 0);
        DIMENSION_PROP_DEFS_CREATED_DATE = cal.getTime();
    }

    static {
        try {
            patientPropositionProperties = IOUtil.loadPropertiesFromResource(I2b2KnowledgeSourceBackend.class, "/patientProposition.properties");
            visitPropositionProperties = IOUtil.loadPropertiesFromResource(I2b2KnowledgeSourceBackend.class, "/visitProposition.properties");
            patientAliasPropositionProperties = IOUtil.loadPropertiesFromResource(I2b2KnowledgeSourceBackend.class, "/patientAliasProposition.properties");
            patientDetailsPropositionProperties = IOUtil.loadPropertiesFromResource(I2b2KnowledgeSourceBackend.class, "/patientDetailsProposition.properties");
            providerPropositionProperties = IOUtil.loadPropertiesFromResource(I2b2KnowledgeSourceBackend.class, "/providerProposition.properties");
        } catch (IOException ex) {
            throw new AssertionError("Can't find dimension proposition properties file: " + ex.getMessage());
        }
        defaultPatientPropositionId = patientPropositionProperties.getProperty("propositionId");
        defaultPatientDetailsPropositionId = patientDetailsPropositionProperties.getProperty("propositionId");
        defaultLanguagePropertyValueSet = parseValueSetResource(
                patientDetailsPropositionProperties.getProperty("language.valueSet.id"),
                patientDetailsPropositionProperties.getProperty("language.valueSet.resource.name"),
                patientDetailsPropositionProperties.getProperty("language.valueSet.resource.hasHeader"),
                patientDetailsPropositionProperties.getProperty("language.valueSet.resource.delimiter"),
                patientDetailsPropositionProperties.getProperty("language.valueSet.resource.column.id"),
                patientDetailsPropositionProperties.getProperty("language.valueSet.resource.column.displayName"),
                patientDetailsPropositionProperties.getProperty("language.valueSet.resource.column.description"));
        defaultMaritalStatusPropertyValueSet = parseValueSetResource(
                patientDetailsPropositionProperties.getProperty("maritalStatus.valueSet.id"),
                patientDetailsPropositionProperties.getProperty("maritalStatus.valueSet.resource.name"),
                patientDetailsPropositionProperties.getProperty("maritalStatus.valueSet.resource.hasHeader"),
                patientDetailsPropositionProperties.getProperty("maritalStatus.valueSet.resource.delimiter"),
                patientDetailsPropositionProperties.getProperty("maritalStatus.valueSet.resource.column.id"),
                patientDetailsPropositionProperties.getProperty("maritalStatus.valueSet.resource.column.displayName"),
                patientDetailsPropositionProperties.getProperty("maritalStatus.valueSet.resource.column.description"));
        defaultReligionPropertyValueSet = parseValueSetResource(
                patientDetailsPropositionProperties.getProperty("religion.valueSet.id"),
                patientDetailsPropositionProperties.getProperty("religion.valueSet.resource.name"),
                patientDetailsPropositionProperties.getProperty("religion.valueSet.resource.hasHeader"),
                patientDetailsPropositionProperties.getProperty("religion.valueSet.resource.delimiter"),
                patientDetailsPropositionProperties.getProperty("religion.valueSet.resource.column.id"),
                patientDetailsPropositionProperties.getProperty("religion.valueSet.resource.column.displayName"),
                patientDetailsPropositionProperties.getProperty("religion.valueSet.resource.column.description"));
        defaultGenderPropertyValueSet = parseValueSet(patientDetailsPropositionProperties.getProperty("gender.valueSet.id"), patientDetailsPropositionProperties.getProperty("gender.valueSet.values"));
        defaultRacePropertyValueSet = parseValueSet(patientDetailsPropositionProperties.getProperty("race.valueSet.id"), patientDetailsPropositionProperties.getProperty("race.valueSet.values"));
        defaultPatientAliasPropositionId = patientAliasPropositionProperties.getProperty("propositionId");
        defaultVisitPropositionId = visitPropositionProperties.getProperty("propositionId");
        defaultInoutPropertyValueSet = parseValueSet(visitPropositionProperties.getProperty("inout.valueSet.id"), visitPropositionProperties.getProperty("inout.valueSet.values"));

    }

    private final QuerySupportBuilder querySupportBuilder;
    private QuerySupport querySupport;
    private String patientPropositionId;
    private String patientAliasPropositionId;
    private String patientDetailsPropositionId;
    private String visitPropositionId;
    private String providerPropositionId;
    private String providerNamePropertyName;
    private String inoutPropertyName;
    private String inoutPropertySource;
    private ValueSet inoutPropertyValueSet;
    private String inoutPropertyValueSetFile;
    private boolean inoutPropertyValueSetHasHeader;
    private char inoutPropertyValueSetDelimiter;
    private int inoutPropertyValueSetIdColumn;
    private Integer inoutPropertyValueSetDisplayNameColumn;
    private Integer inoutPropertyValueSetDescriptionColumn;
    private String visitAgePropertyName;
    private String genderPropertyName;
    private ValueSet genderPropertyValueSet;
    private String genderPropertySource;
    private String genderPropertyValueSetFile;
    private boolean genderPropertyValueSetHasHeader;
    private char genderPropertyValueSetDelimiter;
    private int genderPropertyValueSetIdColumn;
    private Integer genderPropertyValueSetDisplayNameColumn;
    private Integer genderPropertyValueSetDescriptionColumn;
    private String racePropertyName;
    private ValueSet racePropertyValueSet;
    private String racePropertySource;
    private String racePropertyValueSetFile;
    private boolean racePropertyValueSetHasHeader;
    private char racePropertyValueSetDelimiter;
    private int racePropertyValueSetIdColumn;
    private Integer racePropertyValueSetDisplayNameColumn;
    private Integer racePropertyValueSetDescriptionColumn;
    private String languagePropertyName;
    private ValueSet languagePropertyValueSet;
    private String languagePropertySource;
    private String languagePropertyValueSetFile;
    private boolean languagePropertyValueSetHasHeader;
    private char languagePropertyValueSetDelimiter;
    private int languagePropertyValueSetIdColumn;
    private Integer languagePropertyValueSetDisplayNameColumn;
    private Integer languagePropertyValueSetDescriptionColumn;
    private ValueSet maritalStatusPropertyValueSet;
    private String maritalStatusPropertySource;
    private String maritalStatusPropertyName;
    private String maritalStatusPropertyValueSetFile;
    private boolean maritalStatusPropertyValueSetHasHeader;
    private char maritalStatusPropertyValueSetDelimiter;
    private int maritalStatusPropertyValueSetIdColumn;
    private Integer maritalStatusPropertyValueSetDisplayNameColumn;
    private Integer maritalStatusPropertyValueSetDescriptionColumn;
    private String religionPropertyName;
    private ValueSet religionPropertyValueSet;
    private String religionPropertySource;
    private String religionPropertyValueSetFile;
    private boolean religionPropertyValueSetHasHeader;
    private char religionPropertyValueSetDelimiter;
    private int religionPropertyValueSetIdColumn;
    private Integer religionPropertyValueSetDisplayNameColumn;
    private Integer religionPropertyValueSetDescriptionColumn;
    private String dateOfBirthPropertyName;
    private String ageInYearsPropertyName;
    private String dateOfDeathPropertyName;
    private String vitalStatusPropertyName;
    private final String patientDisplayName;
    private final String patientAliasDisplayName;
    private String patientDetailsDisplayName;
    private String visitDisplayName;
    private String visitIdPropertyName;
    private String providerDisplayName;
    private final Map<String, ValueSet> valueSets;
    private final BackendSourceIdFactory sourceIdFactory;
    private String patientPatientIdPropertyName;
    private final String patientAliasPatientIdPropertyName;
    private final String patientAliasFieldNamePropertyName;
    private String patientDetailsPatientIdPropertyName;
    private Map<String, Map<String, ModInterp>> modInterpCached;
    private final String modInterpCachedSyncVariable = "MOD_INTERP_CACHED_SYNC";

    public I2b2KnowledgeSourceBackend() {
        this.querySupportBuilder = new QuerySupportBuilder();
        this.valueSets = new HashMap<>();

        /**
         * Patient
         */
        this.patientPropositionId = defaultPatientPropositionId;
        this.patientDisplayName = patientPropositionProperties.getProperty("displayName");
        this.patientPatientIdPropertyName = patientPropositionProperties.getProperty("patientId.propertyName");

        this.patientAliasPropositionId = defaultPatientAliasPropositionId;
        this.patientAliasDisplayName = patientAliasPropositionProperties.getProperty("displayName");
        this.patientAliasPatientIdPropertyName = patientAliasPropositionProperties.getProperty("patientId.propertyName");
        this.patientAliasFieldNamePropertyName = patientAliasPropositionProperties.getProperty("fieldName.propertyName");

        this.patientDetailsPropositionId = defaultPatientDetailsPropositionId;
        this.patientDetailsDisplayName = patientDetailsPropositionProperties.getProperty("displayName");

        this.patientDetailsPatientIdPropertyName = patientDetailsPropositionProperties.getProperty("patientId.propertyName");

        this.genderPropertyName = patientDetailsPropositionProperties.getProperty("gender.propertyName");
        this.genderPropertyValueSet = defaultGenderPropertyValueSet;
        if (this.genderPropertyValueSet != null) {
            this.valueSets.put(this.genderPropertyValueSet.getId(), this.genderPropertyValueSet);
        }
        this.genderPropertySource = patientDetailsPropositionProperties.getProperty("gender.valueSet.source");

        this.racePropertyName = patientDetailsPropositionProperties.getProperty("race.propertyName");
        this.racePropertyValueSet = defaultRacePropertyValueSet;
        if (this.racePropertyValueSet != null) {
            this.valueSets.put(this.racePropertyValueSet.getId(), this.racePropertyValueSet);
        }
        this.racePropertySource = patientDetailsPropositionProperties.getProperty("race.valueSet.source");

        this.languagePropertyName = patientDetailsPropositionProperties.getProperty("language.propertyName");
        this.languagePropertyValueSet = defaultLanguagePropertyValueSet;
        if (this.languagePropertyValueSet != null) {
            this.valueSets.put(this.languagePropertyValueSet.getId(), this.languagePropertyValueSet);
        }
        this.languagePropertySource = patientDetailsPropositionProperties.getProperty("language.valueSet.source");

        this.maritalStatusPropertyName = patientDetailsPropositionProperties.getProperty("maritalStatus.propertyName");
        this.maritalStatusPropertyValueSet = defaultMaritalStatusPropertyValueSet;
        if (this.maritalStatusPropertyValueSet != null) {
            this.valueSets.put(this.maritalStatusPropertyValueSet.getId(), this.maritalStatusPropertyValueSet);
        }
        this.maritalStatusPropertySource = patientDetailsPropositionProperties.getProperty("maritalStatus.valueSet.source");

        this.religionPropertyName = patientDetailsPropositionProperties.getProperty("religion.propertyName");
        this.religionPropertyValueSet = defaultReligionPropertyValueSet;
        if (this.religionPropertyValueSet != null) {
            this.valueSets.put(this.religionPropertyValueSet.getId(), this.religionPropertyValueSet);
        }
        this.religionPropertySource = patientDetailsPropositionProperties.getProperty("religion.valueSet.source");

        this.dateOfBirthPropertyName = patientDetailsPropositionProperties.getProperty("dateOfBirth.propertyName");

        this.ageInYearsPropertyName = patientDetailsPropositionProperties.getProperty("ageInYears.propertyName");

        this.dateOfDeathPropertyName = patientDetailsPropositionProperties.getProperty("dateOfDeath.propertyName");

        this.vitalStatusPropertyName = patientDetailsPropositionProperties.getProperty("vitalStatus.propertyName");

        /**
         * Visit
         */
        this.visitPropositionId = defaultVisitPropositionId;
        this.visitDisplayName = visitPropositionProperties.getProperty("displayName");

        this.inoutPropertyName = visitPropositionProperties.getProperty("inout.propertyName");
        this.inoutPropertyValueSet = defaultInoutPropertyValueSet;
        if (this.inoutPropertyValueSet != null) {
            this.valueSets.put(this.inoutPropertyValueSet.getId(), this.inoutPropertyValueSet);
        }
        this.inoutPropertySource = visitPropositionProperties.getProperty("inout.valueSet.source");

        this.visitAgePropertyName = visitPropositionProperties.getProperty("age.propertyName");
        this.visitIdPropertyName = visitPropositionProperties.getProperty("id.propertyName");

        /**
         * Provider
         */
        this.providerPropositionId = providerPropositionProperties.getProperty("propositionId");
        this.providerDisplayName = visitPropositionProperties.getProperty("displayName");
        this.providerNamePropertyName = providerPropositionProperties.getProperty("name.propertyName");

        this.genderPropertyValueSetDelimiter = DEFAULT_DELIMITER;
        this.racePropertyValueSetDelimiter = DEFAULT_DELIMITER;
        this.languagePropertyValueSetDelimiter = DEFAULT_DELIMITER;
        this.maritalStatusPropertyValueSetDelimiter = DEFAULT_DELIMITER;
        this.religionPropertyValueSetDelimiter = DEFAULT_DELIMITER;
        this.sourceIdFactory = new BackendSourceIdFactory(this);
    }

    /**
     * Returns which Java database API this backend is configured to use.
     *
     * @return a {@link DatabaseAPI}. The default value is
     * {@link org.arp.javautil.sql.DatabaseAPI}<code>.DRIVERMANAGER</code>
     */
    public DatabaseAPI getDatabaseApi() {
        return this.querySupportBuilder.getDatabaseApi();
    }

    /**
     * Configures which Java database API to use ({@link java.sql.DriverManager}
     * or {@link javax.sql.DataSource}. If <code>null</code>, the default is
     * assigned
     * ({@link org.arp.javautil.sql.DatabaseAPI}<code>.DRIVERMANAGER</code>).
     *
     * @param databaseApi a {@link DatabaseAPI}.
     */
    public void setDatabaseApi(DatabaseAPI databaseApi) {
        this.querySupportBuilder.setDatabaseApi(databaseApi);
    }

    public String getTargetTable() {
        return this.querySupportBuilder.getExcludeTableName();
    }

    @BackendProperty
    public void setTargetTable(String tableName) {
        this.querySupportBuilder.setExcludeTableName(tableName);
    }

    /**
     * Configures which Java database API to use ({@link java.sql.DriverManager}
     * or {@link javax.sql.DataSource} by parsing a {@link DatabaseAPI}'s name.
     * Cannot be null.
     *
     * @param databaseApiString a {@link DatabaseAPI}'s name.
     */
    @BackendProperty(propertyName = "databaseAPI")
    public void parseDatabaseApi(String databaseApiString) {
        setDatabaseApi(DatabaseAPI.valueOf(databaseApiString));
    }

    public String getDatabaseId() {
        return this.querySupportBuilder.getDatabaseId();
    }

    @BackendProperty
    public void setDatabaseId(String databaseId) {
        this.querySupportBuilder.setDatabaseId(databaseId);
    }

    public String getUsername() {
        return this.querySupportBuilder.getUsername();
    }

    @BackendProperty
    public void setUsername(String username) {
        this.querySupportBuilder.setUsername(username);
    }

    public String getPassword() {
        return this.querySupportBuilder.getPassword();
    }

    @BackendProperty
    public void setPassword(String password) {
        this.querySupportBuilder.setPassword(password);
    }

    public String getPatientPatientIdPropertyName() {
        return patientPatientIdPropertyName;
    }

    @BackendProperty
    public void setPatientPatientIdPropertyName(String patientPatientIdPropertyName) {
        this.patientPatientIdPropertyName = patientPatientIdPropertyName;
    }

    public String getPatientDetailsPatientIdPropertyName() {
        return patientDetailsPatientIdPropertyName;
    }

    @BackendProperty
    public void setPatientDetailsPatientIdPropertyName(String patientDetailsPatientIdPropertyName) {
        this.patientDetailsPatientIdPropertyName = patientDetailsPatientIdPropertyName;
    }

    public String getPatientPropositionId() {
        return patientPropositionId;
    }

    @BackendProperty
    public void setPatientPropositionId(String patientPropositionId) {
        if (patientPropositionId != null) {
            this.patientPropositionId = patientPropositionId;
        } else {
            this.patientPropositionId = defaultPatientPropositionId;
        }
    }

    public String getPatientDetailsPropositionId() {
        return patientDetailsPropositionId;
    }

    @BackendProperty
    public void setPatientDetailsPropositionId(String patientDetailsPropositionId) {
        if (patientDetailsPropositionId != null) {
            this.patientDetailsPropositionId = patientDetailsPropositionId;
        } else {
            this.patientDetailsPropositionId = defaultPatientDetailsPropositionId;
        }
    }

    public String getPatientDetailsDisplayName() {
        return patientDetailsDisplayName;
    }

    @BackendProperty
    public void setPatientDetailsDisplayName(String patientDetailsDisplayName) {
        this.patientDetailsDisplayName = patientDetailsDisplayName;
    }

    public String getVisitPropositionId() {
        return visitPropositionId;
    }

    @BackendProperty
    public void setVisitPropositionId(String visitPropositionId) {
        if (visitPropositionId != null) {
            this.visitPropositionId = visitPropositionId;
        } else {
            this.visitPropositionId = defaultVisitPropositionId;
        }
    }

    public String getVisitDisplayName() {
        return visitDisplayName;
    }

    @BackendProperty
    public void setVisitDisplayName(String visitDisplayName) {
        this.visitDisplayName = visitDisplayName;
    }

    public String getProviderPropositionId() {
        return providerPropositionId;
    }

    @BackendProperty
    public void setProviderPropositionId(String providerPropositionId) {
        if (providerPropositionId != null) {
            this.providerPropositionId = providerPropositionId;
        } else {
            this.providerPropositionId = this.providerPropositionProperties.getProperty("propositionId");
        }
    }

    public String getProviderDisplayName() {
        return providerDisplayName;
    }

    @BackendProperty
    public void setProviderDisplayName(String providerDisplayName) {
        this.providerDisplayName = providerDisplayName;
    }

    public String getProviderNamePropertyName() {
        return providerNamePropertyName;
    }

    @BackendProperty
    public void setProviderNamePropertyName(String providerNamePropertyName) {
        this.providerNamePropertyName = providerNamePropertyName;
    }

    public String getInoutPropertyName() {
        return inoutPropertyName;
    }

    @BackendProperty
    public void setInoutPropertyName(String inoutPropertyName) {
        this.inoutPropertyName = inoutPropertyName;
    }

    public String getInoutPropertySource() {
        return inoutPropertySource;
    }

    @BackendProperty
    public void setInoutPropertySource(String inoutPropertySource) {
        this.inoutPropertySource = inoutPropertySource;
    }

    public ValueSet getInoutPropertyValueSet() {
        return inoutPropertyValueSet;
    }

    public void setInoutPropertyValueSet(ValueSet inoutPropertyValueSet) {
        this.inoutPropertyValueSet = inoutPropertyValueSet;
    }

    @BackendProperty(propertyName = "inoutPropertyValueSet")
    public void parseInoutPropertyValueSet(String valueSetStr) {
        this.inoutPropertyValueSet = parseValueSet(this.visitPropositionId, valueSetStr);
    }

    public String getInoutPropertyValueSetFile() {
        return inoutPropertyValueSetFile;
    }

    @BackendProperty
    public void setInoutPropertyValueSetFile(String inoutPropertyValueSetFile) {
        this.inoutPropertyValueSetFile = inoutPropertyValueSetFile;
    }

    public Boolean getInoutPropertyValueSetHasHeader() {
        return inoutPropertyValueSetHasHeader;
    }

    @BackendProperty
    public void setInoutPropertyValueSetHasHeader(Boolean inoutPropertyValueSetHasHeader) {
        if (inoutPropertyValueSetHasHeader != null) {
            this.inoutPropertyValueSetHasHeader = inoutPropertyValueSetHasHeader;
        } else {
            this.inoutPropertyValueSetHasHeader = false;
        }
    }

    public String getInoutPropertyValueSetDelimiter() {
        return Character.toString(inoutPropertyValueSetDelimiter);
    }

    @BackendProperty
    public void setInoutPropertyValueSetDelimiter(String inoutPropertyValueSetDelimiter) {
        if (inoutPropertyValueSetDelimiter != null) {
            if (inoutPropertyValueSetDelimiter.length() > 1) {
                throw new IllegalArgumentException("delimiter can only be one character");
            }
            if (inoutPropertyValueSetDelimiter.length() < 1) {
                throw new IllegalArgumentException("delimiter must be at least one character");
            }
        }
        if (inoutPropertyValueSetDelimiter != null) {
            this.inoutPropertyValueSetDelimiter = inoutPropertyValueSetDelimiter.charAt(0);
        } else {
            this.inoutPropertyValueSetDelimiter = DEFAULT_DELIMITER;
        }
    }

    public Integer getInoutPropertyValueSetIdColumn() {
        return inoutPropertyValueSetIdColumn;
    }

    @BackendProperty
    public void setInoutPropertyValueSetIdColumn(Integer inoutPropertyValueSetIdColumn) {
        if (inoutPropertyValueSetIdColumn == null) {
            throw new IllegalArgumentException("inoutPropertyValueSetIdColumn cannot be null");
        }
        this.inoutPropertyValueSetIdColumn = inoutPropertyValueSetIdColumn;
    }

    public Integer getInoutPropertyValueSetDisplayNameColumn() {
        return inoutPropertyValueSetDisplayNameColumn;
    }

    @BackendProperty
    public void setInoutPropertyValueSetDisplayNameColumn(Integer inoutPropertyValueSetDisplayNameColumn) {
        this.inoutPropertyValueSetDisplayNameColumn = inoutPropertyValueSetDisplayNameColumn;
    }

    public Integer getInoutPropertyValueSetDescriptionColumn() {
        return inoutPropertyValueSetDescriptionColumn;
    }

    @BackendProperty
    public void setInoutPropertyValueSetDescriptionColumn(Integer inoutPropertyValueSetDescriptionColumn) {
        this.inoutPropertyValueSetDescriptionColumn = inoutPropertyValueSetDescriptionColumn;
    }

    public String getVisitAgePropertyName() {
        return visitAgePropertyName;
    }

    @BackendProperty
    public void setVisitAgePropertyName(String visitAgePropertyName) {
        this.visitAgePropertyName = visitAgePropertyName;
    }

    public String getGenderPropertyName() {
        return genderPropertyName;
    }

    @BackendProperty
    public void setGenderPropertyName(String genderPropertyName) {
        this.genderPropertyName = genderPropertyName;
    }

    public ValueSet getGenderPropertyValueSet() {
        return genderPropertyValueSet;
    }

    public void setGenderPropertyValueSet(ValueSet genderPropertyValueSet) {
        this.genderPropertyValueSet = genderPropertyValueSet;
    }

    @BackendProperty(propertyName = "genderPropertyValueSet")
    public void parseGenderPropertyValueSet(String valueSetStr) {
        this.genderPropertyValueSet = parseValueSet(this.patientDetailsPropositionId, valueSetStr);
    }

    public String getGenderPropertySource() {
        return genderPropertySource;
    }

    @BackendProperty
    public void setGenderPropertySource(String genderPropertySource) {
        this.genderPropertySource = genderPropertySource;
    }

    public String getGenderPropertyValueSetFile() {
        return genderPropertyValueSetFile;
    }

    @BackendProperty
    public void setGenderPropertyValueSetFile(String genderPropertyValueSetFile) {
        this.genderPropertyValueSetFile = genderPropertyValueSetFile;
    }

    public Boolean getGenderPropertyValueSetHasHeader() {
        return genderPropertyValueSetHasHeader;
    }

    @BackendProperty
    public void setGenderPropertyValueSetHasHeader(Boolean genderPropertyValueSetHasHeader) {
        if (genderPropertyValueSetHasHeader != null) {
            this.genderPropertyValueSetHasHeader = genderPropertyValueSetHasHeader;
        } else {
            this.genderPropertyValueSetHasHeader = false;
        }
    }

    public String getGenderPropertyValueSetDelimiter() {
        return Character.toString(genderPropertyValueSetDelimiter);
    }

    @BackendProperty
    public void setGenderPropertyValueSetDelimiter(String genderPropertyValueSetDelimiter) {
        if (genderPropertyValueSetDelimiter != null) {
            if (genderPropertyValueSetDelimiter.length() > 1) {
                throw new IllegalArgumentException("delimiter can only be one character");
            }
            if (genderPropertyValueSetDelimiter.length() < 1) {
                throw new IllegalArgumentException("delimiter must be at least one character");
            }
        }
        if (genderPropertyValueSetDelimiter != null) {
            this.genderPropertyValueSetDelimiter = genderPropertyValueSetDelimiter.charAt(0);
        } else {
            this.genderPropertyValueSetDelimiter = DEFAULT_DELIMITER;
        }
    }

    public Integer getGenderPropertyValueSetIdColumn() {
        return genderPropertyValueSetIdColumn;
    }

    @BackendProperty
    public void setGenderPropertyValueSetIdColumn(Integer genderPropertyValueSetIdColumn) {
        if (genderPropertyValueSetIdColumn == null) {
            throw new IllegalArgumentException("genderPropertyValueSetIdColumn cannot be null");
        }
        this.genderPropertyValueSetIdColumn = genderPropertyValueSetIdColumn;
    }

    public Integer getGenderPropertyValueSetDisplayNameColumn() {
        return genderPropertyValueSetDisplayNameColumn;
    }

    @BackendProperty
    public void setGenderPropertyValueSetDisplayNameColumn(Integer genderPropertyValueSetDisplayNameColumn) {
        this.genderPropertyValueSetDisplayNameColumn = genderPropertyValueSetDisplayNameColumn;
    }

    public Integer getGenderPropertyValueSetDescriptionColumn() {
        return genderPropertyValueSetDescriptionColumn;
    }

    @BackendProperty
    public void setGenderPropertyValueSetDescriptionColumn(Integer genderPropertyValueSetDescriptionColumn) {
        this.genderPropertyValueSetDescriptionColumn = genderPropertyValueSetDescriptionColumn;
    }

    public String getRacePropertyName() {
        return racePropertyName;
    }

    @BackendProperty
    public void setRacePropertyName(String racePropertyName) {
        this.racePropertyName = racePropertyName;
    }

    public ValueSet getRacePropertyValueSet() {
        return racePropertyValueSet;
    }

    public void setRacePropertyValueSet(ValueSet racePropertyValueSet) {
        this.racePropertyValueSet = racePropertyValueSet;
    }

    @BackendProperty(propertyName = "racePropertyValueSet")
    public void parseRacePropertyValueSet(String valueSetStr) {
        this.racePropertyValueSet = parseValueSet(this.patientDetailsPropositionId, valueSetStr);
    }

    public String getRacePropertyValueSetFile() {
        return racePropertyValueSetFile;
    }

    @BackendProperty
    public void setRacePropertyValueSetFile(String racePropertyValueSetFile) {
        this.racePropertyValueSetFile = racePropertyValueSetFile;
    }

    public Boolean getRacePropertyValueSetHasHeader() {
        return racePropertyValueSetHasHeader;
    }

    @BackendProperty
    public void setRacePropertyValueSetHasHeader(Boolean racePropertyValueSetHasHeader) {
        if (racePropertyValueSetHasHeader != null) {
            this.racePropertyValueSetHasHeader = racePropertyValueSetHasHeader;
        } else {
            this.racePropertyValueSetHasHeader = false;
        }
    }

    public String getRacePropertyValueSetDelimiter() {
        return Character.toString(racePropertyValueSetDelimiter);
    }

    @BackendProperty
    public void setRacePropertyValueSetDelimiter(String racePropertyValueSetDelimiter) {
        if (racePropertyValueSetDelimiter != null) {
            if (racePropertyValueSetDelimiter.length() > 1) {
                throw new IllegalArgumentException("delimiter can only be one character");
            }
            if (racePropertyValueSetDelimiter.length() < 1) {
                throw new IllegalArgumentException("delimiter must be at least one character");
            }
        }
        if (racePropertyValueSetDelimiter != null) {
            this.racePropertyValueSetDelimiter = racePropertyValueSetDelimiter.charAt(0);
        } else {
            this.racePropertyValueSetDelimiter = DEFAULT_DELIMITER;
        }
    }

    public Integer getRacePropertyValueSetIdColumn() {
        return racePropertyValueSetIdColumn;
    }

    @BackendProperty
    public void setRacePropertyValueSetIdColumn(Integer racePropertyValueSetIdColumn) {
        if (racePropertyValueSetIdColumn == null) {
            throw new IllegalArgumentException("racePropertyValueSetIdColumn cannot be null");
        }
        this.racePropertyValueSetIdColumn = racePropertyValueSetIdColumn;
    }

    public Integer getRacePropertyValueSetDisplayNameColumn() {
        return racePropertyValueSetDisplayNameColumn;
    }

    @BackendProperty
    public void setRacePropertyValueSetDisplayNameColumn(Integer racePropertyValueSetDisplayNameColumn) {
        this.racePropertyValueSetDisplayNameColumn = racePropertyValueSetDisplayNameColumn;
    }

    public Integer getRacePropertyValueSetDescriptionColumn() {
        return racePropertyValueSetDescriptionColumn;
    }

    @BackendProperty
    public void setRacePropertyValueSetDescriptionColumn(Integer racePropertyValueSetDescriptionColumn) {
        this.racePropertyValueSetDescriptionColumn = racePropertyValueSetDescriptionColumn;
    }

    public String getRacePropertySource() {
        return racePropertySource;
    }

    @BackendProperty
    public void setRacePropertySource(String racePropertySource) {
        this.racePropertySource = racePropertySource;
    }

    public String getLanguagePropertyName() {
        return languagePropertyName;
    }

    @BackendProperty
    public void setLanguagePropertyName(String languagePropertyName) {
        this.languagePropertyName = languagePropertyName;
    }

    public ValueSet getLanguagePropertyValueSet() {
        return languagePropertyValueSet;
    }

    public void setLanguagePropertyValueSet(ValueSet languagePropertyValueSet) {
        this.languagePropertyValueSet = languagePropertyValueSet;
    }

    @BackendProperty(propertyName = "languagePropertyValueSet")
    public void parseLanguagePropertyValueSet(String valueSetStr) {
        this.languagePropertyValueSet = parseValueSet(this.patientDetailsPropositionId, valueSetStr);
    }

    public String getLanguagePropertyValueSetFile() {
        return languagePropertyValueSetFile;
    }

    @BackendProperty
    public void setLanguagePropertyValueSetFile(String languagePropertyValueSetFile) {
        this.languagePropertyValueSetFile = languagePropertyValueSetFile;
    }

    public Boolean getLanguagePropertyValueSetHasHeader() {
        return languagePropertyValueSetHasHeader;
    }

    @BackendProperty
    public void setLanguagePropertyValueSetHasHeader(Boolean languagePropertyValueSetHasHeader) {
        if (languagePropertyValueSetHasHeader != null) {
            this.languagePropertyValueSetHasHeader = languagePropertyValueSetHasHeader;
        } else {
            this.languagePropertyValueSetHasHeader = false;
        }
    }

    public String getLanguagePropertyValueSetDelimiter() {
        return Character.toString(languagePropertyValueSetDelimiter);
    }

    @BackendProperty
    public void setLanguagePropertyValueSetDelimiter(String languagePropertyValueSetDelimiter) {
        if (languagePropertyValueSetDelimiter != null) {
            if (languagePropertyValueSetDelimiter.length() > 1) {
                throw new IllegalArgumentException("delimiter can only be one character");
            }
            if (languagePropertyValueSetDelimiter.length() < 1) {
                throw new IllegalArgumentException("delimiter must be at least one character");
            }
        }
        if (languagePropertyValueSetDelimiter != null) {
            this.languagePropertyValueSetDelimiter = languagePropertyValueSetDelimiter.charAt(0);
        } else {
            this.languagePropertyValueSetDelimiter = DEFAULT_DELIMITER;
        }
    }

    public Integer getLanguagePropertyValueSetIdColumn() {
        return languagePropertyValueSetIdColumn;
    }

    @BackendProperty
    public void setLanguagePropertyValueSetIdColumn(Integer languagePropertyValueSetIdColumn) {
        if (languagePropertyValueSetIdColumn == null) {
            throw new IllegalArgumentException("languagePropertyValueSetIdColumn cannot be null");
        }
        this.languagePropertyValueSetIdColumn = languagePropertyValueSetIdColumn;
    }

    public Integer getLanguagePropertyValueSetDisplayNameColumn() {
        return languagePropertyValueSetDisplayNameColumn;
    }

    @BackendProperty
    public void setLanguagePropertyValueSetDisplayNameColumn(Integer languagePropertyValueSetDisplayNameColumn) {
        this.languagePropertyValueSetDisplayNameColumn = languagePropertyValueSetDisplayNameColumn;
    }

    public Integer getLanguagePropertyValueSetDescriptionColumn() {
        return languagePropertyValueSetDescriptionColumn;
    }

    @BackendProperty
    public void setLanguagePropertyValueSetDescriptionColumn(Integer languagePropertyValueSetDescriptionColumn) {
        this.languagePropertyValueSetDescriptionColumn = languagePropertyValueSetDescriptionColumn;
    }

    public String getLanguagePropertySource() {
        return languagePropertySource;
    }

    @BackendProperty
    public void setLanguagePropertySource(String languagePropertySource) {
        this.languagePropertySource = languagePropertySource;
    }

    public ValueSet getMaritalStatusPropertyValueSet() {
        return maritalStatusPropertyValueSet;
    }

    public void setMaritalStatusPropertyValueSet(ValueSet maritalStatusPropertyValueSet) {
        this.maritalStatusPropertyValueSet = maritalStatusPropertyValueSet;
    }

    @BackendProperty(propertyName = "maritalStatusPropertyValueSet")
    public void parseMaritalStatusPropertyValueSet(String valueSetStr) {
        this.maritalStatusPropertyValueSet = parseValueSet(this.patientDetailsPropositionId, valueSetStr);
    }

    public String getMaritalStatusPropertyValueSetFile() {
        return maritalStatusPropertyValueSetFile;
    }

    @BackendProperty
    public void setMaritalStatusPropertyValueSetFile(String maritalStatusPropertyValueSetFile) {
        this.maritalStatusPropertyValueSetFile = maritalStatusPropertyValueSetFile;
    }

    public Boolean getMaritalStatusPropertyValueSetHasHeader() {
        return maritalStatusPropertyValueSetHasHeader;
    }

    @BackendProperty
    public void setMaritalStatusPropertyValueSetHasHeader(Boolean maritalStatusPropertyValueSetHasHeader) {
        if (maritalStatusPropertyValueSetHasHeader != null) {
            this.maritalStatusPropertyValueSetHasHeader = maritalStatusPropertyValueSetHasHeader;
        } else {
            this.maritalStatusPropertyValueSetHasHeader = false;
        }
    }

    public String getMaritalStatusPropertyValueSetDelimiter() {
        return Character.toString(maritalStatusPropertyValueSetDelimiter);
    }

    @BackendProperty
    public void setMaritalStatusPropertyValueSetDelimiter(String maritalStatusPropertyValueSetDelimiter) {
        if (maritalStatusPropertyValueSetDelimiter != null) {
            if (maritalStatusPropertyValueSetDelimiter.length() > 1) {
                throw new IllegalArgumentException("delimiter can only be one character");
            }
            if (maritalStatusPropertyValueSetDelimiter.length() < 1) {
                throw new IllegalArgumentException("delimiter must be at least one character");
            }
        }
        if (maritalStatusPropertyValueSetDelimiter != null) {
            this.maritalStatusPropertyValueSetDelimiter = maritalStatusPropertyValueSetDelimiter.charAt(0);
        } else {
            this.maritalStatusPropertyValueSetDelimiter = DEFAULT_DELIMITER;
        }
    }

    public Integer getMaritalStatusPropertyValueSetIdColumn() {
        return maritalStatusPropertyValueSetIdColumn;
    }

    @BackendProperty
    public void setMaritalStatusPropertyValueSetIdColumn(Integer maritalStatusPropertyValueSetIdColumn) {
        if (maritalStatusPropertyValueSetIdColumn == null) {
            throw new IllegalArgumentException("maritalStatusPropertyValueSetIdColumn cannot be null");
        }
        this.maritalStatusPropertyValueSetIdColumn = maritalStatusPropertyValueSetIdColumn;
    }

    public Integer getMaritalStatusPropertyValueSetDisplayNameColumn() {
        return maritalStatusPropertyValueSetDisplayNameColumn;
    }

    @BackendProperty
    public void setMaritalStatusPropertyValueSetDisplayNameColumn(Integer maritalStatusPropertyValueSetDisplayNameColumn) {
        this.maritalStatusPropertyValueSetDisplayNameColumn = maritalStatusPropertyValueSetDisplayNameColumn;
    }

    public Integer getMaritalStatusPropertyValueSetDescriptionColumn() {
        return maritalStatusPropertyValueSetDescriptionColumn;
    }

    @BackendProperty
    public void setMaritalStatusPropertyValueSetDescriptionColumn(Integer maritalStatusPropertyValueSetDescriptionColumn) {
        this.maritalStatusPropertyValueSetDescriptionColumn = maritalStatusPropertyValueSetDescriptionColumn;
    }

    public String getMaritalStatusPropertySource() {
        return maritalStatusPropertySource;
    }

    @BackendProperty
    public void setMaritalStatusPropertySource(String maritalStatusPropertySource) {
        this.maritalStatusPropertySource = maritalStatusPropertySource;
    }

    public String getMaritalStatusPropertyName() {
        return maritalStatusPropertyName;
    }

    @BackendProperty
    public void setMaritalStatusPropertyName(String maritalStatusPropertyName) {
        this.maritalStatusPropertyName = maritalStatusPropertyName;
    }

    public String getReligionPropertyName() {
        return religionPropertyName;
    }

    @BackendProperty
    public void setReligionPropertyName(String religionPropertyName) {
        this.religionPropertyName = religionPropertyName;
    }

    public ValueSet getReligionPropertyValueSet() {
        return religionPropertyValueSet;
    }

    public void setReligionPropertyValueSet(ValueSet religionPropertyValueSet) {
        this.religionPropertyValueSet = religionPropertyValueSet;
    }

    @BackendProperty(propertyName = "religionPropertyValueSet")
    public void parseReligionPropertyValueSet(String valueSetStr) {
        this.religionPropertyValueSet = parseValueSet(this.patientDetailsPropositionId, valueSetStr);
    }

    public String getReligionPropertyValueSetFile() {
        return religionPropertyValueSetFile;
    }

    @BackendProperty
    public void setReligionPropertyValueSetFile(String religionPropertyValueSetFile) {
        this.religionPropertyValueSetFile = religionPropertyValueSetFile;
    }

    public Boolean getReligionPropertyValueSetHasHeader() {
        return religionPropertyValueSetHasHeader;
    }

    @BackendProperty
    public void setReligionPropertyValueSetHasHeader(Boolean religionPropertyValueSetHasHeader) {
        if (religionPropertyValueSetHasHeader != null) {
            this.religionPropertyValueSetHasHeader = religionPropertyValueSetHasHeader;
        } else {
            this.religionPropertyValueSetHasHeader = false;
        }
    }

    public String getReligionPropertyValueSetDelimiter() {
        return Character.toString(religionPropertyValueSetDelimiter);
    }

    @BackendProperty
    public void setReligionPropertyValueSetDelimiter(String religionPropertyValueSetDelimiter) {
        if (religionPropertyValueSetDelimiter != null) {
            if (religionPropertyValueSetDelimiter.length() > 1) {
                throw new IllegalArgumentException("delimiter can only be one character");
            }
            if (religionPropertyValueSetDelimiter.length() < 1) {
                throw new IllegalArgumentException("delimiter must be at least one character");
            }
        }
        if (religionPropertyValueSetDelimiter != null) {
            this.religionPropertyValueSetDelimiter = religionPropertyValueSetDelimiter.charAt(0);
        } else {
            this.religionPropertyValueSetDelimiter = DEFAULT_DELIMITER;
        }
    }

    public Integer getReligionPropertyValueSetIdColumn() {
        return religionPropertyValueSetIdColumn;
    }

    @BackendProperty
    public void setReligionPropertyValueSetIdColumn(Integer religionPropertyValueSetIdColumn) {
        if (religionPropertyValueSetIdColumn == null) {
            throw new IllegalArgumentException("religionPropertyValueSetIdColumn cannot be null");
        }
        this.religionPropertyValueSetIdColumn = religionPropertyValueSetIdColumn;
    }

    public Integer getReligionPropertyValueSetDisplayNameColumn() {
        return religionPropertyValueSetDisplayNameColumn;
    }

    @BackendProperty
    public void setReligionPropertyValueSetDisplayNameColumn(Integer religionPropertyValueSetDisplayNameColumn) {
        this.religionPropertyValueSetDisplayNameColumn = religionPropertyValueSetDisplayNameColumn;
    }

    public Integer getReligionPropertyValueSetDescriptionColumn() {
        return religionPropertyValueSetDescriptionColumn;
    }

    @BackendProperty
    public void setReligionPropertyValueSetDescriptionColumn(Integer religionPropertyValueSetDescriptionColumn) {
        this.religionPropertyValueSetDescriptionColumn = religionPropertyValueSetDescriptionColumn;
    }

    public String getReligionPropertySource() {
        return religionPropertySource;
    }

    @BackendProperty
    public void setReligionPropertySource(String religionPropertySource) {
        this.religionPropertySource = religionPropertySource;
    }

    public String getDateOfBirthPropertyName() {
        return dateOfBirthPropertyName;
    }

    @BackendProperty
    public void setDateOfBirthPropertyName(String dateOfBirthPropertyName) {
        this.dateOfBirthPropertyName = dateOfBirthPropertyName;
    }

    public String getAgeInYearsPropertyName() {
        return ageInYearsPropertyName;
    }

    @BackendProperty
    public void setAgeInYearsPropertyName(String ageInYearsPropertyName) {
        this.ageInYearsPropertyName = ageInYearsPropertyName;
    }

    public String getDateOfDeathPropertyName() {
        return dateOfDeathPropertyName;
    }

    @BackendProperty
    public void setDateOfDeathPropertyName(String dateOfDeathPropertyName) {
        this.dateOfDeathPropertyName = dateOfDeathPropertyName;
    }

    public String getVitalStatusPropertyName() {
        return vitalStatusPropertyName;
    }

    @BackendProperty
    public void setVitalStatusPropertyName(String vitalStatusPropertyName) {
        this.vitalStatusPropertyName = vitalStatusPropertyName;
    }

    @Override
    public PropositionDefinition readPropositionDefinition(String id) throws KnowledgeSourceReadException {
        List<PropositionDefinition> result = readPropositionDefinitions(new String[]{id});
        if (result.isEmpty()) {
            return null;
        } else {
            return result.get(0);
        }
    }

    @Override
    public List<PropositionDefinition> readPropositionDefinitions(String[] ids) throws KnowledgeSourceReadException {
        List<PropositionDefinition> results = new ArrayList<>(ids != null ? ids.length : 0);
        if (ids != null) {
            List<String> propIdsToFind = readHardCodedPropDefs(ids, results);
            results.addAll(readPropDefs(propIdsToFind));
        }
        return results;
    }

    private List<String> readHardCodedPropDefs(String[] ids, List<PropositionDefinition> results) throws KnowledgeSourceReadException {
        List<String> propIdsToFind = new ArrayList<>(ids.length);
        for (String id : ids) {
            if (id.equals(this.patientDetailsPropositionId)) {
                results.add(newPatientDetailsPropositionDefinition());
            } else if (id.equals(this.patientPropositionId)) {
                results.add(newPatientPropositionDefinition());
            } else if (id.equals(this.visitPropositionId)) {
                results.add(newVisitPropositionDefinition());
            } else if (id.equals(this.providerPropositionId)) {
                results.add(newProviderPropositionDefinition());
            } else if (id.equals(this.patientAliasPropositionId)) {
                results.add(newPatientAliasPropositionDefinition());
            } else {
                propIdsToFind.add(id);
            }
        }
        return propIdsToFind;
    }

    @Override
    public String[] readIsA(String propId) throws KnowledgeSourceReadException {
        Set<String> parents = this.querySupport.getLevelReader().readParentsFromDatabase(propId);
        return parents.toArray(new String[parents.size()]);
    }

    private final QueryConstructor SEARCH_QUERY_CONSTRUCTOR = new QueryConstructor() {

        @Override
        public void appendStatement(StringBuilder sql, String table) {
            sql.append("SELECT ").append(querySupport.getEurekaIdColumn()).append(" FROM ");
            sql.append(table);
            sql.append(" WHERE UPPER(C_NAME) LIKE UPPER(?) AND C_BASECODE IS NOT NULL");

        }
    };

    private static final ResultSetReader<Set<String>> SEARCH_RESULT_SET_READER = new ResultSetReader<Set<String>>() {

        @Override
        public Set<String> read(ResultSet rs) throws KnowledgeSourceReadException {
            try {
                Set<String> result = new HashSet<>();
                if (rs != null) {
                    while (rs.next()) {
                        result.add(rs.getString(1));
                    }
                }
                return result;
            } catch (SQLException ex) {
                throw new KnowledgeSourceReadException(ex);
            }
        }

    };

    @Override
    public Set<String> getKnowledgeSourceSearchResults(String searchKey) throws KnowledgeSourceReadException {
        try (ConnectionSpecQueryExecutor queryExecutor = this.querySupport.getQueryExecutorInstance(SEARCH_QUERY_CONSTRUCTOR)) {
            return queryExecutor.execute(
                    "%" + I2B2Util.escapeLike(searchKey) + "%",
                    SEARCH_RESULT_SET_READER
            );
        }
    }

    @Override
    public void initialize(BackendInstanceSpec config) throws BackendInitializationException {
        super.initialize(config);
        try {
            this.querySupport = this.querySupportBuilder.build();
        } catch (InvalidConnectionSpecArguments | SQLException ex) {
            throw new KnowledgeSourceBackendInitializationException(ex);
        }
    }

    private ConstantDefinition newProviderPropositionDefinition() {
        Date now = new Date();
        ConstantDefinition providerDim = new ConstantDefinition(this.providerPropositionId);
        providerDim.setDisplayName(this.providerPropositionId);
        providerDim.setAccessed(now);
        providerDim.setCreated(DIMENSION_PROP_DEFS_CREATED_DATE);
        providerDim.setSourceId(this.sourceIdFactory.getInstance());
        providerDim.setInDataSource(true);
        providerDim.setPropertyDefinitions(
                new PropertyDefinition(this.providerPropositionId, this.providerNamePropertyName, null, ValueType.NOMINALVALUE, null, this.providerPropositionId));
        return providerDim;
    }

    private static class Root {

        private final String tableCode;
        private final String symbol;
        private final String displayName;

        Root(String tableCode, String displayName, String symbol) {
            assert tableCode != null : "fullName cannot be null";
            assert displayName != null : "displayName cannot be null";
            assert symbol != null : "symbol cannot be null";
            this.tableCode = tableCode;
            this.displayName = displayName;
            this.symbol = symbol;
        }

        public String getTableCode() {
            return tableCode;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getSymbol() {
            return symbol;
        }

    }

    private final QueryConstructor ROOT_QUERY_CONSTRUCTOR = new QueryConstructor() {

        @Override
        public void appendStatement(StringBuilder sql, String table) {
            sql.append("SELECT A2.C_TABLE_CD, A1.C_NAME, A1.").append(querySupport.getEurekaIdColumn()).append(" FROM ");
            sql.append(table);
            sql.append(" A1 JOIN TABLE_ACCESS A2 ON (A1.C_FULLNAME=A2.C_FULLNAME) WHERE A2.C_TABLE_NAME='");
            sql.append(table);
            sql.append("'");
        }
    };

    private static final ResultSetReader<List<Root>> ROOT_RESULT_SET_READER = new ResultSetReader<List<Root>>() {

        @Override
        public List<Root> read(ResultSet rs) throws KnowledgeSourceReadException {
            List<Root> result = new ArrayList<>();
            if (rs != null) {
                try {
                    while (rs.next()) {
                        result.add(new Root(rs.getString(1), rs.getString(2), rs.getString(3)));
                    }

                } catch (SQLException ex) {
                    throw new KnowledgeSourceReadException(ex);
                }
            }
            return result;
        }

    };

    private EventDefinition newVisitPropositionDefinition() throws KnowledgeSourceReadException {
        Date now = new Date();
        EventDefinition visitDim = new EventDefinition(this.visitPropositionId);
        visitDim.setDisplayName(this.visitPropositionId);
        visitDim.setAccessed(now);
        visitDim.setCreated(DIMENSION_PROP_DEFS_CREATED_DATE);
        visitDim.setSourceId(this.sourceIdFactory.getInstance());
        visitDim.setInDataSource(true);
        List<PropertyDefinition> visitDimPropertyDefs = new ArrayList<>();
        visitDimPropertyDefs.add(new PropertyDefinition(this.visitPropositionId, this.visitAgePropertyName, null, ValueType.NUMERICALVALUE, null, this.visitPropositionId,
                new Attribute[]{
                    new Attribute(DeidAttributes.IS_HIPAA_IDENTIFIER, BooleanValue.TRUE),
                    new Attribute(DeidAttributes.HIPAA_IDENTIFIER_TYPE, DeidAttributes.AGE)
                }));
        visitDimPropertyDefs.add(new PropertyDefinition(this.visitPropositionId, this.inoutPropertyName, null, ValueType.NOMINALVALUE, this.inoutPropertyValueSet.getId(), this.visitPropositionId));
        visitDimPropertyDefs.add(new PropertyDefinition(this.visitPropositionId, this.visitIdPropertyName, null, ValueType.NOMINALVALUE, null, this.visitPropositionId,
                new Attribute[]{
                    new Attribute(DeidAttributes.IS_HIPAA_IDENTIFIER, BooleanValue.TRUE),
                    new Attribute(DeidAttributes.HIPAA_IDENTIFIER_TYPE, DeidAttributes.OTHER)
                }));
        visitDim.setPropertyDefinitions(visitDimPropertyDefs.toArray(new PropertyDefinition[visitDimPropertyDefs.size()]));
        List<ReferenceDefinition> refDefs = new ArrayList<>();
        refDefs.add(new ReferenceDefinition("provider", "Provider", new String[]{this.providerPropositionId}));
        refDefs.add(new ReferenceDefinition("patientDetails", "Patient Details", new String[]{this.patientDetailsPropositionId}));
        try (ConnectionSpecQueryExecutor queryExecutor = this.querySupport.getQueryExecutorInstance(ROOT_QUERY_CONSTRUCTOR)) {
            for (Root root : queryExecutor.execute(ROOT_RESULT_SET_READER)) {
                refDefs.add(new ReferenceDefinition(root.getTableCode(), root.getDisplayName(), new String[]{root.getSymbol()}));
            }
        }
        visitDim.setReferenceDefinitions(refDefs.toArray(new ReferenceDefinition[refDefs.size()]));
        return visitDim;
    }

    private ConstantDefinition newPatientPropositionDefinition() {
        Date now = new Date();
        ConstantDefinition patientDim = new ConstantDefinition(this.patientPropositionId);
        patientDim.setAccessed(now);
        patientDim.setSourceId(this.sourceIdFactory.getInstance());
        patientDim.setCreated(DIMENSION_PROP_DEFS_CREATED_DATE);
        patientDim.setDisplayName(this.patientDisplayName);
        patientDim.setInDataSource(true);
        patientDim.setPropertyDefinitions(
                new PropertyDefinition(this.patientPropositionId, this.patientPatientIdPropertyName, null, ValueType.NOMINALVALUE, null, this.patientPropositionId)
        );
        patientDim.setReferenceDefinitions(
                new ReferenceDefinition("patientDetails", "Patient Details", new String[]{this.patientDetailsPropositionId}),
                new ReferenceDefinition("patientAliases", "Patient Aliases", new String[]{this.patientAliasPropositionId})
        );
        return patientDim;
    }

    private ConstantDefinition newPatientAliasPropositionDefinition() {
        Date now = new Date();
        ConstantDefinition patientDim = new ConstantDefinition(this.patientAliasPropositionId);
        patientDim.setAccessed(now);
        patientDim.setSourceId(this.sourceIdFactory.getInstance());
        patientDim.setCreated(DIMENSION_PROP_DEFS_CREATED_DATE);
        patientDim.setDisplayName(this.patientAliasDisplayName);
        patientDim.setInDataSource(true);
        patientDim.setPropertyDefinitions(
                new PropertyDefinition(this.patientAliasPropositionId, this.patientAliasPatientIdPropertyName, null, ValueType.NOMINALVALUE, null, this.patientAliasPropositionId,
                        new Attribute[]{
                            new Attribute(DeidAttributes.IS_HIPAA_IDENTIFIER, BooleanValue.TRUE),
                            new Attribute(DeidAttributes.HIPAA_IDENTIFIER_TYPE, DeidAttributes.MRN)
                        }),
                new PropertyDefinition(this.patientAliasPropositionId, this.patientAliasFieldNamePropertyName, null, ValueType.NOMINALVALUE, null, this.patientAliasPropositionId)
        );
        return patientDim;
    }

    private ConstantDefinition newPatientDetailsPropositionDefinition() {
        Date now = new Date();
        ConstantDefinition patientDim = new ConstantDefinition(this.patientDetailsPropositionId);
        patientDim.setAccessed(now);
        patientDim.setSourceId(this.sourceIdFactory.getInstance());
        patientDim.setCreated(DIMENSION_PROP_DEFS_CREATED_DATE);
        patientDim.setDisplayName(this.patientDetailsDisplayName);
        patientDim.setInDataSource(true);
        patientDim.setPropertyDefinitions(
                new PropertyDefinition(this.patientDetailsPropositionId, this.ageInYearsPropertyName, null, ValueType.NUMERICALVALUE, null, this.patientDetailsPropositionId,
                        new Attribute[]{
                            new Attribute(DeidAttributes.IS_HIPAA_IDENTIFIER, BooleanValue.TRUE),
                            new Attribute(DeidAttributes.HIPAA_IDENTIFIER_TYPE, DeidAttributes.AGE)
                        }),
                new PropertyDefinition(this.patientDetailsPropositionId, this.dateOfBirthPropertyName, null, ValueType.DATEVALUE, null, this.patientDetailsPropositionId,
                        new Attribute[]{
                            new Attribute(DeidAttributes.IS_HIPAA_IDENTIFIER, BooleanValue.TRUE),
                            new Attribute(DeidAttributes.HIPAA_IDENTIFIER_TYPE, DeidAttributes.BIRTHDATE)
                        }),
                new PropertyDefinition(this.patientDetailsPropositionId, this.dateOfDeathPropertyName, null, ValueType.DATEVALUE, null, this.patientDetailsPropositionId,
                        new Attribute[]{
                            new Attribute(DeidAttributes.IS_HIPAA_IDENTIFIER, BooleanValue.TRUE),
                            new Attribute(DeidAttributes.HIPAA_IDENTIFIER_TYPE, DeidAttributes.BIRTHDATE)
                        }),
                new PropertyDefinition(this.patientDetailsPropositionId, this.genderPropertyName, null, ValueType.NOMINALVALUE, this.genderPropertyValueSet.getId(), this.patientDetailsPropositionId),
                new PropertyDefinition(this.patientDetailsPropositionId, this.languagePropertyName, null, ValueType.NOMINALVALUE, this.languagePropertyValueSet.getId(), this.patientDetailsPropositionId),
                new PropertyDefinition(this.patientDetailsPropositionId, this.maritalStatusPropertyName, null, ValueType.NOMINALVALUE, this.maritalStatusPropertyValueSet.getId(), this.patientDetailsPropositionId),
                new PropertyDefinition(this.patientDetailsPropositionId, this.racePropertyName, null, ValueType.NOMINALVALUE, this.racePropertyValueSet.getId(), this.patientDetailsPropositionId),
                new PropertyDefinition(this.patientDetailsPropositionId, this.vitalStatusPropertyName, null, ValueType.BOOLEANVALUE, null, this.patientDetailsPropositionId),
                new PropertyDefinition(this.patientDetailsPropositionId, this.patientDetailsPatientIdPropertyName, null, ValueType.NOMINALVALUE, null, this.patientDetailsPropositionId,
                        new Attribute[]{
                            new Attribute(DeidAttributes.IS_HIPAA_IDENTIFIER, BooleanValue.TRUE),
                            new Attribute(DeidAttributes.HIPAA_IDENTIFIER_TYPE, DeidAttributes.MRN)
                        }
                )
        );
        patientDim.setReferenceDefinitions(
                new ReferenceDefinition("encounters", "Encounters", new String[]{this.visitPropositionId})
        );
        return patientDim;
    }

    @Override
    public AbstractionDefinition readAbstractionDefinition(String id) throws KnowledgeSourceReadException {
        return null;
    }

    @Override
    public List<AbstractionDefinition> readAbstractionDefinitions(String[] ids) throws KnowledgeSourceReadException {
        return java.util.Collections.emptyList();
    }

    @Override
    public ContextDefinition readContextDefinition(String id) throws KnowledgeSourceReadException {
        return null;
    }

    @Override
    public List<ContextDefinition> readContextDefinitions(String[] toArray) throws KnowledgeSourceReadException {
        return java.util.Collections.emptyList();
    }

    @Override
    public TemporalPropositionDefinition readTemporalPropositionDefinition(String id) throws KnowledgeSourceReadException {
        List<TemporalPropositionDefinition> result = readTemporalPropositionDefinitions(new String[]{id});
        if (result.isEmpty()) {
            return null;
        } else {
            return result.get(0);
        }
    }

    @Override
    public List<TemporalPropositionDefinition> readTemporalPropositionDefinitions(String[] ids) throws KnowledgeSourceReadException {
        List<TemporalPropositionDefinition> results = new ArrayList<>(ids != null ? ids.length : 0);
        if (ids != null) {
            List<String> propIdsToFind = readHardCodedTempPropDefs(ids, results);
            results.addAll(readPropDefs(propIdsToFind));
        }
        return results;
    }

    private List<String> readHardCodedTempPropDefs(String[] ids, List<TemporalPropositionDefinition> results) throws KnowledgeSourceReadException {
        List<String> propIdsToFind = new ArrayList<>(ids.length);
        for (String id : ids) {
            if (id.equals(this.visitPropositionId)) {
                results.add(newVisitPropositionDefinition());
            } else {
                propIdsToFind.add(id);
            }
        }
        return propIdsToFind;
    }

    @Override
    public String[] readAbstractedInto(String propId) throws KnowledgeSourceReadException {
        return ArrayUtils.EMPTY_STRING_ARRAY;
    }

    @Override
    public String[] readInduces(String propId) throws KnowledgeSourceReadException {
        return ArrayUtils.EMPTY_STRING_ARRAY;
    }

    @Override
    public String[] readSubContextOfs(String propId) throws KnowledgeSourceReadException {
        return ArrayUtils.EMPTY_STRING_ARRAY;
    }

    @Override
    public void close() {
        super.close();
        synchronized (this.modInterpCachedSyncVariable) {
            this.modInterpCached = null;
        }
    }

    private final QueryConstructor READ_ONE_PROPERTY_DEF_QUERY_CONSTRUCTOR = (StringBuilder sql, String table) -> {
        sql.append("(SELECT C_BASECODE, C_METADATAXML FROM ");
        sql.append(table);
        sql.append(" WHERE ").append(querySupport.getEurekaIdColumn()).append(" = ? AND C_SYNONYM_CD ='N' AND M_APPLIED_PATH<>'@' AND C_BASECODE IS NOT NULL AND C_METADATAXML IS NOT NULL)");
        sql.append(" UNION ALL ");
        sql.append("(SELECT C_BASECODE, NULL FROM (SELECT DISTINCT C_BASECODE FROM EK_MODIFIER_INTERP A2 WHERE PROPERTYNAME = ? AND NOT EXISTS (SELECT 1 FROM ");
        sql.append(table);
        sql.append(" WHERE ").append(querySupport.getEurekaIdColumn()).append(" = ? AND C_SYNONYM_CD ='N' AND M_APPLIED_PATH<>'@' AND C_BASECODE IS NOT NULL AND C_METADATAXML IS NOT NULL)) U2)");
    };

    @Override
    public ValueSet readValueSet(final String id) throws KnowledgeSourceReadException {
        ValueSet result = this.valueSets.get(id);
        if (result != null) {
            return result;
        }
        try (Connection connection = this.querySupport.getConnection()) {
            ValueMetadataParser valueMetadataParser = new ValueMetadataParser();
            valueMetadataParser.init();
            valueMetadataParser.parseValueSetId(id);
            try (QueryExecutor queryExecutor = this.querySupport.getQueryExecutorInstanceRestrictByEkUniqueIds(connection, READ_ONE_PROPERTY_DEF_QUERY_CONSTRUCTOR, valueMetadataParser.getDeclaringPropId())) {
                return queryExecutor.execute(
                        (PreparedStatement stmt, int j) -> {
                            stmt.setString(j++, valueMetadataParser.getConceptBaseCode());
                            stmt.setString(j++, valueMetadataParser.getConceptBaseCode());
                            stmt.setString(j++, valueMetadataParser.getConceptBaseCode());
                            return j;
                        },
                        (ResultSet rs) -> {
                            try {
                                if (rs != null && rs.next()) {
                                    String clob = rs.getString(2);
                                    if (clob != null) {
                                        valueMetadataParser.parse(clob);
                                        return valueMetadataParser.getValueSet();
                                    } else {
                                        List<String> elts = new ArrayList<>();
                                        elts.add(rs.getString(1));
                                        while (rs.next()) {
                                            elts.add(rs.getString(1));
                                        }
                                        return new PropertyDefinitionFactory().getValueSetInstance(id, elts);
                                    }
                                } else {
                                    return null;
                                }
                            } catch (SQLException | SAXParseException ex) {
                                throw new KnowledgeSourceReadException(ex);
                            }
                        });
            }
        } catch (InvalidConnectionSpecArguments | SQLException | ParseException ex) {
            throw new KnowledgeSourceReadException(ex);
        }
    }

    private static final ResultSetReader<Collection<String>> IN_DS_RESULT_SET_READER = (ResultSet rs) -> {
        List<String> result = new ArrayList<>();
        try {
            if (rs != null) {
                while (rs.next()) {
                    result.add(rs.getString(1));
                }
            }
        } catch (SQLException ex) {
            throw new KnowledgeSourceReadException(ex);
        }
        return result;
    };

    private final QueryConstructor IDS_PROPID_QC = new QueryConstructor() {

        @Override
        public void appendStatement(StringBuilder sql, String table) {
            sql.append("SELECT DISTINCT A2.").append(querySupport.getEurekaIdColumn()).append(" FROM ");
            sql.append(table);
            sql.append(" A1 JOIN ");
            sql.append(table);
            sql.append(" A2 ON (A2.C_FULLNAME LIKE A1.C_FULLNAME || '%') WHERE A1.").append(querySupport.getEurekaIdColumn()).append(" = ? AND A1.C_SYNONYM_CD='N' AND A2.C_BASECODE IS NOT NULL");
        }

    };

    private final QueryConstructor IDS_PROPID_QC_POSTGRESQL = new QueryConstructor() {

        @Override
        public void appendStatement(StringBuilder sql, String table) {
            sql.append("SELECT DISTINCT A2.").append(querySupport.getEurekaIdColumn()).append(" FROM ");
            sql.append(table);
            sql.append(" A1 JOIN ");
            sql.append(table);
            sql.append(" A2 ON (A2.C_FULLNAME LIKE A1.C_FULLNAME || '%' ESCAPE '') WHERE A1.").append(querySupport.getEurekaIdColumn()).append(" = ? AND A1.C_SYNONYM_CD='N' AND A2.C_BASECODE IS NOT NULL");
        }

    };

    private final QueryConstructor N_PROPID_QC = new QueryConstructor() {

        @Override
        public void appendStatement(StringBuilder sql, String table) {
            sql.append("SELECT DISTINCT A2.").append(querySupport.getEurekaIdColumn()).append(" FROM ");
            sql.append(table);
            sql.append(" A1 JOIN ");
            sql.append(table);
            sql.append(" A2 ON (A2.C_FULLNAME LIKE A1.C_FULLNAME || '%') WHERE A1.").append(querySupport.getEurekaIdColumn()).append(" = ? AND A1.C_SYNONYM_CD='N'");
        }

    };

    private final QueryConstructor N_PROPID_QC_POSTGRESQL = new QueryConstructor() {

        @Override
        public void appendStatement(StringBuilder sql, String table) {
            sql.append("SELECT DISTINCT A2.").append(querySupport.getEurekaIdColumn()).append(" FROM ");
            sql.append(table);
            sql.append(" A1 JOIN ");
            sql.append(table);
            sql.append(" A2 ON (A2.C_FULLNAME LIKE A1.C_FULLNAME || '%' ESCAPE '') WHERE A1.").append(querySupport.getEurekaIdColumn()).append(" = ? AND A1.C_SYNONYM_CD='N'");
        }

    };

    @Override
    public Collection<String> collectPropIdDescendantsUsingAllNarrower(boolean inDataSourceOnly, final String[] propIds) throws KnowledgeSourceReadException {
        final Set<String> result = new HashSet<>(10000);
        if (propIds != null && propIds.length > 0) {
            TableAccessReader tableAccessReader = this.querySupport.getTableAccessReaderBuilder().restrictTablesBy(propIds).build();
            QueryConstructor qc;
            if (inDataSourceOnly) {
                switch (this.querySupport.getDatabaseProduct()) {
                    case POSTGRESQL:
                        qc = IDS_PROPID_QC_POSTGRESQL;
                        break;
                    default:
                        qc = IDS_PROPID_QC;
                }
            } else {
                switch (this.querySupport.getDatabaseProduct()) {
                    case POSTGRESQL:
                        qc = N_PROPID_QC_POSTGRESQL;
                        break;
                    default:
                        qc = N_PROPID_QC;
                }
            }
            try (ConnectionSpecQueryExecutor queryExecutor = this.querySupport.getQueryExecutorInstance(qc, tableAccessReader)) {
                queryExecutor.prepare();
                for (String propId : filterPropId(propIds, result)) {
                    result.addAll(queryExecutor.execute(
                            propId,
                            IN_DS_RESULT_SET_READER));
                }
            }
        }
        return result;
    }

    private Set<String> filterPropId(String[] propIds, final Collection<String> result) {
        Set<String> propIdsAsSet = Arrays.asSet(propIds);
        for (Iterator<String> itr = propIdsAsSet.iterator(); itr.hasNext();) {
            String propId = itr.next();
            if (patientPropositionId.equals(propId)) {
                result.add(propId);
                itr.remove();
            } else if (patientDetailsPropositionId.equals(propId)) {
                result.add(propId);
                itr.remove();
            } else if (visitPropositionId.equals(propId)) {
                result.add(propId);
                itr.remove();
            } else if (providerPropositionId.equals(propId)) {
                result.add(propId);
                itr.remove();
            }
        }
        return propIdsAsSet;
    }

    private Set<String> filterPropDef(String[] propIds, Map<String, PropositionDefinition> result) throws KnowledgeSourceReadException {
        Set<String> propIdsAsSet = Arrays.asSet(propIds);
        for (Iterator<String> itr = propIdsAsSet.iterator(); itr.hasNext();) {
            String propId = itr.next();
            if (patientDetailsPropositionId.equals(propId)) {
                result.put(propId, newPatientDetailsPropositionDefinition());
                itr.remove();
            } else if (patientPropositionId.equals(propId)) {
                result.put(propId, newPatientPropositionDefinition());
                itr.remove();
            } else if (visitPropositionId.equals(propId)) {
                result.put(propId, newVisitPropositionDefinition());
                itr.remove();
            } else if (providerPropositionId.equals(propId)) {
                result.put(propId, newProviderPropositionDefinition());
                itr.remove();
            } else if (patientAliasPropositionId.equals(propId)) {
                result.put(propId, newPatientAliasPropositionDefinition());
            }
        }
        return propIdsAsSet;
    }

    private final QueryConstructor COLLECT_SUBTREE_PROPID_QC = new QueryConstructor() {

        @Override
        public void appendStatement(StringBuilder sql, String table) {
            sql.append("SELECT DISTINCT A1.").append(querySupport.getEurekaIdColumn()).append(" FROM ");
            sql.append(table);
            sql.append(" A1 WHERE A1.C_FULLNAME LIKE (SELECT A2.C_FULLNAME || '%' FROM ");
            sql.append(table);
            sql.append(" A2 WHERE A2.").append(querySupport.getEurekaIdColumn()).append(" = ? AND A2.C_SYNONYM_CD='N' AND ROWNUM=1)");
        }

    };

    private final QueryConstructor COLLECT_SUBTREE_PROPID_QC_POSTGRESQL = new QueryConstructor() {

        @Override
        public void appendStatement(StringBuilder sql, String table) {
            sql.append("SELECT DISTINCT A1.").append(querySupport.getEurekaIdColumn()).append(" FROM ");
            sql.append(table);
            sql.append(" A1 WHERE A1.C_FULLNAME LIKE (SELECT A2.C_FULLNAME || '%' FROM ");
            sql.append(table);
            sql.append(" A2 WHERE A2.").append(querySupport.getEurekaIdColumn()).append(" = ? AND A2.C_SYNONYM_CD='N' AND ROWNUM=1)");
        }

    };

    @Override
    public Collection<String> collectPropIdDescendantsUsingInverseIsA(final String[] propIds) throws KnowledgeSourceReadException {
        final Set<String> result = new HashSet<>(10000);
        if (propIds != null && propIds.length > 0) {
            TableAccessReader tableAccessReader = this.querySupport.getTableAccessReaderBuilder().restrictTablesBy(propIds).build();
            try (ConnectionSpecQueryExecutor queryExecutor = this.querySupport.getQueryExecutorInstance(this.querySupport.getDatabaseProduct() == DatabaseProduct.POSTGRESQL ? COLLECT_SUBTREE_PROPID_QC_POSTGRESQL : COLLECT_SUBTREE_PROPID_QC, tableAccessReader)) {
                queryExecutor.prepare();
                for (String propId : filterPropId(propIds, result)) {
                    result.addAll(queryExecutor.execute(
                            propId,
                            IN_DS_RESULT_SET_READER));
                }
            }
        }
        return result;
    }

    private final QueryConstructor IDS_PROPDEF_QC_ROWNUM = new QueryConstructor() {

        @Override
        public void appendStatement(StringBuilder sql, String table) {
            sql.append("SELECT C_NAME, C_FULLNAME, VALUETYPE_CD, C_COMMENT, C_METADATAXML, CASE WHEN C_BASECODE IS NOT NULL THEN 1 ELSE 0 END, ").append(querySupport.getEurekaIdColumn()).append(", IMPORT_DATE, UPDATE_DATE, DOWNLOAD_DATE FROM ");
            sql.append(table);
            sql.append(" A1 WHERE EXISTS (SELECT 1 FROM ");
            sql.append(table);
            sql.append(" A2 JOIN EK_TEMP_UNIQUE_IDS A3 ON (A2.").append(querySupport.getEurekaIdColumn()).append(" = A3.UNIQUE_ID) WHERE A1.C_FULLNAME LIKE A2.C_FULLNAME || '%' AND C_SYNONYM_CD='N' AND C_BASECODE IS NOT NULL)");
        }

    };

    private final QueryConstructor IDS_PROPDEF_QC_POSTGRESQL = new QueryConstructor() {

        @Override
        public void appendStatement(StringBuilder sql, String table) {
            sql.append("SELECT C_NAME, C_FULLNAME, VALUETYPE_CD, C_COMMENT, C_METADATAXML, CASE WHEN C_BASECODE IS NOT NULL THEN 1 ELSE 0 END, ").append(querySupport.getEurekaIdColumn()).append(", IMPORT_DATE, UPDATE_DATE, DOWNLOAD_DATE FROM ");
            sql.append(table);
            sql.append(" A1 WHERE EXISTS (SELECT 1 ");
            sql.append(table);
            sql.append(" A2 JOIN EK_TEMP_UNIQUE_IDS A3 ON (A2.").append(querySupport.getEurekaIdColumn()).append(" = A3.UNIQUE_ID) WHERE A1.C_FULLNAME LIKE A2.C_FULLNAME || '%' ESCAPE '' AND C_SYNONYM_CD='N' AND C_BASECODE IS NOT NULL)");
        }

    };

    private final QueryConstructor N_PROPDEF_QC_ROWNUM = new QueryConstructor() {

        @Override
        public void appendStatement(StringBuilder sql, String table) {
            sql.append("SELECT C_NAME, C_FULLNAME, VALUETYPE_CD, C_COMMENT, C_METADATAXML, CASE WHEN C_BASECODE IS NOT NULL THEN 1 ELSE 0 END, ").append(querySupport.getEurekaIdColumn()).append(", IMPORT_DATE, UPDATE_DATE, DOWNLOAD_DATE FROM ");
            sql.append(table);
            sql.append(" A1 WHERE EXISTS (SELECT 1 FROM ");
            sql.append(table);
            sql.append(" A2 JOIN EK_TEMP_UNIQUE_IDS A3 ON (A2.").append(querySupport.getEurekaIdColumn()).append(" = A3.UNIQUE_ID) WHERE A1.C_FULLNAME LIKE A2.C_FULLNAME || '%' AND A2.C_SYNONYM_CD='N')");
        }

    };

    private final QueryConstructor N_PROPDEF_QC_POSTGRESQL = new QueryConstructor() {

        @Override
        public void appendStatement(StringBuilder sql, String table) {
            sql.append("SELECT C_NAME, C_FULLNAME, VALUETYPE_CD, C_COMMENT, C_METADATAXML, CASE WHEN C_BASECODE IS NOT NULL THEN 1 ELSE 0 END, ").append(querySupport.getEurekaIdColumn()).append(", IMPORT_DATE, UPDATE_DATE, DOWNLOAD_DATE FROM ");
            sql.append(table);
            sql.append(" A1 WHERE EXISTS (SELECT 1 FROM ");
            sql.append(table);
            sql.append(" A2 JOIN EK_TEMP_UNIQUE_IDS A3 ON (A2.").append(querySupport.getEurekaIdColumn()).append(" = A3.UNIQUE_ID) WHERE A1.C_FULLNAME LIKE A2.C_FULLNAME || '%' ESCAPE '' AND A2.C_SYNONYM_CD='N')");
        }

    };

    @Override
    public Collection<PropositionDefinition> collectPropDefDescendantsUsingAllNarrower(boolean inDataSourceOnly, final String[] propIds) throws KnowledgeSourceReadException {
        DatabaseProduct databaseProduct = this.querySupport.getDatabaseProduct();
        switch (databaseProduct) {
            case POSTGRESQL:
                return collectPropDefDescendantsCommon(propIds, inDataSourceOnly ? IDS_PROPDEF_QC_POSTGRESQL : N_PROPDEF_QC_POSTGRESQL);
            default:
                return collectPropDefDescendantsCommon(propIds, inDataSourceOnly ? IDS_PROPDEF_QC_ROWNUM : N_PROPDEF_QC_ROWNUM);
        }
    }

    private final QueryConstructor COLLECT_SUBTREE_PROPDEF_QC_ROWNUM = new QueryConstructor() {

        @Override
        public void appendStatement(StringBuilder sql, String table) {
            sql.append("SELECT A1.C_NAME, A1.C_FULLNAME, A1.VALUETYPE_CD, A1.C_COMMENT, A1.C_METADATAXML, CASE WHEN A1.C_BASECODE IS NOT NULL THEN 1 ELSE 0 END, A1.").append(querySupport.getEurekaIdColumn()).append(", A1.IMPORT_DATE, A1.UPDATE_DATE, A1.DOWNLOAD_DATE FROM ");
            sql.append(table);
            sql.append(" A1 WHERE EXISTS (SELECT 1 FROM ");
            sql.append(table);
            sql.append(" A2 JOIN EK_TEMP_UNIQUE_IDS A3 ON (A2.").append(querySupport.getEurekaIdColumn()).append(" = A3.UNIQUE_ID) WHERE A1.C_FULLNAME LIKE A2.C_FULLNAME || '%' AND A2.C_SYNONYM_CD='N')");
        }

    };

    private final QueryConstructor COLLECT_SUBTREE_PROPDEF_QC_POSTGRESQL = new QueryConstructor() {

        @Override
        public void appendStatement(StringBuilder sql, String table) {
            sql.append("SELECT A1.C_NAME, A1.C_FULLNAME, A1.VALUETYPE_CD, A1.C_COMMENT, A1.C_METADATAXML, CASE WHEN A1.C_BASECODE IS NOT NULL THEN 1 ELSE 0 END, A1.").append(querySupport.getEurekaIdColumn()).append(", A1.IMPORT_DATE, A1.UPDATE_DATE, A1.DOWNLOAD_DATE FROM ");
            sql.append(table);
            sql.append(" A1 WHERE EXISTS (SELECT 1 FROM ");
            sql.append(table);
            sql.append(" A2 JOIN EK_TEMP_UNIQUE_IDS A3 ON (A2.").append(querySupport.getEurekaIdColumn()).append(" = A3.UNIQUE_ID) WHERE A1.C_FULLNAME LIKE A2.C_FULLNAME || '%' ESCAPE '' AND A2.C_SYNONYM_CD='N')");
        }

    };

    @Override
    public Collection<PropositionDefinition> collectPropDefDescendantsUsingInverseIsA(final String[] propIds) throws KnowledgeSourceReadException {
        DatabaseProduct databaseProduct = this.querySupport.getDatabaseProduct();
        switch (databaseProduct) {
            case POSTGRESQL:
                return collectPropDefDescendantsCommon(propIds, COLLECT_SUBTREE_PROPDEF_QC_POSTGRESQL);
            default:
                return collectPropDefDescendantsCommon(propIds, COLLECT_SUBTREE_PROPDEF_QC_ROWNUM);
        }
    }

    private Collection<PropositionDefinition> collectPropDefDescendantsCommon(final String[] propIds, QueryConstructor queryConstructor) throws KnowledgeSourceReadException {
        Map<String, PropositionDefinition> result = Collections.newHashMap(10000);
        if (propIds != null && propIds.length > 0) {
            TableAccessReader tableAccessReader = this.querySupport.getTableAccessReaderBuilder().restrictTablesBy(propIds).build();
            try (Connection connection = this.querySupport.getConnection()) {
                try (UniqueIdTempTableHandler childTempTableHandler = new UniqueIdTempTableHandler(this.querySupport.getDatabaseProduct(), connection, false)) {
                    for (String child : filterPropDef(propIds, result)) {
                        childTempTableHandler.insert(child);
                    }
                }
                try (QueryExecutor queryExecutor = this.querySupport.getQueryExecutorInstance(connection, queryConstructor, tableAccessReader)) {
                    queryExecutor.prepare();
                    queryExecutor.execute((ResultSet rs) -> {
                        try {
                            if (rs != null) {
                                Date now = new Date();
                                ValueMetadataParser valueMetadataParser = new ValueMetadataParser();
                                while (rs.next()) {
                                    String propId = rs.getString(7);
                                    if (!result.containsKey(propId)) {
                                        result.put(propId, newTemporalPropositionDefinition(rs, now, valueMetadataParser));
                                    }
                                }
                            }
                            return null;
                        } catch (SQLException | SAXParseException ex) {
                            throw new KnowledgeSourceReadException(ex);
                        }
                    });
                }
                try {
                    connection.commit();
                } catch (SQLException ex) {
                    connection.rollback();
                    throw ex;
                }
            } catch (InvalidConnectionSpecArguments | SQLException ex) {
                throw new KnowledgeSourceReadException(ex);
            }

            populateChildren(result, tableAccessReader);
            populateProperties(result, tableAccessReader);
        }

        return result.values();
    }

    void populateProperties(Map<String, ? extends PropositionDefinition> propIdToPropDef, TableAccessReader tableAccessReader) throws KnowledgeSourceReadException {
        try (Connection connection = this.querySupport.getConnection()) {
            try {
                try (UniqueIdTempTableHandler childTempTableHandler = new UniqueIdTempTableHandler(this.querySupport.getDatabaseProduct(), connection, false)) {
                    for (String propId : propIdToPropDef.keySet()) {
                        childTempTableHandler.insert(propId);
                    }
                }
                doPopulateProperties(propIdToPropDef, connection, tableAccessReader);
                connection.commit();
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            }
        } catch (InvalidConnectionSpecArguments | SQLException ex) {
            throw new KnowledgeSourceReadException(ex);
        }
    }

    void populateProperties(Map<String, ? extends PropositionDefinition> propIdToPropDef, TableAccessReader tableAccessReader, Connection connection) throws KnowledgeSourceReadException {
        try {
            doPopulateProperties(propIdToPropDef, connection, tableAccessReader);
        } catch (SQLException ex) {
            throw new KnowledgeSourceReadException(ex);
        }
    }

    private void doPopulateProperties(Map<String, ? extends PropositionDefinition> propIdToPropDef, final Connection connection, TableAccessReader tableAccessReader) throws SQLException, KnowledgeSourceReadException {
        ResultSetReader<Map<PropositionDefinition, Map<String, PropertyDefinition>>> reader = (ResultSet rs) -> {
            Map<PropositionDefinition, Map<String, PropertyDefinition>> result = new HashMap<>();
            if (rs != null) {
                try {
                    PropertyDefinitionFactory propertyDefinitionFactory = new PropertyDefinitionFactory();
                    while (rs.next()) {
                        String declaringConceptSymbol = rs.getString(5);
                        String conceptSymbol = rs.getString(4);
                        String symbol = rs.getString(3);
                        String name = rs.getString(1); // the display name
                        if (name == null) {
                            throw new KnowledgeSourceReadException("Null property symbol for concept " + symbol);
                        }
                        String clob = rs.getString(6);
                        PropositionDefinition propDef = propIdToPropDef.get(conceptSymbol);
                        Map<String, PropertyDefinition> m = result.get(propDef);
                        if (m == null) {
                            m = new HashMap<>();
                            result.put(propDef, m);
                        }
                        if (!m.containsKey(symbol)) {
                            m.put(symbol, propertyDefinitionFactory.getPropertyDefinitionInstance(clob, declaringConceptSymbol, symbol, conceptSymbol, name));
                        }
                    }
                } catch (SQLException | SAXParseException ex) {
                    throw new KnowledgeSourceReadException(ex);
                }
            }
            return result;
        };
        PropertiesTempTableHandler.createTempTableIfNeeded(connection, this.querySupport.getDatabaseProduct());
        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM EK_TEMP_PROPERTIES")) {
            if (rs.next() && rs.getInt(1) == 0) {
                TableAccessReader localTableAccessReader = this.querySupport.getTableAccessReaderBuilder().build();
                for (String table : localTableAccessReader.read(connection)) {
                    /*
                     * Getting temp space full errors with one query, so split it into one query per metadata table.
                     */
                    PropertiesTempTableHandler childTempTableHandler = new PropertiesTempTableHandler(this.querySupport, connection, table, this.querySupport.getEurekaIdColumn());
                    childTempTableHandler.execute();
                }
            }
        }

        QueryConstructor qc;
        switch (this.querySupport.getDatabaseProduct()) {
            case ORACLE:
                qc = READ_ALL_PROPERTIES_CONSTRUCTOR_ORCL;
                break;
            case POSTGRESQL:
                qc = READ_ALL_PROPERTIES_CONSTRUCTOR_POSTGRESQL;
                break;
            default:
                qc = READ_ALL_PROPERTIES_CONSTRUCTOR;
        }
        for (String table : tableAccessReader.read(connection)) {
            try (QueryExecutor queryExecutor = this.querySupport.getQueryExecutorInstanceRestrictByTables(connection, qc, table)) {
                for (Map.Entry<PropositionDefinition, Map<String, PropertyDefinition>> me : queryExecutor.execute(reader).entrySet()) {
                    PropositionDefinition pd = me.getKey();
                    if (pd != null) {
                        Map<String, PropertyDefinition> value = me.getValue();
                        if (value != null) {
                            ((AbstractPropositionDefinition) pd).setPropertyDefinitions(value.values().toArray(new PropertyDefinition[value.size()]));
                        }
                    }
                }
            }
        }
    }

    void populateChildren(Map<String, ? extends PropositionDefinition> propIdToPropDef, TableAccessReader tableAccessReader) throws KnowledgeSourceReadException {
        this.querySupport.getLevelReader().readChildrenFromDatabase(propIdToPropDef, tableAccessReader, (PropositionDefinition pd, Set<String> children) -> {
            if (children != null && pd != null) {
                ((AbstractPropositionDefinition) pd).setInverseIsA(children.toArray(new String[children.size()]));
            }
        });
    }

    void populateChildren(Map<String, ? extends PropositionDefinition> propIdToPropDef, TableAccessReader tableAccessReader, Connection connection) throws KnowledgeSourceReadException {
        this.querySupport.getLevelReader().readChildrenFromDatabase(propIdToPropDef, tableAccessReader, (PropositionDefinition pd, Set<String> children) -> {
            if (children != null && pd != null) {
                ((AbstractPropositionDefinition) pd).setInverseIsA(children.toArray(new String[children.size()]));
            }
        }, connection);
    }

    private final QueryConstructor READ_ALL_PROPERTIES_CONSTRUCTOR = new QueryConstructor() {

        @Override
        public void appendStatement(StringBuilder sql, String table) {
            sql.append("SELECT A1.C_NAME, A1.VALUETYPE_CD, A1.PROPERTYNAME, A3.").append(querySupport.getEurekaIdColumn()).append(", A1.DECLARING_CONCEPT_ID, A3.C_METADATAXML FROM ");
            sql.append(table);
            sql.append(" A3 JOIN EK_TEMP_UNIQUE_IDS A4 ON (A3.").append(querySupport.getEurekaIdColumn()).append("=A4.UNIQUE_ID AND A3.C_SYNONYM_CD  ='N' AND A3.M_APPLIED_PATH='@') JOIN ");
            sql.append("EK_TEMP_PROPERTIES A1 ON (A3.C_FULLNAME LIKE A1.M_APPLIED_PATH)");
        }

    };

    private final QueryConstructor READ_ALL_PROPERTIES_CONSTRUCTOR_POSTGRESQL = new QueryConstructor() {

        @Override
        public void appendStatement(StringBuilder sql, String table) {
            sql.append("SELECT A1.C_NAME, A1.VALUETYPE_CD, A1.PROPERTYNAME, A3.").append(querySupport.getEurekaIdColumn()).append(", A1.DECLARING_CONCEPT_ID, A3.C_METADATAXML FROM ");
            sql.append(table);
            sql.append(" A3 JOIN EK_TEMP_UNIQUE_IDS A4 ON (A3.").append(querySupport.getEurekaIdColumn()).append("=A4.UNIQUE_ID AND A3.C_SYNONYM_CD  ='N' AND A3.M_APPLIED_PATH='@') JOIN ");
            sql.append("EK_TEMP_PROPERTIES A1 ON (A3.C_FULLNAME LIKE A1.M_APPLIED_PATH ESCAPE '')");
        }

    };

    private final QueryConstructor READ_ALL_PROPERTIES_CONSTRUCTOR_ORCL = new QueryConstructor() {

        @Override
        public void appendStatement(StringBuilder sql, String table) {
            sql.append("SELECT A1.C_NAME, A1.VALUETYPE_CD, A1.PROPERTYNAME, A3.").append(querySupport.getEurekaIdColumn()).append(", A1.DECLARING_CONCEPT_ID, A3.C_METADATAXML FROM ");
            sql.append(table);
            sql.append(" A3 JOIN EK_TEMP_UNIQUE_IDS A4 ON (A3.").append(querySupport.getEurekaIdColumn()).append("=A4.UNIQUE_ID AND A3.C_SYNONYM_CD  ='N' AND A3.M_APPLIED_PATH='@') JOIN ");
            sql.append("EK_TEMP_PROPERTIES A1 ON (A3.C_FULLNAME LIKE A1.M_APPLIED_PATH)");
        }

    };

    private TemporalPropositionDefinition newTemporalPropositionDefinition(ResultSet rs, Date accessed, ValueMetadataParser valueMetadataParser) throws SAXParseException, KnowledgeSourceReadException, SQLException {
        if (Arrays.contains(VALUE_TYPE_CDS, rs.getString(3))) {
            valueMetadataParser.init();
            String clob = rs.getString(4);
            valueMetadataParser.parse(clob);
            ValueType valueType = valueMetadataParser.getValueType();
            PrimitiveParameterDefinition result = new PrimitiveParameterDefinition(rs.getString(7));
            result.setDisplayName(rs.getString(1));
            result.setDescription(rs.getString(4));
            result.setInDataSource(rs.getBoolean(6));
            result.setValueType(valueType);
            result.setAccessed(accessed);
            result.setCreated(rs.getTimestamp(8));
            result.setUpdated(rs.getTimestamp(9));
            result.setDownloaded(rs.getTimestamp(10));
            result.setSourceId(this.sourceIdFactory.getInstance());
            return result;
        } else {
            EventDefinition result = new EventDefinition(rs.getString(7));
            result.setDisplayName(rs.getString(1));
            result.setDescription(rs.getString(4));
            result.setInDataSource(rs.getBoolean(6));
            result.setAccessed(accessed);
            result.setCreated(rs.getTimestamp(8));
            result.setUpdated(rs.getTimestamp(9));
            result.setDownloaded(rs.getTimestamp(10));
            result.setSourceId(this.sourceIdFactory.getInstance());
            return result;
        }
    }

    private final QueryConstructor READ_PROPDEFS_QUERY_CONSTRUCTOR = new QueryConstructor() {

        @Override
        public void appendStatement(StringBuilder sql, String table) {
            sql.append("SELECT C_NAME, C_FULLNAME, VALUETYPE_CD, C_COMMENT, C_METADATAXML, CASE WHEN C_BASECODE IS NOT NULL THEN 1 ELSE 0 END, ").append(querySupport.getEurekaIdColumn()).append(", IMPORT_DATE, UPDATE_DATE, DOWNLOAD_DATE FROM ");
            sql.append(table);
            sql.append(" JOIN EK_TEMP_UNIQUE_IDS ON UNIQUE_ID=").append(querySupport.getEurekaIdColumn()).append(" WHERE C_SYNONYM_CD='N'");
        }
    };

    private Collection<TemporalPropositionDefinition> readPropDefs(final List<String> ids) throws KnowledgeSourceReadException {
        Map<String, TemporalPropositionDefinition> resultMap = Collections.newHashMap(ids.size());
        TableAccessReader tableAccessReader = this.querySupport.getTableAccessReaderBuilder().restrictTablesBy(ids.toArray(new String[ids.size()])).build();
        try (Connection connection = this.querySupport.getConnection()) {
            try {
                try (UniqueIdTempTableHandler uniqIdTempTableHandler = new UniqueIdTempTableHandler(this.querySupport.getDatabaseProduct(), connection, false)) {
                    for (String id : ids) {
                        uniqIdTempTableHandler.insert(id);
                    }
                }

                try (QueryExecutor queryExecutor = this.querySupport.getQueryExecutorInstance(connection, READ_PROPDEFS_QUERY_CONSTRUCTOR, tableAccessReader)) {
                    queryExecutor.execute((ResultSet rs) -> {
                        try {
                            if (rs != null) {
                                ValueMetadataParser valueMetadataParser = new ValueMetadataParser();
                                Date accessed = new Date();
                                while (rs.next()) {
                                    TemporalPropositionDefinition abd = newTemporalPropositionDefinition(rs, accessed, valueMetadataParser);
                                    resultMap.put(abd.getId(), abd);
                                }
                            }
                            return null;
                        } catch (SQLException | SAXParseException ex) {
                            throw new KnowledgeSourceReadException(ex);
                        }
                    });
                }
                connection.commit();
            } catch (SQLException ex) {
                try {
                    connection.rollback();
                } catch (SQLException ignore) {
                }
                throw ex;
            }
        } catch (SQLException | InvalidConnectionSpecArguments ex) {
            throw new KnowledgeSourceReadException(ex);
        }
        if (!resultMap.isEmpty()) {
            try (Connection connection = this.querySupport.getConnection()) {
                try {
                    try (UniqueIdTempTableHandler uniqIdTempTableHandler = new UniqueIdTempTableHandler(this.querySupport.getDatabaseProduct(), connection, false)) {
                        for (PropositionDefinition pd : resultMap.values()) {
                            uniqIdTempTableHandler.insert(pd.getId());
                        }
                    }
                    populateChildren(resultMap, tableAccessReader, connection);
                    populateProperties(resultMap, tableAccessReader, connection);
                    connection.commit();
                } catch (SQLException ex) {
                    try {
                        connection.rollback();
                    } catch (SQLException ignore) {
                    }
                    throw ex;
                }
            } catch (SQLException | InvalidConnectionSpecArguments ex) {
                throw new KnowledgeSourceReadException(ex);
            }
        }

        return resultMap.values();
    }

    private static ValueSet parseValueSet(String valueSetId, String vses) {
        try (Reader reader = new StringReader(vses);
                CSVReader r = new CSVReader(reader, ',')) {
            String[] readNext = r.readNext();
            ValueSetElement[] vsesArr = new ValueSetElement[readNext.length];
            for (int i = 0; i < readNext.length; i++) {
                try (Reader reader2 = new StringReader(readNext[i]);
                        CSVReader r2 = new CSVReader(reader2, '|')) {
                    String[] segments = r2.readNext();
                    vsesArr[i] = new ValueSetElement(NominalValue.getInstance(segments[0]), segments[1], null);
                }
            }
            return new ValueSet(valueSetId, null, vsesArr, null);
        } catch (IOException ignore) {
            throw new AssertionError("Should never happen");
        }
    }

    private static ValueSet parseValueSetResource(String valueSetId, String name, String hasHeader, String delimiter, String idCol, String displayNameCol, String descriptionCol) {
        boolean header = Boolean.parseBoolean(hasHeader);
        int id = Integer.parseInt(idCol) - 1;
        int displayName;
        if (displayNameCol != null) {
            displayName = Integer.parseInt(displayNameCol) - 1;
        } else {
            displayName = -1;
        }
        int description;
        if (descriptionCol != null) {
            description = Integer.parseInt(descriptionCol) - 1;
        } else {
            description = -1;
        }
        int maxIndex = Math.max(id, displayName);
        maxIndex = Math.max(maxIndex, description);
        try (Reader reader = new InputStreamReader(I2b2KnowledgeSourceBackend.class.getResourceAsStream(name));
                CSVReader r = new CSVReader(reader, delimiter.charAt(0))) {
            String[] cols;
            List<ValueSetElement> vsel = new ArrayList<>();
            boolean first = header;
            while ((cols = r.readNext()) != null) {
                if (first) {
                    first = false;
                } else if (cols.length - 1 >= maxIndex) {
                    vsel.add(new ValueSetElement(NominalValue.getInstance(cols[id]), displayName > -1 ? cols[displayName] : null, null));
                }
            }
            return new ValueSet(valueSetId, null, vsel.toArray(new ValueSetElement[vsel.size()]), null);
        } catch (IOException ex) {
            throw new AssertionError("Should never happen");
        }
    }

}
