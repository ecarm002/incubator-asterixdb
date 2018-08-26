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
package org.apache.asterix.algebra.operators.physical;

import java.util.List;
import java.util.logging.Logger;

import org.apache.asterix.runtime.operators.joins.IIntervalMergeJoinCheckerFactory;
import org.apache.asterix.runtime.operators.joins.intervalmergejoin.IntervalMergeJoinOperatorDescriptor;
import org.apache.hyracks.algebricks.core.algebra.base.LogicalVariable;
import org.apache.hyracks.algebricks.core.algebra.operators.logical.AbstractBinaryJoinOperator.JoinKind;
import org.apache.hyracks.api.dataflow.IOperatorDescriptor;
import org.apache.hyracks.api.dataflow.value.IRangeMap;
import org.apache.hyracks.api.dataflow.value.RecordDescriptor;
import org.apache.hyracks.api.job.IOperatorDescriptorRegistry;
import org.apache.hyracks.dataflow.std.base.RangeId;

public class IntervalMergeJoinPOperator extends AbstractIntervalJoinPOperator {

    private final int memSizeInFrames;
    protected final List<LogicalVariable> keysLeftBranch;
    protected final List<LogicalVariable> keysRightBranch;

    private static final Logger LOGGER = Logger.getLogger(IntervalMergeJoinPOperator.class.getName());

    public IntervalMergeJoinPOperator(JoinKind kind, JoinPartitioningType partitioningType,
            List<LogicalVariable> sideLeft, List<LogicalVariable> sideRight, int memSizeInFrames,
            IIntervalMergeJoinCheckerFactory mjcf, RangeId leftRangeId, RangeId rightRangeId, IRangeMap rangeMapHint) {

        super(kind, partitioningType, sideLeft, sideRight, mjcf, leftRangeId, rightRangeId, rangeMapHint);
        this.memSizeInFrames = memSizeInFrames;
        this.keysLeftBranch = sideLeft;
        this.keysRightBranch = sideRight;

        LOGGER.fine("IntervalMergeJoinPOperator constructed with: JoinKind=" + kind + ", JoinPartitioningType="
                + partitioningType + ", List<LogicalVariable>=" + keysLeftBranch + ", List<LogicalVariable>="
                + keysRightBranch + ", int memSizeInFrames=" + memSizeInFrames + ", IMergeJoinCheckerFactory mjcf="
                + mjcf + ", RangeId leftRangeId=" + leftRangeId + ", RangeId rightRangeId=" + rightRangeId + ".");
    }

    @Override
    public String getIntervalJoin() {
        return "INTERVAL_MERGE_JOIN";
    }

    @Override
    IOperatorDescriptor getIntervalOperatorDescriptor(int[] keysLeft, int[] keysRight, IOperatorDescriptorRegistry spec,
            RecordDescriptor recordDescriptor, IIntervalMergeJoinCheckerFactory mjcf, RangeId rangeId) {
        return new IntervalMergeJoinOperatorDescriptor(spec, memSizeInFrames, recordDescriptor, keysLeft, keysRight,
                mjcf);
    }

}
