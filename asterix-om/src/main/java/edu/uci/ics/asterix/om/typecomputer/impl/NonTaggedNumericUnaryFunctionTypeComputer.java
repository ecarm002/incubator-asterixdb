/*
 * Numeric Unary Functions like abs
 * Author : Xiaoyu Ma@UC Irvine
 * 01/30/2012
 */
package edu.uci.ics.asterix.om.typecomputer.impl;

import edu.uci.ics.asterix.om.typecomputer.base.IResultTypeComputer;
import edu.uci.ics.asterix.om.types.ATypeTag;
import edu.uci.ics.asterix.om.types.AUnionType;
import edu.uci.ics.asterix.om.types.BuiltinType;
import edu.uci.ics.asterix.om.types.IAType;
import edu.uci.ics.asterix.om.util.NonTaggedFormatUtil;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.ILogicalExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.AbstractFunctionCallExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.IVariableTypeEnvironment;
import edu.uci.ics.hyracks.algebricks.core.algebra.metadata.IMetadataProvider;
import edu.uci.ics.hyracks.algebricks.core.api.exceptions.AlgebricksException;
import edu.uci.ics.hyracks.algebricks.core.api.exceptions.NotImplementedException;
import java.util.ArrayList;
import java.util.List;

public class NonTaggedNumericUnaryFunctionTypeComputer implements IResultTypeComputer {

    private static final String errMsg = "Arithmetic operations are not implemented for ";
    public static final NonTaggedNumericUnaryFunctionTypeComputer INSTANCE =
            new NonTaggedNumericUnaryFunctionTypeComputer();

    private NonTaggedNumericUnaryFunctionTypeComputer() {
    }

    @Override
    public IAType computeType(ILogicalExpression expression, IVariableTypeEnvironment env,
            IMetadataProvider<?, ?> metadataProvider) throws AlgebricksException {
        AbstractFunctionCallExpression fce = (AbstractFunctionCallExpression) expression;
        ILogicalExpression arg1 = fce.getArguments().get(0).getValue();

        IAType t = (IAType) env.getType(arg1);
        ATypeTag tag = t.getTypeTag();

        if (tag == ATypeTag.UNION
                && NonTaggedFormatUtil.isOptionalField((AUnionType) env.getType(arg1))) {
            return (IAType) env.getType(arg1);
        }
        
        List<IAType> unionList = new ArrayList<IAType>();
        unionList.add(BuiltinType.ANULL);
        switch (tag) {
            case INT8:
                unionList.add(BuiltinType.AINT8);
                break;                
            case INT16:
                unionList.add(BuiltinType.AINT16);
                break;                 
            case INT32:
                unionList.add(BuiltinType.AINT32);
                break;                 
            case INT64:
                unionList.add(BuiltinType.AINT64);
                break;                 
            case FLOAT:
                unionList.add(BuiltinType.AFLOAT);
                break;                 
            case DOUBLE:
                unionList.add(BuiltinType.ADOUBLE);
                break;
            case NULL:
                return BuiltinType.ANULL;
            default: {
                throw new NotImplementedException(errMsg + t.getTypeName());
            }
        }

        return new AUnionType(unionList, "NumericUnaryFuncionsResult");
    }
}
