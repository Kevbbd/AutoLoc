package fr.ece.autoloc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class HorlogeConfig {

    /** Horloge du système, injectée partout où « aujourd'hui » intervient dans une règle métier. */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
