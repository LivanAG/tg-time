package com.controlhorario.summary.calc;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Expone los servicios de cálculo (puros) como beans. */
@Configuration
public class CalcConfig {

    @Bean
    MonthSummaryService monthSummaryService() {
        return new MonthSummaryService();
    }

    @Bean
    PeriodSummaryService periodSummaryService() {
        return new PeriodSummaryService();
    }
}
