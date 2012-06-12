package edu.uci.ics.asterix.runtime.evaluators.common;

import java.io.DataOutput;
import java.io.IOException;

import edu.uci.ics.asterix.builders.IAOrderedListBuilder;
import edu.uci.ics.asterix.builders.OrderedListBuilder;
import edu.uci.ics.asterix.om.types.AOrderedListType;
import edu.uci.ics.asterix.om.types.BuiltinType;
import edu.uci.ics.hyracks.algebricks.common.exceptions.AlgebricksException;
import edu.uci.ics.hyracks.algebricks.runtime.base.IEvaluator;
import edu.uci.ics.hyracks.algebricks.runtime.base.IEvaluatorFactory;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.ArrayBackedValueStorage;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.IDataOutputProvider;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.IFrameTupleReference;
import edu.uci.ics.hyracks.storage.am.invertedindex.tokenizers.IBinaryTokenizer;
import edu.uci.ics.hyracks.storage.am.invertedindex.tokenizers.IToken;
import edu.uci.ics.hyracks.storage.am.invertedindex.tokenizers.IntArray;

public class WordTokensEvaluator implements IEvaluator {
    protected final DataOutput out;
    protected final ArrayBackedValueStorage argOut = new ArrayBackedValueStorage();
    protected final IEvaluator stringEval;

    protected final IBinaryTokenizer tokenizer;

    protected final IntArray itemOffsets = new IntArray();
    protected final ArrayBackedValueStorage tokenBuffer = new ArrayBackedValueStorage();

    private IAOrderedListBuilder listBuilder = new OrderedListBuilder();
    private ArrayBackedValueStorage inputVal = new ArrayBackedValueStorage();
    private BuiltinType itemType;

    public WordTokensEvaluator(IEvaluatorFactory[] args, IDataOutputProvider output, IBinaryTokenizer tokenizer,
            BuiltinType itemType) throws AlgebricksException {
        out = output.getDataOutput();
        stringEval = args[0].createEvaluator(argOut);
        this.tokenizer = tokenizer;
        this.itemType = itemType;
    }

    @Override
    public void evaluate(IFrameTupleReference tuple) throws AlgebricksException {
        argOut.reset();
        stringEval.evaluate(tuple);
        byte[] bytes = argOut.getByteArray();
        tokenizer.reset(bytes, 0, argOut.getLength());
        tokenBuffer.reset();

        try {
            listBuilder.reset(new AOrderedListType(itemType, null));
            while (tokenizer.hasNext()) {
                inputVal.reset();
                tokenizer.next();
                IToken token = tokenizer.getToken();
                token.serializeToken(inputVal.getDataOutput());
                listBuilder.addItem(inputVal);
            }
            listBuilder.write(out, true);
        } catch (IOException e) {
            throw new AlgebricksException(e);
        }
    }
}
