package fr.ece.autoloc.securite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/** Réglage du limiteur des formulaires publics : 30 envois par minute et par adresse IP par défaut. */
@Configuration
public class LimiteRequetesPubliques {

    @Bean
    public LimiteurDebit limiteurRequetesPubliques(Clock clock,
            @Value("${autoloc.securite.public.requetes-max-par-minute:30}") int maximum) {
        return new LimiteurDebit(maximum, Duration.ofMinutes(1), clock);
    }
}
