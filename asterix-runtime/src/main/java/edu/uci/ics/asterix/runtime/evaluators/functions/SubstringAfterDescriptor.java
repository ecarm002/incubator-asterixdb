package edu.uci.ics.asterix.runtime.evaluators.functions;

import edu.uci.ics.asterix.common.functions.FunctionConstants;
import edu.uci.ics.asterix.om.types.ATypeTag;
import edu.uci.ics.asterix.runtime.evaluators.base.AbstractScalarFunctionDynamicDescriptor;
import edu.uci.ics.hyracks.algebricks.core.algebra.functions.FunctionIdentifier;
import edu.uci.ics.hyracks.algebricks.core.algebra.runtime.base.IEvaluator;
import edu.uci.ics.hyracks.algebricks.core.algebra.runtime.base.IEvaluatorFactory;
import edu.uci.ics.hyracks.algebricks.core.api.exceptions.AlgebricksException;
import edu.uci.ics.hyracks.data.std.primitive.UTF8StringPointable;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.ArrayBackedValueStorage;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.IDataOutputProvider;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.IFrameTupleReference;
import java.io.DataOutput;
import java.io.IOException;

public class SubstringAfterDescriptor extends AbstractScalarFunctionDynamicDescriptor {

    private static final long serialVersionUID = 1L;
    private final static FunctionIdentifier FID = new FunctionIdentifier(FunctionConstants.ASTERIX_NS, "substring-after", 2,
            true);

    @Override
    public IEvaluatorFactory createEvaluatorFactory(final IEvaluatorFactory[] args) throws AlgebricksException {
        return new IEvaluatorFactory() {
            private static final long serialVersionUID = 1L;

            @Override
            public IEvaluator createEvaluator(final IDataOutputProvider output) throws AlgebricksException {
                return new IEvaluator() {

                    private DataOutput out = output.getDataOutput();
                    private ArrayBackedValueStorage array0 = new ArrayBackedValueStorage();
                    private ArrayBackedValueStorage array1 = new ArrayBackedValueStorage();
                    private IEvaluator evalString = args[0].createEvaluator(array0);
                    private IEvaluator evalPattern = args[1].createEvaluator(array1);
                    private final byte stt = ATypeTag.STRING.serialize();

                    @Override
                    public void evaluate(IFrameTupleReference tuple) throws AlgebricksException {
                        array0.reset();
                        evalString.evaluate(tuple);
                        byte[] src = array0.getBytes();
                        
                        array1.reset();
                        evalPattern.evaluate(tuple);
                        byte[] pattern = array1.getBytes();                       
                        
                        int srcLen = UTF8StringPointable.getUTFLen(src, 1);
                        int patternLen = UTF8StringPointable.getUTFLen(pattern, 1);
                        int posSrc = 3;
                        int posPattern = 3;
                       
                        int offset = 0;
                        int curSrcPos = 3; 
                        // boolean found = false;
                        while (posSrc - 3 < srcLen - patternLen) {
                            offset = 0;                        
                            while(posPattern + offset - 3 < patternLen && posSrc + offset - 3 < srcLen) {
                                char c1 = UTF8StringPointable.charAt(src, posSrc + offset);
                                char c2 = UTF8StringPointable.charAt(pattern, posPattern + offset);
                                if(c1 != c2)
                                    break;                                
                                offset++;
                            }
                            if(offset == patternLen) {
                                // found = true;
                                break;
                            }          
                            posSrc += UTF8StringPointable.charSize(src, posSrc);
                        }
                        
                        
                        posSrc += patternLen;
                        int substrByteLen = srcLen - posSrc + 3;
                        try {
                            out.writeByte(stt);
                            out.writeByte((byte) ((substrByteLen >>> 8) & 0xFF));
                            out.writeByte((byte) ((substrByteLen >>> 0) & 0xFF));
                            out.write(src, posSrc, substrByteLen);

                        } catch (IOException e) {
                            throw new AlgebricksException(e);
                        }
                    }
                };
            }
        };
    }

    @Override
    public FunctionIdentifier getIdentifier() {
        return FID;
    }

}
