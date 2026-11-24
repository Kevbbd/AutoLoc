package fr.ece.autoloc.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Machine à états de la réservation et invariant des paiements du contrat. */
class ReservationContratTest {

    private static final LocalDate AUJOURDHUI = LocalDate.of(2027, 3, 10);
    private static final LocalDateTime MAINTENANT = AUJOURDHUI.atTime(10, 0);

    private Reservation reservation;
    private Contrat contrat;

    @BeforeEach
    void reservationConfirmeeDeTroisJoursA50Euros() {
        reservation = new Reservation();
        reservation.setDateDebut(AUJOURDHUI.plusDays(5));
        reservation.setDateFin(AUJOURDHUI.plusDays(8));
        reservation.setStatut(StatutReservation.CONFIRMEE);
        contrat = new Contrat();
        contrat.setMontantTotal(new BigDecimal("150.00"));
        contrat.setValide(true);
        reservation.attacherContrat(contrat);
    }

    @Test
    void annulerRendLeContratInvalide() {
        reservation.annuler();

        assertThat(reservation.getStatut()).isEqualTo(StatutReservation.ANNULEE);
        assertThat(contrat.isValide()).isFalse();
        assertThat(reservation.estActive()).isFalse();
    }

    @Test
    void uneReservationAnnuleeOuTermineeNePeutPlusEtreAnnulee() {
        reservation.annuler();
        assertThatThrownBy(reservation::annuler).isInstanceOf(RegleMetierException.class);

        Reservation terminee = new Reservation();
        terminee.setStatut(StatutReservation.TERMINEE);
        assertThatThrownBy(terminee::annuler).isInstanceOf(RegleMetierException.class);
    }

    @Test
    void seuleUneReservationConfirmeePeutEtreTerminee() {
        reservation.terminer();
        assertThat(reservation.getStatut()).isEqualTo(StatutReservation.TERMINEE);

        assertThatThrownBy(reservation::terminer).isInstanceOf(RegleMetierException.class);
    }

    @Test
    void leClientPeutAnnulerJusquAuJourDuDepartInclus() {
        assertThat(reservation.estAnnulableParClient(AUJOURDHUI)).isTrue();
        assertThat(reservation.estAnnulableParClient(AUJOURDHUI.plusDays(5))).as("jour du départ").isTrue();
        assertThat(reservation.estAnnulableParClient(AUJOURDHUI.plusDays(6))).as("location commencée").isFalse();
    }

    @Test
    void couvreLesJoursDeLaLocationSeulementSiElleEstActive() {
        assertThat(reservation.couvre(AUJOURDHUI.plusDays(5))).isTrue();
        assertThat(reservation.couvre(AUJOURDHUI.plusDays(8))).as("jour du retour").isFalse();

        reservation.annuler();
        assertThat(reservation.couvre(AUJOURDHUI.plusDays(5))).isFalse();
    }

    @Test
    void lesPaiementsSAdditionnentJusquAuMontantTotal() {
        contrat.encaisser(new BigDecimal("50.00"), ModePaiement.CARTE, MAINTENANT);
        assertThat(contrat.montantPaye()).isEqualByComparingTo("50.00");
        assertThat(contrat.resteAPayer()).isEqualByComparingTo("100.00");

        Paiement solde = contrat.encaisser(new BigDecimal("100.00"), ModePaiement.VIREMENT, MAINTENANT);
        assertThat(solde.getContrat()).isSameAs(contrat);
        assertThat(contrat.resteAPayer()).isEqualByComparingTo("0");
    }

    @Test
    void unPaiementNePeutPasDepasserLeResteAPayer() {
        contrat.encaisser(new BigDecimal("140.00"), ModePaiement.CARTE, MAINTENANT);

        assertThatThrownBy(() -> contrat.encaisser(new BigDecimal("10.01"), ModePaiement.CARTE, MAINTENANT))
                .isInstanceOf(RegleMetierException.class);
        assertThat(contrat.getPaiements()).hasSize(1);
    }

    @Test
    void unPaiementDoitEtreStrictementPositif() {
        assertThatThrownBy(() -> contrat.encaisser(BigDecimal.ZERO, ModePaiement.CARTE, MAINTENANT))
                .isInstanceOf(RegleMetierException.class);
        assertThatThrownBy(() -> contrat.encaisser(new BigDecimal("-5"), ModePaiement.CARTE, MAINTENANT))
                .isInstanceOf(RegleMetierException.class);
    }

    @Test
    void unContratInvalideNAccepteAucunPaiement() {
        reservation.annuler();

        assertThatThrownBy(() -> contrat.encaisser(new BigDecimal("10.00"), ModePaiement.ESPECES, MAINTENANT))
                .isInstanceOf(RegleMetierException.class);
    }
}
