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

import java.util.EnumSet;

import org.apache.asterix.dataflow.data.common.ILogicalBinaryComparator;
import org.apache.asterix.dataflow.data.nontagged.serde.ADateSerializerDeserializer;
import org.apache.asterix.dataflow.data.nontagged.serde.ADateTimeSerializerDeserializer;
import org.apache.asterix.dataflow.data.nontagged.serde.ADayTimeDurationSerializerDeserializer;
import org.apache.asterix.dataflow.data.nontagged.serde.ATimeSerializerDeserializer;
import org.apache.asterix.dataflow.data.nontagged.serde.AYearMonthDurationSerializerDeserializer;
import org.apache.asterix.formats.nontagged.BinaryComparatorFactoryProvider;
import org.apache.asterix.om.base.IAObject;
import org.apache.asterix.om.types.ATypeTag;
import org.apache.asterix.om.types.EnumDeserializer;
import org.apache.hyracks.api.dataflow.value.IBinaryComparator;
import org.apache.hyracks.api.exceptions.HyracksDataException;
import org.apache.hyracks.data.std.accessors.PointableBinaryComparatorFactory;
import org.apache.hyracks.data.std.primitive.ByteArrayPointable;

public class LogicalScalarBinaryComparator implements ILogicalBinaryComparator {

    private static final EnumSet<ATypeTag> INEQUALITY_UNDEFINED_TYPES =
            EnumSet.of(DURATION, INTERVAL, LINE, POINT, POINT3D, POLYGON, CIRCLE, RECTANGLE);

    private final IBinaryComparator strBinaryComp =
            BinaryComparatorFactoryProvider.UTF8STRING_POINTABLE_INSTANCE.createBinaryComparator();
    private final IBinaryComparator circleBinaryComp =
            ACirclePartialBinaryComparatorFactory.INSTANCE.createBinaryComparator();
    private final IBinaryComparator durationBinaryComp =
            ADurationPartialBinaryComparatorFactory.INSTANCE.createBinaryComparator();
    private final IBinaryComparator intervalBinaryComp =
            AIntervalAscPartialBinaryComparatorFactory.INSTANCE.createBinaryComparator();
    private final IBinaryComparator lineBinaryComparator =
            ALinePartialBinaryComparatorFactory.INSTANCE.createBinaryComparator();
    private final IBinaryComparator pointBinaryComparator =
            APointPartialBinaryComparatorFactory.INSTANCE.createBinaryComparator();
    private final IBinaryComparator point3DBinaryComparator =
            APoint3DPartialBinaryComparatorFactory.INSTANCE.createBinaryComparator();
    private final IBinaryComparator polygonBinaryComparator =
            APolygonPartialBinaryComparatorFactory.INSTANCE.createBinaryComparator();
    private final IBinaryComparator rectangleBinaryComparator =
            ARectanglePartialBinaryComparatorFactory.INSTANCE.createBinaryComparator();
    private final IBinaryComparator uuidBinaryComparator =
            AUUIDPartialBinaryComparatorFactory.INSTANCE.createBinaryComparator();
    private final IBinaryComparator byteArrayComparator =
            new PointableBinaryComparatorFactory(ByteArrayPointable.FACTORY).createBinaryComparator();

    private final boolean isEquality;

    public LogicalScalarBinaryComparator(boolean isEquality) {
        this.isEquality = isEquality;
    }

