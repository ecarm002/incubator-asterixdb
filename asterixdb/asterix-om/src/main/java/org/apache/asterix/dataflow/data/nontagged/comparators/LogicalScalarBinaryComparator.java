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
package org.apache.asterix.dataflow.data.nontagged.comparators;

import static org.apache.asterix.om.types.ATypeTag.CIRCLE;
import static org.apache.asterix.om.types.ATypeTag.DURATION;
import static org.apache.asterix.om.types.ATypeTag.INTERVAL;
import static org.apache.asterix.om.types.ATypeTag.LINE;
import static org.apache.asterix.om.types.ATypeTag.POINT;
import static org.apache.asterix.om.types.ATypeTag.POINT3D;
import static org.apache.asterix.om.types.ATypeTag.POLYGON;
import static org.apache.asterix.om.types.ATypeTag.RECTANGLE;
import static org.apache.asterix.om.types.ATypeTag.VALUE_TYPE_MAPPING;

import java.util.EnumSet;

import org.apache.asterix.dataflow.data.common.ILogicalBinaryComparator;
import org.apache.asterix.dataflow.data.nontagged.serde.ADateSerializerDeserializer;
import org.apache.asterix.dataflow.data.nontagged.serde.ADateTimeSerializerDeserializer;
import org.apache.asterix.dataflow.data.nontagged.serde.ADayTimeDurationSerializerDeserializer;
import org.apache.asterix.dataflow.data.nontagged.serde.ATimeSerializerDeserializer;
import org.apache.asterix.dataflow.data.nontagged.serde.AYearMonthDurationSerializerDeserializer;
import org.apache.asterix.om.base.IAObject;
import org.apache.asterix.om.types.ATypeTag;
import org.apache.asterix.om.types.hierachy.ATypeHierarchy;
import org.apache.hyracks.api.exceptions.HyracksDataException;
import org.apache.hyracks.data.std.api.IPointable;
import org.apache.hyracks.data.std.primitive.BooleanPointable;
import org.apache.hyracks.data.std.primitive.ByteArrayPointable;
import org.apache.hyracks.data.std.primitive.UTF8StringPointable;

public final class LogicalScalarBinaryComparator implements ILogicalBinaryComparator {

    private static final EnumSet<ATypeTag> INEQUALITY_UNDEFINED_TYPES =
            EnumSet.of(DURATION, INTERVAL, LINE, POINT, POINT3D, POLYGON, CIRCLE, RECTANGLE);
    private final boolean isEquality;

    LogicalScalarBinaryComparator(boolean isEquality) {
        this.isEquality = isEquality;
    }

