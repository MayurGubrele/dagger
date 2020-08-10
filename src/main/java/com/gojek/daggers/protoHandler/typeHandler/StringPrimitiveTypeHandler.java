package com.gojek.daggers.protoHandler.typeHandler;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeinfo.Types;

import java.util.ArrayList;
import java.util.List;

public class StringPrimitiveTypeHandler implements PrimitiveTypeHandler {
    private Descriptors.FieldDescriptor fieldDescriptor;

    public StringPrimitiveTypeHandler(Descriptors.FieldDescriptor fieldDescriptor) {
        this.fieldDescriptor = fieldDescriptor;
    }

    @Override
    public boolean canHandle() {
        return fieldDescriptor.getJavaType() == JavaType.STRING;
    }

    @Override
    public Object getValue(Object field) {
        return getValueOrDefault(field, "");
    }

    @Override
    public Object getArray(Object field) {
        List<String> inputValues = new ArrayList<>();
        if (field != null) inputValues = (List<String>) field;
        return inputValues.toArray(new String[]{});
    }

    @Override
    public TypeInformation getTypeInformation() {
        return Types.STRING;
    }

    @Override
    public TypeInformation getArrayType() {
        return Types.OBJECT_ARRAY(Types.STRING);
    }
}
