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
package edu.emory.cci.aiw.i2b2etl.metadata;

import org.protempa.proposition.value.ValueType;

/**
 *
 * @author Andrew Post
 */
public enum DataType {
    TEXT("T"),
    NUMERIC("N");
    
    private final String code;
    
    public static DataType dataTypeFor(ValueType valueType) {
        if (ValueType.NUMERICALVALUE == valueType ||
                    ValueType.NUMBERVALUE == valueType ||
                    ValueType.INEQUALITYNUMBERVALUE == valueType) {
                return DataType.NUMERIC;
            } else {
                return DataType.TEXT;
            }
    }
    
    private DataType(String code) {
        assert code != null : "code cannot be null";
        this.code = code;
    }
    
    public String getCode() {
        return this.code;
    }
}
