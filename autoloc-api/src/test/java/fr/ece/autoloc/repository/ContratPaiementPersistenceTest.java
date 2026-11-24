package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.CategorieVehicule;
import fr.ece.autoloc.domain.Client;
import fr.ece.autoloc.domain.Contrat;
import fr.ece.autoloc.domain.ModePaiement;
import fr.ece.autoloc.domain.Paiement;
import fr.ece.autoloc.domain.Reservation;
import fr.ece.autoloc.domain.StatutReservation;
import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Vehicule;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie sur une vraie base (H2) les choix de cascade, d'orphanRemoval et de chargement
 * de la chaîne Client -> Reservation -> Contrat -> Paiement.
 */
@DataJpaTest
@ActiveProfiles("test")
class ContratPaiementPersistenceTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ContratRepository contratRepository;

    private Long idContrat;

    @BeforeEach
    void enregistrerUnContratAvecDeuxPaiements() {
        Vehicule vehicule = new Vehicule();
        vehicule.setImmatriculation("ZZ-001-ZZ");
        vehicule.setMarque("Renault");
        vehicule.setModele("Clio");
        vehicule.setCategorie(CategorieVehicule.CITADINE);
        vehicule.setTarifJournalier(new BigDecimal("39.90"));
        vehicule.setStatut(StatutVehicule.LOUE);
        entityManager.persist(vehicule);

        Client client = new Client();
        client.setNom("Durand");
        client.setPrenom("Léa");
        client.setEmail("lea.durand@example.com");
        client.setNumPermis("PERMIS-TEST-1");
        client.setDateInscription(LocalDate.now());

        Reservation reservation = new Reservation();
        reservation.setDateDebut(LocalDate.now());
        reservation.setDateFin(LocalDate.now().plusDays(2));
        reservation.setStatut(StatutReservation.CONFIRMEE);
        reservation.setVehicule(vehicule);
        client.ajouterReservation(reservation);

        Contrat contrat = new Contrat();
        contrat.setDateSignature(LocalDate.now());
        contrat.setMontantTotal(new BigDecimal("79.80"));
        contrat.setValide(true);
        reservation.attacherContrat(contrat);
        contrat.ajouterPaiement(paiement("30.00"));
        contrat.ajouterPaiement(paiement("49.80"));

        clientRepository.save(client);
        entityManager.flush();
        entityManager.clear();
        idContrat = contrat.getIdContrat();
    }

    @Test
    void sauvegarderLeClientEnregistreToutLaChaineParCascade() {
        assertThat(compter("Reservation")).isEqualTo(1);
        assertThat(compter("Contrat")).isEqualTo(1);
        assertThat(compter("Paiement")).isEqualTo(2);
    }

    @Test
    void retirerUnPaiementDuContratLeSupprimeEnBase() {
        Contrat contrat = contratRepository.findWithPaiementsByIdContrat(idContrat).orElseThrow();

        contrat.retirerPaiement(contrat.getPaiements().get(0));
        entityManager.flush();

        assertThat(compter("Paiement")).isEqualTo(1);
    }

    @Test
    void supprimerUnContratSupprimeSesPaiements() {
        Contrat contrat = entityManager.find(Contrat.class, idContrat);

        entityManager.remove(contrat);
        entityManager.flush();

        assertThat(compter("Contrat")).isZero();
        assertThat(compter("Paiement")).isZero();
    }

    @Test
    void lesPaiementsNeSontChargesQueSurDemande() {
        PersistenceUnitUtil persistenceUnitUtil = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();

        Contrat contratSeul = contratRepository.findById(idContrat).orElseThrow();
        assertThat(persistenceUnitUtil.isLoaded(contratSeul, "paiements")).isFalse();

        entityManager.clear();
        Contrat contratAvecPaiements = contratRepository.findWithPaiementsByIdContrat(idContrat).orElseThrow();
        assertThat(persistenceUnitUtil.isLoaded(contratAvecPaiements, "paiements")).isTrue();
        assertThat(contratAvecPaiements.getPaiements()).hasSize(2);
    }

    private long compter(String entite) {
        return entityManager.createQuery("select count(e) from " + entite + " e", Long.class).getSingleResult();
    }

    private static Paiement paiement(String montant) {
        Paiement paiement = new Paiement();
        paiement.setMontant(new BigDecimal(montant));
        paiement.setDatePaiement(LocalDateTime.now());
        paiement.setModePaiement(ModePaiement.CARTE);
        return paiement;
    }
}
