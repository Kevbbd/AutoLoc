package fr.ece.autoloc.service;

import fr.ece.autoloc.domain.Periode;
import fr.ece.autoloc.service.exception.RequeteInvalideException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Règles de dates et de prix, avec une horloge figée au 10 mars 2027. */
class ReglesLocationTest {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final LocalDate AUJOURDHUI = LocalDate.of(2027, 3, 10);

    private final ReglesLocation regles = new ReglesLocation(
            Clock.fixed(AUJOURDHUI.atStartOfDay(PARIS).plusHours(9).toInstant(), PARIS));

    @Test
    void accepteUnePeriodeQuiCommenceAujourdhui() {
        Periode periode = regles.periodeReservable(AUJOURDHUI, AUJOURDHUI.plusDays(1));

        assertThat(periode.jours()).isEqualTo(1);
    }

    @Test
    void refuseUneDateDeRetourQuiNSuitPasLeDepart() {
        assertThatThrownBy(() -> regles.periodeReservable(AUJOURDHUI.plusDays(2), AUJOURDHUI.plusDays(2)))
                .isInstanceOf(RequeteInvalideException.class)
                .extracting("champ").isEqualTo("dateFin");
    }

    @Test
    void refuseUnDepartDansLePasse() {
        assertThatThrownBy(() -> regles.periodeReservable(AUJOURDHUI.minusDays(1), AUJOURDHUI.plusDays(1)))
                .isInstanceOf(RequeteInvalideException.class)
                .extracting("champ").isEqualTo("dateDebut");
    }

    @Test
    void limiteLaDureeA30JoursExactement() {
        assertThat(regles.periodeReservable(AUJOURDHUI, AUJOURDHUI.plusDays(30)).jours()).isEqualTo(30);

        assertThatThrownBy(() -> regles.periodeReservable(AUJOURDHUI, AUJOURDHUI.plusDays(31)))
                .isInstanceOf(RequeteInvalideException.class);
    }

    @Test
    void limiteLAnticipationA365Jours() {
        LocalDate limite = AUJOURDHUI.plusDays(ReglesLocation.DELAI_MAX_RESERVATION_JOURS);
        assertThat(regles.periodeReservable(limite, limite.plusDays(1))).isNotNull();

        assertThatThrownBy(() -> regles.periodeReservable(limite.plusDays(1), limite.plusDays(2)))
                .isInstanceOf(RequeteInvalideException.class);
    }

    @Test
    void leMontantEstLeTarifMultiplieParLeNombreDeJours() {
        Periode troisJours = new Periode(AUJOURDHUI, AUJOURDHUI.plusDays(3));

        assertThat(regles.montant(new BigDecimal("39.90"), troisJours)).isEqualByComparingTo("119.70");
        assertThat(regles.montant(new BigDecimal("39.90"), troisJours).scale()).isEqualTo(2);
    }

    @Test
    void lHorlogeInjecteeFixeAujourdhui() {
        assertThat(regles.aujourdhui()).isEqualTo(AUJOURDHUI);
    }
}
