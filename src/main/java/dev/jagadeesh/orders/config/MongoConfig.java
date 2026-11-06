package dev.jagadeesh.orders.config;

import org.bson.types.Decimal128;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.math.BigDecimal;
import java.util.List;

@Configuration
public class MongoConfig {

    /** Store money as Decimal128 (exact) instead of the default string, so $sum works in aggregations. */
    @Bean
    public MongoCustomConversions customConversions() {
        return new MongoCustomConversions(List.of(new BigDecimalToDecimal128(), new Decimal128ToBigDecimal()));
    }

    @WritingConverter
    static class BigDecimalToDecimal128 implements Converter<BigDecimal, Decimal128> {
        @Override
        public Decimal128 convert(BigDecimal source) {
            return new Decimal128(source);
        }
    }

    @ReadingConverter
    static class Decimal128ToBigDecimal implements Converter<Decimal128, BigDecimal> {
        @Override
        public BigDecimal convert(Decimal128 source) {
            return source.bigDecimalValue();
        }
    }
}
