package fr.ece.autoloc.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Propriétés de la période semi-ouverte [debut, fin) sur laquelle repose toute la détection de conflits. */
class PeriodeTest {

    private static final LocalDate J = LocalDate.of(2027, 3, 10);

    private static Periode p(int debut, int fin) {
        return new Periode(J.plusDays(debut), J.plusDays(fin));
    }

    @Test
    void leNombreDeJoursEstLaDifferenceDesDates() {
        assertThat(p(0, 1).jours()).isEqualTo(1);
        assertThat(p(0, 3).jours()).isEqualTo(3);
    }

    @Test
    void uneFinEgaleOuAnterieureAuDebutEstRefusee() {
        assertThatThrownBy(() -> p(0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> p(2, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    /** Réservation existante [0, 3) comparée à des demandes placées tout autour. */
    @ParameterizedTest(name = "[{0}, {1}) chevauche [0, 3) ? {2}")
    @CsvSource({
            "-3, -1, false",  // entièrement avant
            "-2,  0, false",  // se termine le jour du départ : pas de conflit
            "-2,  1, true",   // déborde sur le début
            " 0,  3, true",   // identique
            " 1,  2, true",   // incluse
            "-1,  4, true",   // englobante
            " 2,  5, true",   // déborde sur la fin
            " 3,  5, false",  // commence le jour du retour : pas de conflit
            " 4,  6, false"   // entièrement après
    })
    void detecteLesChevauchements(int debut, int fin, boolean attendu) {
        Periode existante = p(0, 3);
        Periode demandee = p(debut, fin);

        assertThat(demandee.chevauche(existante)).isEqualTo(attendu);
        assertThat(existante.chevauche(demandee)).as("la relation est symétrique").isEqualTo(attendu);
    }

    @Test
    void contientLeJourDeDepartMaisPasLeJourDeRetour() {
        Periode periode = p(0, 3);

        assertThat(periode.contient(J)).isTrue();
        assertThat(periode.contient(J.plusDays(2))).isTrue();
        assertThat(periode.contient(J.plusDays(3))).isFalse();
        assertThat(periode.contient(J.minusDays(1))).isFalse();
    }
}
