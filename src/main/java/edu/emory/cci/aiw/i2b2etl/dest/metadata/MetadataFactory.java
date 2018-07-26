package edu.emory.cci.aiw.i2b2etl.dest.metadata;

/*-
 * #%L
 * AIW i2b2 ETL
 * %%
 * Copyright (C) 2012 - 2016 Emory University
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

import edu.emory.cci.aiw.i2b2etl.dest.config.Data;
import edu.emory.cci.aiw.i2b2etl.dest.config.FolderSpec;
import edu.emory.cci.aiw.i2b2etl.dest.config.Settings;
import org.arp.javautil.sql.ConnectionSpec;
import org.protempa.KnowledgeSourceCache;
import org.protempa.PropositionDefinition;
import org.protempa.PropositionDefinitionCache;

/**
 *
 * @author Andrew Post
 */
public final class MetadataFactory {

    public Metadata getInstance(PropositionDefinitionCache propDefs, String sourceSystemCode, KnowledgeSourceCache cache,
            PropositionDefinition[] userDefinedPropositionDefinitions,
            FolderSpec[] folderSpecs,
            Settings settings,
            Data dataSection, ConnectionSpec metaConnectionSpec) throws OntologyBuildException {
        Metadata result = new Metadata(propDefs, sourceSystemCode, cache, 
                userDefinedPropositionDefinitions,
                folderSpecs, settings, dataSection, metaConnectionSpec);
        result.init();
        return result;
    }
}
