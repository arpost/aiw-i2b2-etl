/*
 * #%L
 * AIW i2b2 ETL
 * %%
 * Copyright (C) 2012 Emory University
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
package edu.emory.cci.aiw.i2b2etl.cli;

import edu.emory.cci.aiw.i2b2etl.ProtempaFactory;
import edu.emory.cci.aiw.i2b2etl.I2B2QueryResultsHandler;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import org.arp.javautil.arrays.Arrays;
import org.arp.javautil.io.WithBufferedReaderByLine;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.protempa.KnowledgeSource;
import org.protempa.KnowledgeSourceReadException;
import org.protempa.Protempa;
import org.protempa.query.DefaultQueryBuilder;
import org.protempa.query.Query;
import org.protempa.query.QueryBuildException;
import org.protempa.query.QueryBuilder;
import org.protempa.query.handler.QueryResultsHandlerInitException;

/**
 *
 * @author Andrew Post
 */
public class GetPropositionIdsNeededTest {

    private static Protempa protempa;
    private static File confXML;
    private static Set<String> expectedPropIds;

    @BeforeClass
    public static void setUp() throws Exception {
        protempa = new ProtempaFactory().newInstance();
        confXML = new I2b2ETLConfAsFile().getFile();
        expectedPropIds = expectedPropIds();
    }

    @Test
    public void testPropositionIds() throws
            QueryResultsHandlerInitException, KnowledgeSourceReadException, 
            QueryBuildException {
        KnowledgeSource knowledgeSource = protempa.getKnowledgeSource();
        I2B2QueryResultsHandler qrh = new I2B2QueryResultsHandler(confXML);
        QueryBuilder queryBuilder = new DefaultQueryBuilder();
        Query query = protempa.buildQuery(queryBuilder);
        qrh.init(knowledgeSource, query);
        String[] actualPropIds = qrh.getPropositionIdsNeeded();
        Assert.assertEquals(expectedPropIds, Arrays.asSet(actualPropIds));
    }

    @AfterClass
    public static void tearDown() {
        if (protempa != null) {
            protempa.close();
        }
    }
    
    private static Set<String> expectedPropIds() throws IOException {
        final Set<String> result = new HashSet<String>();
        InputStream is = 
                GetPropositionIdsNeededTest.class.getResourceAsStream(
                "/get-proposition-ids-needed-test-file");
        new WithBufferedReaderByLine(is) {

            @Override
            public void readLine(String line) {
                line = line.trim();
                if (line.length() != 0) {
                    result.add(line);
                }
            }  
        }.execute();
        
        return result;
    }
}
