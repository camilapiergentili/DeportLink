package com.deportlink.DeportLink.persistence.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Convert;
import jakarta.persistence.Converter;

import java.time.Duration;

@Converter(autoApply = true)
public class DurationConverter implements AttributeConverter<Duration, Long> {
    @Override
    public Long convertToDatabaseColumn(Duration duration) {
        return (duration == null) ? null : duration.toMinutes();
    }

    @Override
    public Duration convertToEntityAttribute(Long aLong) {
        return (aLong == null) ? null : Duration.ofMinutes(aLong);
    }
}
