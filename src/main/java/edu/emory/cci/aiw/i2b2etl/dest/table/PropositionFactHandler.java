/*
 * #%L
 * AIW i2b2 ETL
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
package edu.emory.cci.aiw.i2b2etl.dest.table;

import edu.emory.cci.aiw.i2b2etl.dest.metadata.Metadata;
import edu.emory.cci.aiw.i2b2etl.dest.metadata.Concept;
import edu.emory.cci.aiw.i2b2etl.dest.metadata.conceptid.ConceptId;
import edu.emory.cci.aiw.i2b2etl.dest.metadata.conceptid.ModifierConceptId;
import edu.emory.cci.aiw.i2b2etl.dest.metadata.conceptid.PropDefConceptId;
import edu.emory.cci.aiw.i2b2etl.dest.metadata.conceptid.PropertyConceptId;
import edu.emory.cci.aiw.i2b2etl.dest.metadata.UnknownPropositionDefinitionException;

import java.sql.*;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.arp.javautil.sql.ConnectionSpec;
import org.drools.util.StringUtils;
import org.protempa.KnowledgeSourceCache;
import org.protempa.PropertyDefinition;
import org.protempa.PropositionDefinition;
import org.protempa.proposition.*;
import org.protempa.proposition.value.*;
import org.protempa.dest.table.Derivation;
import org.protempa.dest.table.Link;
import org.protempa.dest.table.LinkTraverser;
import org.protempa.proposition.comparator.AllPropositionIntervalComparator;

public final class PropositionFactHandler extends FactHandler {

    private static final Comparator<Proposition> PROP_COMP
            = new AllPropositionIntervalComparator();

    private final LinkTraverser linkTraverser;
    private final Link[] links;
    private final Link[] derivationLinks;
    private KnowledgeSourceCache cache;
    private final Set<ConceptId> missingConcepts;

    public PropositionFactHandler(ConnectionSpec connSpec,
            Link[] links, String propertyName,
            String start, String finish, String unitsPropertyName,
            String[] potentialDerivedPropIds, Metadata metadata,
            KnowledgeSourceCache cache,
            RejectedFactHandlerFactory rejectedFactHandlerFactory) throws SQLException {
        super(connSpec, propertyName, start, finish, unitsPropertyName, metadata, rejectedFactHandlerFactory);
        if (cache == null) {
            throw new IllegalArgumentException("cache cannot be null");
        }

        this.linkTraverser = new LinkTraverser();
        this.links = links;
        if (potentialDerivedPropIds == null) {
            potentialDerivedPropIds = StringUtils.EMPTY_STRING_ARRAY;
        }
        this.derivationLinks = new Link[]{
            new Derivation(potentialDerivedPropIds,
            Derivation.Behavior.MULT_FORWARD)
        };
        this.cache = cache;
        this.missingConcepts = new HashSet<>();
    }

    private abstract class PropositionWrapper implements Comparable<PropositionWrapper> {

        abstract Concept getConcept();

        abstract Proposition getProposition();

        @Override
        public int compareTo(PropositionWrapper o) {
            return PROP_COMP.compare(getProposition(), o.getProposition());
        }
    }

    @Override
    public void handleRecord(PatientDimension patient, VisitDimension visit,
            ProviderDimension provider,
            Proposition encounterProp,
            Map<Proposition, Set<Proposition>> forwardDerivations,
            Map<Proposition, Set<Proposition>> backwardDerivations,
            Map<UniqueId, Proposition> references,
            Set<Proposition> derivedPropositions)
            throws InvalidFactException {
        assert patient != null : "patient cannot be null";
        assert visit != null : "visit cannot be null";
        assert provider != null : "provider cannot be null";
        List<Proposition> props;
        try {
            props = this.linkTraverser.traverseLinks(this.links, encounterProp,
                    forwardDerivations,
                    backwardDerivations, references, this.cache);
            Set<Proposition> derivedPropCache = new HashSet<>();
            for (Proposition prop : props) {
                String propertyName = getPropertyName();
                Value propertyVal = propertyName != null
                        ? prop.getProperty(propertyName) : null;
                PropDefConceptId conceptId = PropDefConceptId.getInstance(prop.getId(), propertyName, propertyVal, getMetadata());
                doInsert(conceptId, prop, encounterProp, patient, visit, provider);
                List<Proposition> derivedProps = this.linkTraverser.traverseLinks(
                            this.derivationLinks, prop, forwardDerivations,
                            backwardDerivations, references,
                            this.cache);
                for (Proposition derivedProp : derivedProps) {
                    if (derivedPropCache.add(derivedProp)) {
                        PropDefConceptId derivedConceptId = PropDefConceptId.getInstance(derivedProp.getId(), null, null, getMetadata());
                        doInsert(derivedConceptId, derivedProp, encounterProp, patient, visit, provider);
                    }
                }
            }
        } catch (UnknownPropositionDefinitionException ex) {
            throw new InvalidFactException(ex);
        }
    }

    private void doInsert(PropertyConceptId conceptId, Proposition prop, Proposition encounterProp, PatientDimension patient, VisitDimension visit, ProviderDimension provider) throws InvalidFactException, UnknownPropositionDefinitionException {
        assert conceptId != null : "conceptId cannot be null";
        assert prop != null : "prop cannot be null";
        assert encounterProp != null : "encounterProp cannot be null";
        assert patient != null : "patient cannot be null";
        assert visit != null : "visit cannot be null";
        assert provider != null : "provider cannot be null";
        if (getMetadata().getFromIdCache(conceptId) == null) {
            // Only log the problem on its first occurrence.
            if (this.missingConcepts.add(conceptId)) {
                TableUtil.logger().log(Level.WARNING, "No metadata for concept {0}; this data will not be loaded", conceptId);
            }
        } else {
            ObservationFact obx = populateObxFact(prop,
                    encounterProp, patient, visit, provider, conceptId, null);
            PropositionDefinition propDef = this.cache.get(prop.getId());
            if (propDef == null) {
                throw new UnknownPropositionDefinitionException(prop);
            }
            try {
                insert(obx);
            } catch (SQLException ex) {
                String msg = "Observation fact not created";
                throw new InvalidFactException(msg, ex);
            }
            for (String propertyName : prop.getPropertyNames()) {
                PropertyDefinition propertyDefinition = propDef.propertyDefinition(propertyName);
                if (propertyDefinition != null) {
                    ModifierConceptId modConceptId = ModifierConceptId.getInstance(propertyDefinition.getDeclaringPropId(), propertyName, prop.getProperty(propertyName), getMetadata());
                    //Check with property value, then without, to cover both kinds of modifier concepts.
                    boolean found = getMetadata().getFromIdCache(modConceptId) != null;
                    if (!found) {
                        modConceptId = ModifierConceptId.getInstance(propertyDefinition.getDeclaringPropId(), propertyName, null, getMetadata());
                        found = getMetadata().getFromIdCache(modConceptId) != null;
                    }
                    if (!found) {
                        if (this.missingConcepts.add(modConceptId)) {
                            TableUtil.logger().log(Level.WARNING, "No metadata for modifier {0}; this modifier data will not be loaded. If you already are loading it as a concept, you may ignore this warning.", modConceptId);
                        }
                    } else {
                        ObservationFact modObx = populateObxFact(prop, encounterProp, patient, visit, provider, conceptId, modConceptId);
                        try {
                            insert(modObx);
                        } catch (SQLException ex) {
                            throw new InvalidFactException("Modifier fact not created", ex);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void close() throws SQLException {
        this.missingConcepts.clear();
        super.close();
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

}
