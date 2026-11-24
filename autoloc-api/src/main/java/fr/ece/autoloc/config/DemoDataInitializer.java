package fr.ece.autoloc.config;

import fr.ece.autoloc.domain.Agence;
import fr.ece.autoloc.domain.CategorieVehicule;
import fr.ece.autoloc.domain.Client;
import fr.ece.autoloc.domain.Contrat;
import fr.ece.autoloc.domain.Employe;
import fr.ece.autoloc.domain.Equipement;
import fr.ece.autoloc.domain.Maintenance;
import fr.ece.autoloc.domain.ModePaiement;
import fr.ece.autoloc.domain.Paiement;
import fr.ece.autoloc.domain.Reservation;
import fr.ece.autoloc.domain.RoleEmploye;
import fr.ece.autoloc.domain.StatutReservation;
import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.repository.AgenceRepository;
import fr.ece.autoloc.repository.ClientRepository;
import fr.ece.autoloc.repository.EmployeRepository;
import fr.ece.autoloc.repository.EquipementRepository;
import fr.ece.autoloc.repository.VehiculeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Jeu de données de démonstration, chargé uniquement avec le profil "dev".
 * Les sauvegardes illustrent les choix de cascade documentés dans docs/fetch-strategy.md.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DemoDataInitializer implements CommandLineRunner {

    private final AgenceRepository agenceRepository;
    private final VehiculeRepository vehiculeRepository;
    private final EmployeRepository employeRepository;
    private final EquipementRepository equipementRepository;
    private final ClientRepository clientRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (agenceRepository.count() > 0) {
            log.info("Données de démonstration déjà présentes : chargement ignoré");
            return;
        }

        Equipement gps = equipement("GPS");
        Equipement siegeEnfant = equipement("Siège enfant");
        Equipement chainesNeige = equipement("Chaînes à neige");
        equipementRepository.saveAll(List.of(gps, siegeEnfant, chainesNeige));

        Agence agence = agenceRepository.save(agence());

        Vehicule clio = vehicule("AB-123-CD", "Renault", "Clio", CategorieVehicule.CITADINE, "39.90");
        Vehicule peugeot508 = vehicule("EF-456-GH", "Peugeot", "508", CategorieVehicule.BERLINE, "64.90");
        Vehicule duster = vehicule("IJ-789-KL", "Dacia", "Duster", CategorieVehicule.SUV, "54.90");
        clio.ajouterEquipement(gps);
        peugeot508.ajouterEquipement(siegeEnfant);
        duster.ajouterEquipement(gps);
        duster.ajouterEquipement(chainesNeige);

        // Vehicule -> Maintenance (PERSIST) : la maintenance est enregistrée avec le véhicule
        duster.setStatut(StatutVehicule.MAINTENANCE);
        duster.ajouterMaintenance(maintenance("Révision des 60 000 km"));

        // Agence -> Vehicule / Employe : aucune cascade, chaque entité est enregistrée explicitement
        agence.ajouterVehicule(clio);
        agence.ajouterVehicule(peugeot508);
        agence.ajouterVehicule(duster);
        vehiculeRepository.saveAll(agence.getVehicules());

        agence.ajouterEmploye(employe("Martin", "Claire", RoleEmploye.MANAGER));
        agence.ajouterEmploye(employe("Benali", "Yanis", RoleEmploye.AGENT));
        employeRepository.saveAll(agence.getEmployes());

        // Client -> Reservation (PERSIST) -> Contrat (ALL) -> Paiement (ALL) :
        // une seule sauvegarde du client enregistre toute la chaîne
        Client client = client();
        Reservation reservation = reservation(clio);
        client.ajouterReservation(reservation);
        Contrat contrat = contrat(new BigDecimal("119.70"));
        reservation.attacherContrat(contrat);
        contrat.ajouterPaiement(paiement("50.00", ModePaiement.CARTE));
        contrat.ajouterPaiement(paiement("69.70", ModePaiement.VIREMENT));
        clio.setStatut(StatutVehicule.LOUE);
        clientRepository.save(client);

        log.info("Données de démonstration chargées : 1 agence, 3 véhicules, 3 équipements, 2 employés, "
                + "1 client, 1 réservation, 1 contrat, 2 paiements, 1 maintenance");
    }

    private static Equipement equipement(String libelle) {
        Equipement equipement = new Equipement();
        equipement.setLibelle(libelle);
        return equipement;
    }

    private static Agence agence() {
        Agence agence = new Agence();
        agence.setNom("AutoLoc Paris Centre");
        agence.setVille("Paris");
        agence.setAdresse("37 quai de Grenelle, 75015 Paris");
        agence.setTelephone("01 23 45 67 89");
        return agence;
    }

    private static Vehicule vehicule(String immatriculation, String marque, String modele,
                                     CategorieVehicule categorie, String tarifJournalier) {
        Vehicule vehicule = new Vehicule();
        vehicule.setImmatriculation(immatriculation);
        vehicule.setMarque(marque);
        vehicule.setModele(modele);
        vehicule.setCategorie(categorie);
        vehicule.setTarifJournalier(new BigDecimal(tarifJournalier));
        vehicule.setStatut(StatutVehicule.DISPONIBLE);
        return vehicule;
    }

    private static Maintenance maintenance(String description) {
        Maintenance maintenance = new Maintenance();
        maintenance.setDateDebut(LocalDate.now());
        maintenance.setDescription(description);
        return maintenance;
    }

    private static Employe employe(String nom, String prenom, RoleEmploye role) {
        Employe employe = new Employe();
        employe.setNom(nom);
        employe.setPrenom(prenom);
        employe.setRole(role);
        return employe;
    }

    private static Client client() {
        Client client = new Client();
        client.setNom("Durand");
        client.setPrenom("Léa");
        client.setEmail("lea.durand@example.com");
        client.setTelephone("06 12 34 56 78");
        client.setNumPermis("PERMIS-75-000123");
        client.setDateInscription(LocalDate.now());
        return client;
    }

    private static Reservation reservation(Vehicule vehicule) {
        Reservation reservation = new Reservation();
        reservation.setDateDebut(LocalDate.now());
        reservation.setDateFin(LocalDate.now().plusDays(3));
        reservation.setStatut(StatutReservation.CONFIRMEE);
        reservation.setVehicule(vehicule);
        return reservation;
    }

    private static Contrat contrat(BigDecimal montantTotal) {
        Contrat contrat = new Contrat();
        contrat.setDateSignature(LocalDate.now());
        contrat.setMontantTotal(montantTotal);
        contrat.setValide(true);
        return contrat;
    }

    private static Paiement paiement(String montant, ModePaiement modePaiement) {
        Paiement paiement = new Paiement();
        paiement.setMontant(new BigDecimal(montant));
        paiement.setDatePaiement(LocalDateTime.now());
        paiement.setModePaiement(modePaiement);
        return paiement;
    }
}
