/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
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

import edu.emory.cci.aiw.i2b2etl.metadata.Concept;
import java.sql.SQLException;
import java.util.ArrayList;

/**
 *
 * @author arpost
 */
public class ModifierDimensionLoader extends ConceptHierarchyLoader {
    private final ModifierDimensionHandler handler;
    private final ModifierDimension modifierDimension;

    public ModifierDimensionLoader(ModifierDimensionHandler handler) {
        this.handler = handler;
        this.modifierDimension = new ModifierDimension();
    }
    
    

    @Override
    protected void loadConcept(Concept concept) throws SQLException {
        if (concept.isInUse() && !concept.getAppliedPath().equals("@")) {
            ArrayList<String> paths = concept.getHierarchyPaths();
            if (paths != null) {
                for (String path : paths) {
                    this.modifierDimension.setPath(path);
                    this.modifierDimension.setConceptCode(concept.getConceptCode());
                    this.modifierDimension.setDisplayName(concept.getDisplayName());
                    this.modifierDimension.setSourceSystemCode(concept.getSourceSystemCode());
                    this.modifierDimension.setDownloaded(concept.getDownloaded());
                    this.handler.insert(this.modifierDimension);
                }
            }
        }
    }
    
}