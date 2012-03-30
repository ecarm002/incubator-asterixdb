package edu.uci.ics.asterix.runtime.evaluators.functions;

import edu.uci.ics.asterix.formats.nontagged.AqlSerializerDeserializerProvider;
import edu.uci.ics.asterix.om.base.ABoolean;
import edu.uci.ics.asterix.om.base.AString;
import edu.uci.ics.asterix.om.types.ATypeTag;
import edu.uci.ics.asterix.om.types.BuiltinType;
import edu.uci.ics.hyracks.algebricks.core.algebra.runtime.base.IEvaluator;
import edu.uci.ics.hyracks.algebricks.core.algebra.runtime.base.IEvaluatorFactory;
import edu.uci.ics.hyracks.algebricks.core.api.exceptions.AlgebricksException;
import edu.uci.ics.hyracks.api.dataflow.value.ISerializerDeserializer;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.ArrayBackedValueStorage;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.IFrameTupleReference;
import java.io.DataOutput;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 *
 * @author Xiaoyu Ma
 */
public abstract class AbstractTripleStringBoolEval implements IEvaluator {

    private DataOutput dout;
    private final static byte SER_NULL_TYPE_TAG = ATypeTag.NULL.serialize();
    private ArrayBackedValueStorage array0 = new ArrayBackedValueStorage();
    private ArrayBackedValueStorage array1 = new ArrayBackedValueStorage();
    private ArrayBackedValueStorage array2 = new ArrayBackedValueStorage();
    private IEvaluator eval0;
    private IEvaluator eval1;
    private IEvaluator eval2;
    @SuppressWarnings("unchecked")
    private ISerializerDeserializer boolSerde = AqlSerializerDeserializerProvider.INSTANCE.getSerializerDeserializer(BuiltinType.ABOOLEAN);

    public AbstractTripleStringBoolEval(DataOutput dout, IEvaluatorFactory eval0,
            IEvaluatorFactory eval1, IEvaluatorFactory eval2) throws AlgebricksException {
        this.dout = dout;
        this.eval0 = eval0.createEvaluator(array0);
        this.eval1 = eval1.createEvaluator(array1);
        this.eval2 = eval2.createEvaluator(array2);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void evaluate(IFrameTupleReference tuple) throws AlgebricksException {
        array0.reset();
        eval0.evaluate(tuple);
        array1.reset();
        eval1.evaluate(tuple);
        array2.reset();
        eval2.evaluate(tuple);

        try {
            if (array0.getBytes()[0] == SER_NULL_TYPE_TAG
                    && array1.getBytes()[0] == SER_NULL_TYPE_TAG) {
                boolSerde.serialize(ABoolean.TRUE, dout);
                return;
            } else if (array0.getBytes()[0] == SER_NULL_TYPE_TAG
                    || array1.getBytes()[0] == SER_NULL_TYPE_TAG) {
                boolSerde.serialize(ABoolean.FALSE, dout);
                return;
            }
        } catch (HyracksDataException e) {
            throw new AlgebricksException(e);
        }

        byte[] b0 = array0.getBytes();
        byte[] b1 = array1.getBytes();
        byte[] b2 = array2.getBytes();

        int len0 = array0.getLength();
        int len1 = array1.getLength();
        int len2 = array2.getLength();

        int s0 = array0.getStartIndex();
        int s1 = array1.getStartIndex();
        int s2 = array2.getStartIndex();

        ABoolean res = compute(b0, len0, s0,
                b1, len1, s1, b2, len2, s2,
                array0, array1) ? ABoolean.TRUE : ABoolean.FALSE;
        try {
            boolSerde.serialize(res, dout);
        } catch (HyracksDataException e) {
            throw new AlgebricksException(e);
        }
    }

    protected abstract boolean compute(byte[] b0, int l0, int s0,
            byte[] b1, int l1, int s1, byte[] b2, int l2, int s2,
            ArrayBackedValueStorage array0, ArrayBackedValueStorage array1) throws AlgebricksException;

    protected String toRegex(AString pattern) {
        StringBuilder sb = new StringBuilder();
        String str = pattern.getStringValue();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '\\' && (i < str.length() - 1)
                    && (str.charAt(i + 1) == '_' || str.charAt(i + 1) == '%')) {
                sb.append(str.charAt(i + 1));
                ++i;
            } else if (c == '%') {
                sb.append(".*");
            } else if (c == '_') {
                sb.append(".");
            } else {
                if (Arrays.binarySearch(reservedRegexChars, c) >= 0) {
                    sb.append('\\');
                }
                sb.append(c);
            }
        }
        return sb.toString();
    }
    
    protected int toFlag(AString pattern) {
        String str = pattern.getStringValue();
        int flag = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch(c) {
                case 's':
                    flag |= Pattern.DOTALL;
                    break;
                case 'm':
                    flag |= Pattern.MULTILINE;
                    break;
                case 'i':
                    flag |= Pattern.CASE_INSENSITIVE;
                    break;
                case 'x':
                    flag |= Pattern.COMMENTS;
                    break;
            }
        }
        return flag;
    }    
    
    private final static char[] reservedRegexChars = new char[]{'\\', '(', ')', '[', ']', '{', '}', '.', '^', '$', '*', '|'};

    static {
        Arrays.sort(reservedRegexChars);
    }
}
