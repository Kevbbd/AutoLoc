package fr.ece.autoloc.securite;

import fr.ece.autoloc.support.HorlogeReglable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CompteurEchecsTest {

    private static final Duration QUINZE_MINUTES = Duration.ofMinutes(15);

    private HorlogeReglable horloge;
    private CompteurEchecs compteur;

    @BeforeEach
    void preparer() {
        horloge = new HorlogeReglable(Instant.parse("2027-03-10T09:00:00Z"));
        compteur = new CompteurEchecs(5, QUINZE_MINUTES, QUINZE_MINUTES, horloge);
    }

    @Test
    void leCinquiemeEchecBloquePendantQuinzeMinutes() {
        for (int i = 1; i <= 4; i++) {
            assertThat(compteur.echec("admin")).as("échec %d", i).isFalse();
            assertThat(compteur.blocageRestant("admin")).isEmpty();
        }

        assertThat(compteur.echec("admin")).as("5e échec : déclenche le blocage").isTrue();
        assertThat(compteur.blocageRestant("admin")).contains(QUINZE_MINUTES);

        horloge.avancer(Duration.ofMinutes(14));
        assertThat(compteur.blocageRestant("admin")).contains(Duration.ofMinutes(1));
        horloge.avancer(Duration.ofMinutes(1));
        assertThat(compteur.blocageRestant("admin")).isEmpty();
    }

    @Test
    void desEchecsPendantLeBlocageNeLeProlongentPas() {
        for (int i = 0; i < 5; i++) {
            compteur.echec("admin");
        }
        horloge.avancer(Duration.ofMinutes(10));
        assertThat(compteur.echec("admin")).isFalse();

        assertThat(compteur.blocageRestant("admin")).contains(Duration.ofMinutes(5));
    }

    @Test
    void lesEchecsAnciensSortentDeLaFenetre() {
        for (int i = 0; i < 4; i++) {
            compteur.echec("admin");
        }
        horloge.avancer(QUINZE_MINUTES);

        assertThat(compteur.echec("admin")).as("nouvelle fenêtre : 1er échec").isFalse();
        assertThat(compteur.blocageRestant("admin")).isEmpty();
    }

    @Test
    void uneReussiteRemetAZero() {
        for (int i = 0; i < 4; i++) {
            compteur.echec("admin");
        }
        compteur.reussite("admin");

        for (int i = 0; i < 4; i++) {
            assertThat(compteur.echec("admin")).isFalse();
        }
        assertThat(compteur.blocageRestant("admin")).isEmpty();
    }

    @Test
    void lesClesSontIndependantes() {
        for (int i = 0; i < 5; i++) {
            compteur.echec("admin");
        }

        assertThat(compteur.blocageRestant("admin")).isPresent();
        assertThat(compteur.blocageRestant("student")).isEmpty();
    }

    @Test
    void laMemoireResteBorneeFaceAUnFloodDIdentifiants() {
        for (int i = 0; i < CompteurEchecs.CLES_MAX; i++) {
            compteur.echec("inconnu-" + i);
        }
        horloge.avancer(QUINZE_MINUTES);
        compteur.echec("dernier");

        assertThat(compteur.clesSuivies()).isEqualTo(1);
    }
}