    @SuppressWarnings("squid:S1226") // asking for introducing a new variable for incremented local variables
    @Override
    public Result compare(byte[] leftBytes, int leftStart, int leftLen, byte[] rightBytes, int rightStart, int rightLen)
            throws HyracksDataException {
        ATypeTag leftTag = EnumDeserializer.ATYPETAGDESERIALIZER.deserialize(leftBytes[leftStart]);
        ATypeTag rightTag = EnumDeserializer.ATYPETAGDESERIALIZER.deserialize(rightBytes[rightStart]);
        Result comparisonResult = LogicalComparatorUtil.returnMissingOrNullOrMismatch(leftTag, rightTag);
        if (comparisonResult != null) {
            return comparisonResult;
        }
        if (comparisonUndefined(leftTag, rightTag, isEquality)) {
            return Result.NULL;
        }
        // compare number if one of args is number
        comparisonResult =
                LogicalComparatorUtil.compareNumbers(leftTag, leftBytes, leftStart, rightTag, rightBytes, rightStart);
        if (comparisonResult != null) {
            return comparisonResult;
        }

        // comparing non-numeric
        // return null if !=, the assumption here is only numeric types are compatible with each other
        if (leftTag != rightTag) {
            return Result.NULL;
        }

        leftStart++;
        leftLen--;
        rightStart++;
        rightLen--;

        int result;
        switch (leftTag) {
            case BOOLEAN:
                result = Integer.compare(leftBytes[leftStart], rightBytes[rightStart]);
                break;
            case STRING:
                result = strBinaryComp.compare(leftBytes, leftStart, leftLen, rightBytes, rightStart, rightLen);
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
                result = circleBinaryComp.compare(leftBytes, leftStart, leftLen, rightBytes, rightStart, rightLen);
                break;
            case LINE:
                result = lineBinaryComparator.compare(leftBytes, leftStart, leftLen, rightBytes, rightStart, rightLen);
                break;
            case POINT:
                result = pointBinaryComparator.compare(leftBytes, leftStart, leftLen, rightBytes, rightStart, rightLen);
                break;
            case POINT3D:
                result = point3DBinaryComparator.compare(leftBytes, leftStart, leftLen, rightBytes, rightStart,
                        rightLen);
                break;
            case POLYGON:
                result = polygonBinaryComparator.compare(leftBytes, leftStart, leftLen, rightBytes, rightStart,
                        rightLen);
                break;
            case DURATION:
                result = durationBinaryComp.compare(leftBytes, leftStart, leftLen, rightBytes, rightStart, rightLen);
                break;
            case INTERVAL:
                result = intervalBinaryComp.compare(leftBytes, leftStart, leftLen, rightBytes, rightStart, rightLen);
                break;
            case RECTANGLE:
                result = rectangleBinaryComparator.compare(leftBytes, leftStart, leftLen, rightBytes, rightStart,
                        rightLen);
                break;
            case BINARY:
                result = byteArrayComparator.compare(leftBytes, leftStart, leftLen, rightBytes, rightStart, rightLen);
                break;
            case UUID:
                result = uuidBinaryComparator.compare(leftBytes, leftStart, leftLen, rightBytes, rightStart, rightLen);
                break;
            default:
                return Result.NULL;
        }
        return ILogicalBinaryComparator.asResult(result);
    }

    @Override
    public Result compare(byte[] leftBytes, int leftStart, int leftLen, IAObject rightConstant) {
        // TODO(ali): currently defined for numbers only
        ATypeTag leftTag = EnumDeserializer.ATYPETAGDESERIALIZER.deserialize(leftBytes[leftStart]);
        ATypeTag rightTag = rightConstant.getType().getTypeTag();
        Result comparisonResult = LogicalComparatorUtil.returnMissingOrNullOrMismatch(leftTag, rightTag);
        if (comparisonResult != null) {
            return comparisonResult;
        }
        if (comparisonUndefined(leftTag, rightTag, isEquality)) {
            return Result.NULL;
        }
        comparisonResult = LogicalComparatorUtil.compareNumWithConstant(leftTag, leftBytes, leftStart, rightConstant);
        if (comparisonResult != null) {
            return comparisonResult;
        }
        return Result.NULL;
    }

    @Override
    public Result compare(IAObject leftConstant, byte[] rightBytes, int rightStart, int rightLen) {
        // TODO(ali): currently defined for numbers only
        Result result = compare(rightBytes, rightStart, rightLen, leftConstant);
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
        Result comparisonResult = LogicalComparatorUtil.returnMissingOrNullOrMismatch(leftTag, rightTag);
        if (comparisonResult != null) {
            return comparisonResult;
        }
        if (comparisonUndefined(leftTag, rightTag, isEquality)) {
            return Result.NULL;
        }
        comparisonResult = LogicalComparatorUtil.compareConstants(leftConstant, rightConstant);
        if (comparisonResult != null) {
            return comparisonResult;
        }
        return Result.NULL;
    }

    private static boolean comparisonUndefined(ATypeTag leftTag, ATypeTag rightTag, boolean isEquality) {
        return !isEquality
                && (INEQUALITY_UNDEFINED_TYPES.contains(leftTag) || INEQUALITY_UNDEFINED_TYPES.contains(rightTag));
    }
}
