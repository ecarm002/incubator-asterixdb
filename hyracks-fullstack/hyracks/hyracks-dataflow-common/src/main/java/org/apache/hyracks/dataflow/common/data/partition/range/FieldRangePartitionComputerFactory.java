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
package org.apache.hyracks.dataflow.common.data.partition.range;

import org.apache.hyracks.api.comm.IFrameTupleAccessor;
import org.apache.hyracks.api.dataflow.value.IBinaryComparator;
import org.apache.hyracks.api.dataflow.value.IBinaryRangeComparatorFactory;
import org.apache.hyracks.api.dataflow.value.IRangeMap;
import org.apache.hyracks.api.dataflow.value.IRangePartitionType.RangePartitioningType;
import org.apache.hyracks.api.dataflow.value.ITupleRangePartitionComputer;
import org.apache.hyracks.api.dataflow.value.ITupleRangePartitionComputerFactory;
import org.apache.hyracks.api.exceptions.HyracksDataException;
import org.apache.hyracks.api.storage.IGrowableIntArray;

public class FieldRangePartitionComputerFactory implements ITupleRangePartitionComputerFactory {
    private static final long serialVersionUID = 1L;
    private final int[] rangeFields;
    private IBinaryRangeComparatorFactory[] comparatorFactories;
    private RangePartitioningType rangeType;

    public FieldRangePartitionComputerFactory(int[] rangeFields, IBinaryRangeComparatorFactory[] comparatorFactories,
            RangePartitioningType rangeType) {
        this.rangeFields = rangeFields;
        this.comparatorFactories = comparatorFactories;
        this.rangeType = rangeType;
    }

    @Override
    public ITupleRangePartitionComputer createPartitioner(IRangeMap rangeMap) {
        final IBinaryComparator[] minComparators = new IBinaryComparator[comparatorFactories.length];
        for (int i = 0; i < comparatorFactories.length; ++i) {
            minComparators[i] = comparatorFactories[i].createMinBinaryComparator();
        }
        final IBinaryComparator[] maxComparators = new IBinaryComparator[comparatorFactories.length];
        for (int i = 0; i < comparatorFactories.length; ++i) {
            maxComparators[i] = comparatorFactories[i].createMaxBinaryComparator();
        }
        final int splitCount = rangeMap.getSplitCount();

        return new ITupleRangePartitionComputer() {
            private int partitionCount;
            private double rangesPerPart = 1;

            @Override
            public void partition(IFrameTupleAccessor accessor, int tIndex, int nParts, IGrowableIntArray map)
                    throws HyracksDataException {
                if (nParts == 1) {
                    map.add(0);
                    return;
                }
                // Map range partition to node partitions.
                if (partitionCount != nParts) {
                    partitionCount = nParts;
                    if (splitCount + 1 > nParts) {
                        rangesPerPart = ((double) splitCount + 1) / nParts;
                    }
                }
                getRangePartitions(accessor, tIndex, map);
            }

            /*
             * Determine the range partitions.
             */
            private void getRangePartitions(IFrameTupleAccessor accessor, int tIndex, IGrowableIntArray map)
                    throws HyracksDataException {
                int minPartition = getPartitionMap(binarySearchRangePartition(accessor, tIndex, minComparators));
                int maxPartition = getPartitionMap(binarySearchRangePartition(accessor, tIndex, maxComparators));
                switch (rangeType) {
                    case PROJECT:
                        addPartition(minPartition, map);
                        break;

                    case PROJECT_END:
                        addPartition(maxPartition, map);
                        break;

                    case REPLICATE:
                        for (int pid = minPartition; pid < partitionCount; ++pid) {
                            addPartition(pid, map);
                        }
                        break;

                    case SPLIT:
                        for (int pid = minPartition; pid <= maxPartition && pid < partitionCount; ++pid) {
                            addPartition(pid, map);
                        }
                        break;

                    default:
                }
            }

            private void addPartition(int partition, IGrowableIntArray map) {
                if (!hasPartition(partition, map)) {
                    map.add(partition);
                }
            }

            private int getPartitionMap(int partition) {
                return (int) Math.floor(partition / rangesPerPart);
            }

            private boolean hasPartition(int pid, IGrowableIntArray map) {
                for (int i = 0; i < map.size(); ++i) {
                    if (map.get(i) == pid) {
                        return true;
                    }
                }
                return false;
            }

            /*
             * Return first match or suggested index.
             */
            private int binarySearchRangePartition(IFrameTupleAccessor accessor, int tIndex,
                    IBinaryComparator[] comparators) throws HyracksDataException {
                int searchIndex = 0;
                int left = 0;
                int right = splitCount;
                int cmp;
                while (left <= right) {
                    searchIndex = (left + right) / 2;
                    cmp = compareSlotAndFields(accessor, tIndex, searchIndex, comparators);
                    if (cmp > 0) {
                        left = searchIndex + 1;
                        searchIndex += 1;
                    } else if (cmp < 0) {
                        right = searchIndex - 1;
                    } else {
                        return searchIndex + 1;
                    }
                }
                return searchIndex;
            }

            private int compareSlotAndFields(IFrameTupleAccessor accessor, int tIndex, int mapIndex,
                    IBinaryComparator[] comparators) throws HyracksDataException {
                int c = 0;
                int startOffset = accessor.getTupleStartOffset(tIndex);
                int slotLength = accessor.getFieldSlotsLength();
                for (int f = 0; f < comparators.length; ++f) {
                    int fIdx = rangeFields[f];
                    int fStart = accessor.getFieldStartOffset(tIndex, fIdx);
                    int fEnd = accessor.getFieldEndOffset(tIndex, fIdx);
                    c = comparators[f].compare(accessor.getBuffer().array(), startOffset + slotLength + fStart,
                            fEnd - fStart, rangeMap.getByteArray(f, mapIndex), rangeMap.getStartOffset(f, mapIndex),
                            rangeMap.getLength(f, mapIndex));
                    if (c != 0) {
                        return c;
                    }
                }
                return c;
            }

        };
    }
}
