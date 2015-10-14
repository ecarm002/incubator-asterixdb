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
package org.apache.asterix.dataflow.data.nontagged.comparators.allenrelations;

import org.apache.asterix.dataflow.data.nontagged.serde.AIntervalSerializerDeserializer;
import org.apache.hyracks.api.dataflow.value.IBinaryComparator;
import org.apache.hyracks.api.dataflow.value.IBinaryComparatorFactory;

public class OverlapIntervalBinaryComparatorFactory implements IBinaryComparatorFactory {

    private static final long serialVersionUID = 1L;

    public static final OverlapIntervalBinaryComparatorFactory INSTANCE = new OverlapIntervalBinaryComparatorFactory();

    private OverlapIntervalBinaryComparatorFactory() {

    }

    @Override
    public IBinaryComparator createBinaryComparator() {
        return new IBinaryComparator() {

            @Override
            public int compare(byte[] b1, int s1, int l1, byte[] b2, int s2, int l2) {
                long start0 = AIntervalSerializerDeserializer.getIntervalStart(b1, s1 + 1);
                long end0 = AIntervalSerializerDeserializer.getIntervalEnd(b1, s1 + 1);
                long start1 = AIntervalSerializerDeserializer.getIntervalStart(b2, s2 + 1);
                long end1 = AIntervalSerializerDeserializer.getIntervalEnd(b2, s2 + 1);

                int c = -1;
                if (start0 < start1 && end0 > start1 && end1 > end0) {
                    // These intervals overlap
                    c = 0;
                } else if (start0 < start1) {
                    c = 1;
                }
                return c;
            }
        };
    }
}
