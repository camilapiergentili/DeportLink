package com.deportlink.deportlink.dto.request;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;
public class StrictIntegerDeserializer extends JsonDeserializer<Integer> {
    public Integer deserialize(JsonParser p, DeserializationContext c) throws IOException {
        if (!p.hasToken(JsonToken.VALUE_NUMBER_INT))
            return (Integer)c.handleUnexpectedToken(Integer.class, p);
        return p.getIntValue();
    }
}
