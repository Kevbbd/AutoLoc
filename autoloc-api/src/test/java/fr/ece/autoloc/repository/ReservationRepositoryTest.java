package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.CategorieVehicule;
import fr.ece.autoloc.domain.Client;
import fr.ece.autoloc.domain.Reservation;
import fr.ece.autoloc.domain.StatutReservation;
import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Vehicule;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Vérifie en SQL réel que les requêtes de chevauchement respectent la sémantique [debut, fin). */
@DataJpaTest
@ActiveProfiles("test")
class ReservationRepositoryTest {

    private static final LocalDate J = LocalDate.now().plusDays(10);

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ReservationRepository reservationRepository;

    private Vehicule clio;
    private Vehicule duster;
    private Reservation existante;

    @BeforeEach
    void uneReservationConfirmeeDeJAJPlus3() {
        clio = vehicule("RR-001-AA");
        duster = vehicule("RR-002-BB");
        Client client = new Client();
        client.setNom("Test");
        client.setPrenom("Repo");
        client.setEmail("repo@example.com");
        client.setNumPermis("REPO-1");
        client.setDateInscription(LocalDate.now());
        entityManager.persist(client);

        existante = reservation(client, clio, 0, 3, StatutReservation.CONFIRMEE);
        reservation(client, duster, 0, 3, StatutReservation.ANNULEE);
        entityManager.flush();
        entityManager.clear();
    }

    @ParameterizedTest(name = "demande [J{0}, J{1}) en conflit ? {2}")
    @CsvSource({
            "-2, 0, false",
            "-2, 1, true",
            " 1, 2, true",
            " 2, 5, true",
            " 3, 5, false"
    })
    void detecteLesConflitsAuxBornes(int debut, int fin, boolean conflit) {
        Vehicule vehicule = entityManager.find(Vehicule.class, clio.getIdVehicule());

        boolean resultat = reservationRepository.existsByVehiculeAndStatutInAndDateDebutLessThanAndDateFinGreaterThan(
                vehicule, StatutReservation.ACTIFS, J.plusDays(fin), J.plusDays(debut));

        assertThat(resultat).isEqualTo(conflit);
    }

    @Test
    void ignoreLesReservationsAnnulees() {
        Vehicule vehicule = entityManager.find(Vehicule.class, duster.getIdVehicule());

        assertThat(reservationRepository.existsByVehiculeAndStatutInAndDateDebutLessThanAndDateFinGreaterThan(
                vehicule, StatutReservation.ACTIFS, J.plusDays(3), J)).isFalse();
    }

    @Test
    void listeLesVehiculesOccupesEnUneRequete() {
        assertThat(reservationRepository.findIdVehiculesOccupes(StatutReservation.ACTIFS, J.plusDays(1), J.plusDays(2)))
                .containsExactly(clio.getIdVehicule());
        assertThat(reservationRepository.findIdVehiculesOccupes(StatutReservation.ACTIFS, J.plusDays(3), J.plusDays(4)))
                .isEmpty();
    }

    @Test
    void exclutLaReservationCouranteQuandOnChercheUneAutreLocationCeJourLa() {
        Vehicule vehicule = entityManager.find(Vehicule.class, clio.getIdVehicule());

        assertThat(reservationRepository
                .existsByVehiculeAndStatutInAndDateDebutLessThanEqualAndDateFinGreaterThanAndIdReservationNot(
                        vehicule, StatutReservation.ACTIFS, J.plusDays(1), J.plusDays(1), existante.getIdReservation()))
                .isFalse();
        assertThat(reservationRepository
                .existsByVehiculeAndStatutInAndDateDebutLessThanEqualAndDateFinGreaterThanAndIdReservationNot(
                        vehicule, StatutReservation.ACTIFS, J.plusDays(1), J.plusDays(1), -1L))
                .isTrue();
    }

    @Test
    void unCodeEstAttribueALEnregistrement() {
        assertThat(existante.getCodeAcces()).matches("[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{10}");
    }

    @Test
    void consultationExigeLeCodeEtLeBonEmailSansTenirCompteDeLaCasse() {
        String code = existante.getCodeAcces();
        assertThat(reservationRepository.findByCodeAccesAndClientEmailIgnoreCase(code, "REPO@example.com")).isPresent();
        assertThat(reservationRepository.findByCodeAccesAndClientEmailIgnoreCase(code, "autre@example.com")).isEmpty();
        assertThat(reservationRepository.findByCodeAccesAndClientEmailIgnoreCase("2222222222", "REPO@example.com")).isEmpty();
    }

    private Vehicule vehicule(String immatriculation) {
        Vehicule vehicule = new Vehicule();
        vehicule.setImmatriculation(immatriculation);
        vehicule.setMarque("Test");
        vehicule.setModele("Repo");
        vehicule.setCategorie(CategorieVehicule.CITADINE);
        vehicule.setTarifJournalier(new BigDecimal("40.00"));
        vehicule.setStatut(StatutVehicule.DISPONIBLE);
        entityManager.persist(vehicule);
        return vehicule;
    }

    private Reservation reservation(Client client, Vehicule vehicule, int debut, int fin, StatutReservation statut) {
        Reservation reservation = new Reservation();
        reservation.setDateDebut(J.plusDays(debut));
        reservation.setDateFin(J.plusDays(fin));
        reservation.setStatut(statut);
        reservation.setVehicule(vehicule);
        reservation.setClient(client);
        entityManager.persist(reservation);
        return reservation;
    }
}
