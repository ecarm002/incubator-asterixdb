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
import org.apache.hyracks.api.job.IConnectorDescriptorRegistry;
import org.apache.hyracks.dataflow.common.data.partition.range.RangeMap;
import org.apache.hyracks.dataflow.std.base.RangeId;
import org.apache.hyracks.dataflow.std.connectors.MToNMultiPartitioningConnectorDescriptor;
import org.apache.hyracks.dataflow.common.data.partition.range.DynamicFieldRangeMultiPartitionComputerFactory;
import org.apache.hyracks.dataflow.common.data.partition.range.StaticFieldRangeMultiPartitionComputerFactory;
import org.apache.hyracks.dataflow.common.data.partition.range.FieldRangeMultiPartitionComputerFactory;

import java.util.ArrayList;
import java.util.List;

public class RangeMultiPartitionExchangePOperator extends AbstractExchangePOperator {

    private List<OrderColumn> partitioningFields;
    private INodeDomain domain;
    private RangeMap rangeMap;
    private final boolean rangeMapIsComputedAtRunTime;
    private final String rangeMapKeyInContext;
    private RangePartitioningType rangeType;
    private RangeId rangeId;

    public RangeMultiPartitionExchangePOperator(List<OrderColumn> partitioningFields, INodeDomain domain, RangeId rangeId, RangeMap rangeMap,
                                                boolean rangeMapIsComputedAtRunTime, String rangeMapKeyInContext,
                                                RangePartitioningType rangeType) {
        this.partitioningFields = partitioningFields;
        this.domain = domain;
        this.rangeId = rangeId;
        this.rangeMap = rangeMap;
        this.rangeMapIsComputedAtRunTime = rangeMapIsComputedAtRunTime;
        this.rangeMapKeyInContext = rangeMapKeyInContext;
        this.rangeType = rangeType;
    }

    public RangeMultiPartitionExchangePOperator(List<OrderColumn> partitioningFields, String rangeMapKeyInContext,
                                           INodeDomain domain, RangeId rangeId, RangePartitioningType rangeType) {
        this(partitioningFields, domain, rangeId, null, true, rangeMapKeyInContext, rangeType);
    }

    public RangeMultiPartitionExchangePOperator(List<OrderColumn> partitioningFields, INodeDomain domain, RangeId rangeId,
                                           RangeMap rangeMap, RangePartitioningType rangeType) {
        this(partitioningFields, domain, rangeId, rangeMap, false, "", rangeType);
    }

    @Override
    public PhysicalOperatorTag getOperatorTag() {
        return PhysicalOperatorTag.RANGE_MULTI_PARTITION_EXCHANGE;
    }

    public List<OrderColumn> getPartitioningFields() {
        return partitioningFields;
    }

    public RangePartitioningType getRangeType() {
        return rangeType;
    }

    public RangeId getRangeId() {
        return rangeId;
    }

    public INodeDomain getDomain() {
        return domain;
    }

    @Override
    public void computeDeliveredProperties(ILogicalOperator op, IOptimizationContext context) {
        IPartitioningProperty p = new OrderedPartitionedProperty(new ArrayList<OrderColumn>(partitioningFields), domain,
                rangeId, rangeType, null);
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
        return emptyUnaryRequirements();
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

        FieldRangeMultiPartitionComputerFactory partitionerFactory;
        if (rangeMapIsComputedAtRunTime) {
            partitionerFactory = new DynamicFieldRangeMultiPartitionComputerFactory(sortFields, rangeComps, rangeMapKeyInContext,
                    op.getSourceLocation(), rangeType);
        } else {
            partitionerFactory = new StaticFieldRangeMultiPartitionComputerFactory(sortFields, rangeComps, rangeMap, rangeType);
        }

        IConnectorDescriptor conn = new MToNMultiPartitioningConnectorDescriptor(spec, partitionerFactory, rangeId, sortFields,
                binaryComps, nkcf);
        return new Pair<>(conn, null);
    }

    @Override
    public String toString() {
        return getOperatorTag().toString() + " " + partitioningFields + " " + rangeType + " " + rangeId;
    }

}
