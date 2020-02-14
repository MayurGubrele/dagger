package com.gojek.daggers.postProcessors.longbow.validator;

import com.gojek.daggers.exception.DaggerConfigurationException;
import org.apache.commons.lang.StringUtils;

import java.util.Arrays;
import java.util.stream.Collectors;

public class LongbowValidator {
    private String[] columnNames;

    public LongbowValidator(String[] columnNames) {
        this.columnNames = columnNames;
    }

    public void validateLongbow(LongbowType longbowType) {
        String missingFields = Arrays
                .stream(longbowType.getMandatoryFields())
                .filter(field -> Arrays.stream(columnNames)
                        .noneMatch(columnName -> columnName.contains(field)))
                .collect(Collectors.joining(","));

        String wrongFields = Arrays.stream(longbowType.getInvalidFields())
                .filter(field -> Arrays.stream(columnNames)
                        .anyMatch(columnName -> columnName.contains(field)))
                .collect(Collectors.joining(","));

        if (StringUtils.isNotEmpty(missingFields))
            throw new DaggerConfigurationException("Missing required field: " + " in Longbow type : " + longbowType.getTypeValue());
        if (StringUtils.isNotEmpty(wrongFields))
            throw new DaggerConfigurationException("Invalid fields present : " + wrongFields + " in Longbow type : " + longbowType.getTypeValue());
    }
}