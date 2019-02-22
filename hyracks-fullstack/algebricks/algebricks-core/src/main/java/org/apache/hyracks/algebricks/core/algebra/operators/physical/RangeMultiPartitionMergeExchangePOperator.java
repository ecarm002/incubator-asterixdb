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
package org.apache.hyracks.algebricks.core.algebra.operators.physical;

import org.apache.hyracks.algebricks.common.exceptions.AlgebricksException;
import org.apache.hyracks.algebricks.common.utils.Pair;
import org.apache.hyracks.algebricks.core.algebra.base.IHyracksJobBuilder.TargetConstraint;
import org.apache.hyracks.algebricks.core.algebra.base.ILogicalOperator;
import org.apache.hyracks.algebricks.core.algebra.base.IOptimizationContext;
import org.apache.hyracks.algebricks.core.algebra.base.LogicalVariable;
import org.apache.hyracks.algebricks.core.algebra.base.PhysicalOperatorTag;
import org.apache.hyracks.algebricks.core.algebra.expressions.IVariableTypeEnvironment;
import org.apache.hyracks.algebricks.core.algebra.operators.logical.AbstractLogicalOperator;
import org.apache.hyracks.algebricks.core.algebra.operators.logical.IOperatorSchema;
import org.apache.hyracks.algebricks.core.algebra.operators.logical.OrderOperator.IOrder.OrderKind;
import org.apache.hyracks.algebricks.core.algebra.properties.*;
import org.apache.hyracks.algebricks.core.algebra.properties.ILocalStructuralProperty.PropertyType;
import org.apache.hyracks.algebricks.core.jobgen.impl.JobGenContext;
import org.apache.hyracks.algebricks.data.IBinaryComparatorFactoryProvider;
import org.apache.hyracks.algebricks.data.INormalizedKeyComputerFactoryProvider;
import org.apache.hyracks.api.dataflow.IConnectorDescriptor;
import org.apache.hyracks.api.dataflow.value.IBinaryComparatorFactory;
import org.apache.hyracks.api.dataflow.value.IBinaryRangeComparatorFactory;
import org.apache.hyracks.api.dataflow.value.INormalizedKeyComputerFactory;
import org.apache.hyracks.api.dataflow.value.IRangePartitionType.RangePartitioningType;
import org.apache.hyracks.api.dataflow.value.ITupleMultiPartitionComputerFactory;
import org.apache.hyracks.api.job.IConnectorDescriptorRegistry;
import org.apache.hyracks.dataflow.common.data.partition.range.StaticFieldRangeMultiPartitionComputerFactory;
import org.apache.hyracks.dataflow.std.connectors.MToNMultiPartitioningMergingConnectorDescriptor;
import org.apache.hyracks.dataflow.common.data.partition.range.RangeMap;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class RangeMultiPartitionMergeExchangePOperator extends AbstractExchangePOperator {

    private List<OrderColumn> partitioningFields;
    private INodeDomain domain;
    private RangePartitioningType rangeType;
    private RangeMap rangeMap;

    public RangeMultiPartitionMergeExchangePOperator(List<OrderColumn> partitioningFields, INodeDomain domain,
                                                     RangePartitioningType rangeType, RangeMap rangeMap) {
        this.partitioningFields = partitioningFields;
        this.domain = domain;
        this.rangeType = rangeType;
        this.rangeMap = rangeMap;
    }

    @Override
    public PhysicalOperatorTag getOperatorTag() {
        return PhysicalOperatorTag.RANGE_PARTITION_MERGE_EXCHANGE;
    }

    public List<OrderColumn> getPartitioningFields() {
        return partitioningFields;
    }

    public RangePartitioningType getRangeType() {
        return rangeType;
    }

    public INodeDomain getDomain() {
        return domain;
    }

    @Override
    public void computeDeliveredProperties(ILogicalOperator op, IOptimizationContext context) {
        IPartitioningProperty p = new OrderedPartitionedProperty(partitioningFields, domain, rangeType);
        AbstractLogicalOperator op2 = (AbstractLogicalOperator) op.getInputs().get(0).getValue();
        List<ILocalStructuralProperty> op2Locals = op2.getDeliveredPhysicalProperties().getLocalProperties();
        List<ILocalStructuralProperty> locals = new ArrayList<>();
        for (ILocalStructuralProperty prop : op2Locals) {
            if (prop.getPropertyType() == PropertyType.LOCAL_ORDER_PROPERTY) {
                locals.add(prop);
            } else {
                break;
            }
        }
        this.deliveredProperties = new StructuralPropertiesVector(p, locals);
    }

    @Override
    public PhysicalRequirements getRequiredPropertiesForChildren(ILogicalOperator op,
                                                                 IPhysicalPropertiesVector reqdByParent, IOptimizationContext context) {
        List<ILocalStructuralProperty> orderProps = new LinkedList<>();
        List<OrderColumn> columns = new ArrayList<>();
        for (OrderColumn oc : partitioningFields) {
            LogicalVariable var = oc.getColumn();
            columns.add(new OrderColumn(var, oc.getOrder()));
        }
        orderProps.add(new LocalOrderProperty(columns));
        StructuralPropertiesVector[] r = new StructuralPropertiesVector[] {
                new StructuralPropertiesVector(null, orderProps) };
        return new PhysicalRequirements(r, IPartitioningRequirementsCoordinator.NO_COORDINATION);
    }

    @Override
    public Pair<IConnectorDescriptor, TargetConstraint> createConnectorDescriptor(IConnectorDescriptorRegistry spec,
                                                                                  ILogicalOperator op, IOperatorSchema opSchema, JobGenContext context) throws AlgebricksException {
        int n = partitioningFields.size();
        int[] sortFields = new int[n];
        IBinaryRangeComparatorFactory[] rangeComps = new IBinaryRangeComparatorFactory[n];
        IBinaryComparatorFactory[] binaryComps = new IBinaryComparatorFactory[n];

        INormalizedKeyComputerFactoryProvider nkcfProvider = context.getNormalizedKeyComputerFactoryProvider();
        INormalizedKeyComputerFactory nkcf = null;

        IVariableTypeEnvironment env = context.getTypeEnvironment(op);
        int i = 0;
        for (OrderColumn oc : partitioningFields) {
            LogicalVariable var = oc.getColumn();
            sortFields[i] = opSchema.findVariable(var);
            Object type = env.getVarType(var);
            OrderKind order = oc.getOrder();
            if (i == 0 && nkcfProvider != null && type != null) {
                nkcf = nkcfProvider.getNormalizedKeyComputerFactory(type, order == OrderKind.ASC);
            }
            IBinaryComparatorFactoryProvider bcfp = context.getBinaryComparatorFactoryProvider();
            rangeComps[i] = bcfp.getRangeBinaryComparatorFactory(type, oc.getOrder() == OrderKind.ASC, rangeType);
            binaryComps[i] = bcfp.getBinaryComparatorFactory(type, oc.getOrder() == OrderKind.ASC);
            i++;
        }
        ITupleMultiPartitionComputerFactory tpcf = new StaticFieldRangeMultiPartitionComputerFactory(sortFields, rangeComps, rangeMap, rangeType);
        IConnectorDescriptor conn = new MToNMultiPartitioningMergingConnectorDescriptor(spec, tpcf, sortFields,
                binaryComps, nkcf);
        return new Pair<>(conn, null);
    }

    @Override
    public String toString() {
        return getOperatorTag().toString() + " " + partitioningFields + " SPLIT COUNT:" + rangeMap.getSplitCount() + " " + rangeType;
    }

}
