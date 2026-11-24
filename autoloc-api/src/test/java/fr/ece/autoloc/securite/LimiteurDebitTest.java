package fr.ece.autoloc.securite;

import fr.ece.autoloc.support.HorlogeReglable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LimiteurDebitTest {

    private final HorlogeReglable horloge = new HorlogeReglable(Instant.parse("2027-03-10T09:00:00Z"));
    private final LimiteurDebit limiteur = new LimiteurDebit(3, Duration.ofMinutes(1), horloge);

    @Test
    void auDelaDuMaximumIlFautAttendreLaFenetreSuivante() {
        assertThat(limiteur.enregistrer("10.0.0.1")).isEmpty();
        assertThat(limiteur.enregistrer("10.0.0.1")).isEmpty();
        assertThat(limiteur.enregistrer("10.0.0.1")).isEmpty();

        horloge.avancer(Duration.ofSeconds(20));
        assertThat(limiteur.enregistrer("10.0.0.1")).contains(Duration.ofSeconds(40));

        horloge.avancer(Duration.ofSeconds(40));
        assertThat(limiteur.enregistrer("10.0.0.1")).as("nouvelle minute").isEmpty();
    }

    @Test
    void chaqueAdresseASonPropreQuota() {
        for (int i = 0; i < 3; i++) {
            limiteur.enregistrer("10.0.0.1");
        }

        assertThat(limiteur.enregistrer("10.0.0.1")).isPresent();
        assertThat(limiteur.enregistrer("10.0.0.2")).isEmpty();
    }
}
