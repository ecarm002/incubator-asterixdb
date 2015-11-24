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

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.apache.asterix.runtime.operators.joins.IntervalPartitionJoinOperatorDescriptor;
import org.apache.hyracks.algebricks.common.exceptions.AlgebricksException;
import org.apache.hyracks.algebricks.core.algebra.base.IHyracksJobBuilder;
import org.apache.hyracks.algebricks.core.algebra.base.ILogicalOperator;
import org.apache.hyracks.algebricks.core.algebra.base.IOptimizationContext;
import org.apache.hyracks.algebricks.core.algebra.base.LogicalVariable;
import org.apache.hyracks.algebricks.core.algebra.base.PhysicalOperatorTag;
import org.apache.hyracks.algebricks.core.algebra.operators.logical.AbstractBinaryJoinOperator.JoinKind;
import org.apache.hyracks.algebricks.core.algebra.operators.logical.AbstractLogicalOperator;
import org.apache.hyracks.algebricks.core.algebra.operators.logical.IOperatorSchema;
import org.apache.hyracks.algebricks.core.algebra.operators.logical.OrderOperator.IOrder.OrderKind;
import org.apache.hyracks.algebricks.core.algebra.operators.physical.AbstractJoinPOperator;
import org.apache.hyracks.algebricks.core.algebra.properties.IPartitioningProperty;
import org.apache.hyracks.algebricks.core.algebra.properties.IPartitioningRequirementsCoordinator;
import org.apache.hyracks.algebricks.core.algebra.properties.IPhysicalPropertiesVector;
import org.apache.hyracks.algebricks.core.algebra.properties.OrderColumn;
import org.apache.hyracks.algebricks.core.algebra.properties.OrderedPartitionedProperty;
import org.apache.hyracks.algebricks.core.algebra.properties.PhysicalRequirements;
import org.apache.hyracks.algebricks.core.algebra.properties.StructuralPropertiesVector;
import org.apache.hyracks.algebricks.core.jobgen.impl.JobGenContext;
import org.apache.hyracks.algebricks.core.jobgen.impl.JobGenHelper;
import org.apache.hyracks.api.dataflow.value.RecordDescriptor;
import org.apache.hyracks.api.job.IOperatorDescriptorRegistry;
import org.apache.hyracks.dataflow.common.data.partition.range.IRangeMap;
import org.apache.hyracks.dataflow.common.data.partition.range.IRangePartitionType.RangePartitioningType;
import org.apache.hyracks.dataflow.std.join.IMergeJoinCheckerFactory;

public class IntervalPartitionJoinPOperator extends AbstractJoinPOperator {

    private final List<LogicalVariable> keysLeftBranch;
    private final List<LogicalVariable> keysRightBranch;
    private final int memSizeInFrames;
    private final int maxInputBuildSizeInFrames;
    private final int aveRecordsPerFrame;
    private final double fudgeFactor;
    private final IMergeJoinCheckerFactory mjcf;
    private final IRangeMap rangeMap;

    private static final Logger LOGGER = Logger.getLogger(IntervalPartitionJoinPOperator.class.getName());

    public IntervalPartitionJoinPOperator(JoinKind kind, JoinPartitioningType partitioningType,
            List<LogicalVariable> sideLeftOfEqualities, List<LogicalVariable> sideRightOfEqualities,
            int memSizeInFrames, int maxInputSizeInFrames, int aveRecordsPerFrame, double fudgeFactor,
            IMergeJoinCheckerFactory mjcf, IRangeMap rangeMap) {
        super(kind, partitioningType);
        this.keysLeftBranch = sideLeftOfEqualities;
        this.keysRightBranch = sideRightOfEqualities;
        this.memSizeInFrames = memSizeInFrames;
        this.maxInputBuildSizeInFrames = maxInputSizeInFrames;
        this.aveRecordsPerFrame = aveRecordsPerFrame;
        this.fudgeFactor = fudgeFactor;
        this.mjcf = mjcf;
        this.rangeMap = rangeMap;

        LOGGER.fine("IntervalPartitionJoinPOperator constructed with: JoinKind=" + kind + ", JoinPartitioningType="
                + partitioningType + ", List<LogicalVariable>=" + sideLeftOfEqualities + ", List<LogicalVariable>="
                + sideRightOfEqualities + ", int memSizeInFrames=" + memSizeInFrames + ", int maxInputSizeInFrames="
                + maxInputSizeInFrames + ", int aveRecordsPerFrame=" + aveRecordsPerFrame + ", double fudgeFactor="
                + fudgeFactor + ", IMergeJoinCheckerFactory mjcf=" + mjcf + ", IRangeMap rangeMap=" + rangeMap + ".");
    }

