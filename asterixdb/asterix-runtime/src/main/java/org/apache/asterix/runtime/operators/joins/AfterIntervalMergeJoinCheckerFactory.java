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
package org.apache.asterix.runtime.operators.joins;

import org.apache.hyracks.api.context.IHyracksTaskContext;
import org.apache.hyracks.api.dataflow.value.IRangePartitionType.RangePartitioningType;

public class AfterIntervalMergeJoinCheckerFactory extends AbstractIntervalMergeJoinCheckerFactory {
    private static final long serialVersionUID = 1L;

    @Override
    public IIntervalMergeJoinChecker createMergeJoinChecker(int[] keys0, int[] keys1, IHyracksTaskContext ctx) {
        return new AfterIntervalMergeJoinChecker(keys0, keys1);
    }

    @Override
    public IIntervalMergeJoinChecker createInverseMergeJoinChecker(int[] keys0, int[] keys1, IHyracksTaskContext ctx) {
        return new BeforeIntervalMergeJoinChecker(keys0, keys1);
    }

    @Override
    public RangePartitioningType getLeftPartitioningType() {
        return RangePartitioningType.PROJECT;
    }

    @Override
    public RangePartitioningType getRightPartitioningType() {
        return RangePartitioningType.REPLICATE;
    }

}