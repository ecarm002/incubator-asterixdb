/*
 * Copyright 2009-2011 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.uci.ics.asterix.aql.expression;

import edu.uci.ics.asterix.aql.base.Statement;
import edu.uci.ics.asterix.aql.expression.visitor.IAqlExpressionVisitor;
import edu.uci.ics.asterix.aql.expression.visitor.IAqlVisitorWithVoidReturn;
import edu.uci.ics.asterix.common.config.DatasetConfig.DatasetType;
import edu.uci.ics.asterix.common.exceptions.AsterixException;

public class DatasetDecl implements Statement {
    protected Identifier name;
    protected Identifier dataverse;
    protected Identifier itemTypeName;
    protected DatasetType datasetType;
    protected IDatasetDetailsDecl datasetDetailsDecl;

    public boolean ifNotExists;

    public DatasetDecl(Identifier dataverse, Identifier name, Identifier itemTypeName, DatasetType datasetType,
            IDatasetDetailsDecl idd, boolean ifNotExists) {
        this.dataverse = dataverse;
        this.name = name;
        this.itemTypeName = itemTypeName;
        this.ifNotExists = ifNotExists;
        this.datasetType = datasetType;
        datasetDetailsDecl = idd;
    }

    public boolean getIfNotExists() {
        return this.ifNotExists;
    }

    public DatasetType getDatasetType() {
        return datasetType;
    }

    public Identifier getName() {
        return name;
    }

    public Identifier getItemTypeName() {
        return itemTypeName;
    }

    @Override
    public <R, T> R accept(IAqlExpressionVisitor<R, T> visitor, T arg) throws AsterixException {
        return visitor.visitDatasetDecl(this, arg);
    }

    @Override
    public <T> void accept(IAqlVisitorWithVoidReturn<T> visitor, T arg) throws AsterixException {
        visitor.visit(this, arg);
    }

    @Override
    public Kind getKind() {
        return Kind.DATASET_DECL;
    }

    public IDatasetDetailsDecl getDatasetDetailsDecl() {
        return datasetDetailsDecl;
    }

    public Identifier getDataverse() {
        return dataverse;
    }

}