    @Override
    public PhysicalOperatorTag getOperatorTag() {
        return PhysicalOperatorTag.EXTENSION_OPERATOR;
    }

    @Override
    public String toString() {
        return "INTERVAL_PARTITION_JOIN " + keysLeftBranch + keysRightBranch;
    }

    @Override
    public boolean isMicroOperator() {
        return false;
    }

    @Override
    public void computeDeliveredProperties(ILogicalOperator iop, IOptimizationContext context) {
        IPartitioningProperty pp = null;
        ArrayList<OrderColumn> order = new ArrayList<OrderColumn>();
        for (LogicalVariable v : keysLeftBranch) {
            order.add(new OrderColumn(v, OrderKind.ASC));
        }
        pp = new OrderedPartitionedProperty(order, null, rangeMap, RangePartitioningType.PROJECT);
        deliveredProperties = new StructuralPropertiesVector(pp, null);
    }

    @Override
    public PhysicalRequirements getRequiredPropertiesForChildren(ILogicalOperator iop,
            IPhysicalPropertiesVector reqdByParent) {
        StructuralPropertiesVector[] pv = new StructuralPropertiesVector[2];
        AbstractLogicalOperator op = (AbstractLogicalOperator) iop;

        IPartitioningProperty ppLeft = null;
        IPartitioningProperty ppRight = null;

        ArrayList<OrderColumn> orderLeft = new ArrayList<OrderColumn>();
        for (LogicalVariable v : keysLeftBranch) {
            orderLeft.add(new OrderColumn(v, OrderKind.ASC));
        }

        ArrayList<OrderColumn> orderRight = new ArrayList<OrderColumn>();
        for (LogicalVariable v : keysRightBranch) {
            orderRight.add(new OrderColumn(v, OrderKind.ASC));
        }

        if (op.getExecutionMode() == AbstractLogicalOperator.ExecutionMode.PARTITIONED) {
            ppLeft = new OrderedPartitionedProperty(orderLeft, null, rangeMap, mjcf.getLeftPartitioningType());
            ppRight = new OrderedPartitionedProperty(orderRight, null, rangeMap, mjcf.getRightPartitioningType());
        }

        pv[0] = new StructuralPropertiesVector(ppLeft, null);
        pv[1] = new StructuralPropertiesVector(ppRight, null);
        IPartitioningRequirementsCoordinator prc = IPartitioningRequirementsCoordinator.NO_COORDINATION;
        return new PhysicalRequirements(pv, prc);
    }

    @Override
    public void contributeRuntimeOperator(IHyracksJobBuilder builder, JobGenContext context, ILogicalOperator op,
            IOperatorSchema opSchema, IOperatorSchema[] inputSchemas, IOperatorSchema outerPlanSchema)
                    throws AlgebricksException {
        int[] keysLeft = JobGenHelper.variablesToFieldIndexes(keysLeftBranch, inputSchemas[0]);
        int[] keysRight = JobGenHelper.variablesToFieldIndexes(keysRightBranch, inputSchemas[1]);

        IOperatorDescriptorRegistry spec = builder.getJobSpec();
        RecordDescriptor recordDescriptor = JobGenHelper.mkRecordDescriptor(context.getTypeEnvironment(op), opSchema,
                context);

        IntervalPartitionJoinOperatorDescriptor opDesc = new IntervalPartitionJoinOperatorDescriptor(spec,
                memSizeInFrames, maxInputBuildSizeInFrames, aveRecordsPerFrame, fudgeFactor, keysLeft, keysRight,
                recordDescriptor, mjcf, rangeMap);
        contributeOpDesc(builder, (AbstractLogicalOperator) op, opDesc);

        ILogicalOperator src1 = op.getInputs().get(0).getValue();
        builder.contributeGraphEdge(src1, 0, op, 0);
        ILogicalOperator src2 = op.getInputs().get(1).getValue();
        builder.contributeGraphEdge(src2, 0, op, 1);
    }

}