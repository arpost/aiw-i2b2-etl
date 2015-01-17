package edu.emory.cci.aiw.i2b2etl.table;

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

import edu.emory.cci.aiw.i2b2etl.metadata.MetadataUtil;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.arp.javautil.sql.ConnectionSpec;

/**
 *
 * @author arpost
 */
public class EncounterMappingHandler extends RecordHandler<VisitDimension> {
    public static final String TEMP_ENC_MAPPING_TABLE = "temp_encounter_mapping";
    
    public EncounterMappingHandler(ConnectionSpec connSpec) throws SQLException {
        super(connSpec,
                "insert into " + TEMP_ENC_MAPPING_TABLE + "(encounter_id, encounter_id_source, encounter_map_id, encounter_map_id_source, " +
                    "encounter_map_id_status, patient_map_id, patient_map_id_source, update_date, download_date, import_date, sourcesystem_cd)" +
                    " values (?,?,?,?,?,?,?,?,?,?,?)");
    }

    @Override
    protected void setParameters(PreparedStatement ps2, VisitDimension visit) throws SQLException {
        ps2.setString(1, visit.getEncryptedVisitId());
        ps2.setString(2, MetadataUtil.toSourceSystemCode(visit.getEncryptedVisitIdSourceSystem()));
        ps2.setString(3, visit.getEncryptedVisitId());
        ps2.setString(4, MetadataUtil.toSourceSystemCode(visit.getEncryptedVisitIdSourceSystem()));
        ps2.setString(5, EncounterIdeStatusCode.ACTIVE.getCode());
        ps2.setString(6, visit.getEncryptedPatientId());
        ps2.setString(7, MetadataUtil.toSourceSystemCode(visit.getEncryptedPatientIdSourceSystem()));
        ps2.setDate(8, null);
        ps2.setDate(9, null);
        ps2.setTimestamp(10, importTimestamp());
        ps2.setString(11, MetadataUtil.toSourceSystemCode(visit.getVisitSourceSystem()));
    }

}