    @Override
    public Result compare(IPointable left, IPointable right) throws HyracksDataException {
        ATypeTag leftTag = VALUE_TYPE_MAPPING[left.getByteArray()[left.getStartOffset()]];
        ATypeTag rightTag = VALUE_TYPE_MAPPING[right.getByteArray()[right.getStartOffset()]];
        Result comparisonResult = ComparatorUtil.returnMissingOrNullOrMismatch(leftTag, rightTag);
        if (comparisonResult != null) {
            return comparisonResult;
        }
        if (comparisonUndefined(leftTag, rightTag, isEquality)) {
            return Result.INCOMPARABLE;
        }
        // compare number if one of args is number since compatibility has already been checked above
        if (ATypeHierarchy.getTypeDomain(leftTag) == ATypeHierarchy.Domain.NUMERIC) {
            return ComparatorUtil.compareNumbers(leftTag, left, rightTag, right);
        }

        // comparing non-numeric
        // throw an exception if !=, the assumption here is only numeric types are compatible with each other
        if (leftTag != rightTag) {
            throw new IllegalStateException("Two different non-numeric tags but they are compatible");
        }

        byte[] leftBytes = left.getByteArray();
        byte[] rightBytes = right.getByteArray();
        int leftStart = left.getStartOffset() + 1;
        int rightStart = right.getStartOffset() + 1;
        int leftLen = left.getLength() - 1;
        int rightLen = right.getLength() - 1;

        int result;
        switch (leftTag) {
            case BOOLEAN:
                result = BooleanPointable.compare(leftBytes, leftStart, leftLen, rightBytes, rightStart, rightLen);
                break;
            case STRING:
                result = UTF8StringPointable.compare(leftBytes, leftStart, leftLen, rightBytes, rightStart, rightLen);
                break;
            case YEARMONTHDURATION:
                result = Integer.compare(AYearMonthDurationSerializerDeserializer.getYearMonth(leftBytes, leftStart),
                        AYearMonthDurationSerializerDeserializer.getYearMonth(rightBytes, rightStart));
                break;
            case TIME:
                result = Integer.compare(ATimeSerializerDeserializer.getChronon(leftBytes, leftStart),
                        ATimeSerializerDeserializer.getChronon(rightBytes, rightStart));
                break;
            case DATE:
                result = Integer.compare(ADateSerializerDeserializer.getChronon(leftBytes, leftStart),
                        ADateSerializerDeserializer.getChronon(rightBytes, rightStart));
                break;
            case DAYTIMEDURATION:
                result = Long.compare(ADayTimeDurationSerializerDeserializer.getDayTime(leftBytes, leftStart),
                        ADayTimeDurationSerializerDeserializer.getDayTime(rightBytes, rightStart));
                break;
            case DATETIME:
                result = Long.compare(ADateTimeSerializerDeserializer.getChronon(leftBytes, leftStart),
                        ADateTimeSerializerDeserializer.getChronon(rightBytes, rightStart));
                break;
            case CIRCLE:
                result = ACirclePartialBinaryComparatorFactory.compare(leftBytes, leftStart, leftLen, rightBytes,
                        rightStart, rightLen);
                break;
            case LINE:
                result = ALinePartialBinaryComparatorFactory.compare(leftBytes, leftStart, leftLen, rightBytes,
                        rightStart, rightLen);
                break;
            case POINT:
                result = APointPartialBinaryComparatorFactory.compare(leftBytes, leftStart, leftLen, rightBytes,
                        rightStart, rightLen);
                break;
            case POINT3D:
                result = APoint3DPartialBinaryComparatorFactory.compare(leftBytes, leftStart, leftLen, rightBytes,
                        rightStart, rightLen);
                break;
            case POLYGON:
                result = APolygonPartialBinaryComparatorFactory.compare(leftBytes, leftStart, leftLen, rightBytes,
                        rightStart, rightLen);
                break;
            case DURATION:
                result = ADurationPartialBinaryComparatorFactory.compare(leftBytes, leftStart, leftLen, rightBytes,
                        rightStart, rightLen);
                break;
            case INTERVAL:
                result = AIntervalAscPartialBinaryComparatorFactory.compare(leftBytes, leftStart, leftLen, rightBytes,
                        rightStart, rightLen);
                break;
            case RECTANGLE:
                result = ARectanglePartialBinaryComparatorFactory.compare(leftBytes, leftStart, leftLen, rightBytes,
                        rightStart, rightLen);
                break;
            case BINARY:
                result = ByteArrayPointable.compare(leftBytes, leftStart, leftLen, rightBytes, rightStart, rightLen);
                break;
            case UUID:
                result = AUUIDPartialBinaryComparatorFactory.compare(leftBytes, leftStart, leftLen, rightBytes,
                        rightStart, rightLen);
                break;
            default:
                return Result.NULL;
        }
        return ILogicalBinaryComparator.asResult(result);
    }

    @Override
    public Result compare(IPointable left, IAObject rightConstant) {
        // TODO(ali): currently defined for numbers only
        ATypeTag leftTag = VALUE_TYPE_MAPPING[left.getByteArray()[left.getStartOffset()]];
        ATypeTag rightTag = rightConstant.getType().getTypeTag();
        Result comparisonResult = ComparatorUtil.returnMissingOrNullOrMismatch(leftTag, rightTag);
        if (comparisonResult != null) {
            return comparisonResult;
        }
        if (comparisonUndefined(leftTag, rightTag, isEquality)) {
            return Result.NULL;
        }
        if (ATypeHierarchy.getTypeDomain(leftTag) == ATypeHierarchy.Domain.NUMERIC) {
            return ComparatorUtil.compareNumWithConstant(leftTag, left, rightConstant);
        }
        return Result.NULL;
    }

    @Override
    public Result compare(IAObject leftConstant, IPointable right) {
        // TODO(ali): currently defined for numbers only
        Result result = compare(right, leftConstant);
        if (result == Result.LT) {
            return Result.GT;
        } else if (result == Result.GT) {
            return Result.LT;
        }
        return result;
    }

    @Override
    public Result compare(IAObject leftConstant, IAObject rightConstant) {
        // TODO(ali): currently defined for numbers only
        ATypeTag leftTag = leftConstant.getType().getTypeTag();
        ATypeTag rightTag = rightConstant.getType().getTypeTag();
        Result comparisonResult = ComparatorUtil.returnMissingOrNullOrMismatch(leftTag, rightTag);
        if (comparisonResult != null) {
            return comparisonResult;
        }
        if (comparisonUndefined(leftTag, rightTag, isEquality)) {
            return Result.NULL;
        }
        if (ATypeHierarchy.getTypeDomain(leftTag) == ATypeHierarchy.Domain.NUMERIC) {
            return ComparatorUtil.compareConstants(leftConstant, rightConstant);
        }
        return Result.NULL;
    }

    private static boolean comparisonUndefined(ATypeTag leftTag, ATypeTag rightTag, boolean isEquality) {
        return !isEquality
                && (INEQUALITY_UNDEFINED_TYPES.contains(leftTag) || INEQUALITY_UNDEFINED_TYPES.contains(rightTag));
    }
}
