/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.asterix.algebra.operators;

import java.util.Collection;
import java.util.List;

import org.apache.hyracks.algebricks.common.exceptions.AlgebricksException;
import org.apache.hyracks.algebricks.core.algebra.base.LogicalVariable;
import org.apache.hyracks.algebricks.core.algebra.operators.logical.AbstractDelegatedLogicalOperator;
import org.apache.hyracks.algebricks.core.algebra.operators.logical.IOperatorDelegate;
import org.apache.hyracks.algebricks.core.algebra.visitors.ILogicalExpressionReferenceTransform;

public class IntervalLocalRangeSplitterOperator extends AbstractDelegatedLogicalOperator {

    private final List<LogicalVariable> joinKeyLogicalVars;

    public IntervalLocalRangeSplitterOperator(List<LogicalVariable> joinKeyLogicalVars) {
        this.joinKeyLogicalVars = joinKeyLogicalVars;
    }

    @Override
    public boolean isMap() {
        return false;
    }

    @Override
    public IOperatorDelegate newInstance() {
        return new IntervalLocalRangeSplitterOperator(joinKeyLogicalVars);
    }

    @Override
    public boolean acceptExpressionTransform(ILogicalExpressionReferenceTransform transform)
            throws AlgebricksException {
        return false;
    }

    @Override
    public String toString() {
        return "IntervalLocalRangeSplitterOperator " + joinKeyLogicalVars;
    }

    @Override
    public void getUsedVariables(Collection<LogicalVariable> usedVars) {
        usedVars.addAll(joinKeyLogicalVars);
    }

    @Override
    public void getProducedVariables(Collection<LogicalVariable> producedVars) {
        // No produced variables.
    }
}
