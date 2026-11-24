package fr.ece.autoloc.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Active {@code @Async} (envoi des e-mails en tâche de fond, sur l'exécuteur fourni par Spring Boot). */
@Configuration
@EnableAsync
public class AsynchroneConfig {
}
