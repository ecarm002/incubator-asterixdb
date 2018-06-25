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

import org.apache.asterix.om.pointables.nonvisitor.AIntervalPointable;
import org.apache.hyracks.api.exceptions.HyracksDataException;
import org.apache.hyracks.dataflow.std.buffermanager.ITupleAccessor;
import org.apache.hyracks.dataflow.std.join.IMergeJoinChecker;

public interface IIntervalMergeJoinChecker extends IMergeJoinChecker {

    public boolean checkToRemoveLeftActive();

    public boolean checkToRemoveRightActive();

    public boolean checkToIncrementMerge(ITupleAccessor accessorLeft, ITupleAccessor accessorRight)
            throws HyracksDataException;

    public boolean compareInterval(AIntervalPointable ipLeft, AIntervalPointable ipRight) throws HyracksDataException;

    public boolean compareIntervalPartition(int s1, int e1, int s2, int e2);

    public boolean checkToSaveInResult(long start0, long end0, long start1, long end1, boolean reversed);

    public boolean checkToSaveInMemory(long start0, long end0, long start1, long end1, boolean reversed);

    public boolean checkToRemoveFromMemory(long start0, long end0, long start1, long end1, boolean reversed);

    boolean compareInterval(long start0, long end0, long start1, long end1) throws HyracksDataException;

}