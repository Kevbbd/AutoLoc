package fr.ece.autoloc.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie que les méthodes utilitaires maintiennent les deux côtés de chaque association
 * bidirectionnelle (test unitaire pur, sans Spring ni base de données).
 */
class AssociationsBidirectionnellesTest {

    @Test
    void ajouterEtRetirerUnPaiementMetAJourLesDeuxCotes() {
        Contrat contrat = new Contrat();
        Paiement paiement = new Paiement();

        contrat.ajouterPaiement(paiement);
        assertThat(contrat.getPaiements()).containsExactly(paiement);
        assertThat(paiement.getContrat()).isSameAs(contrat);

        contrat.retirerPaiement(paiement);
        assertThat(contrat.getPaiements()).isEmpty();
        assertThat(paiement.getContrat()).isNull();
    }

    @Test
    void attacherUnContratRenseigneLaReservationDuContrat() {
        Reservation reservation = new Reservation();
        Contrat contrat = new Contrat();

        reservation.attacherContrat(contrat);

        assertThat(reservation.getContrat()).isSameAs(contrat);
        assertThat(contrat.getReservation()).isSameAs(reservation);
    }

    @Test
    void ajouterUneReservationRenseigneLeClient() {
        Client client = new Client();
        Reservation reservation = new Reservation();

        client.ajouterReservation(reservation);

        assertThat(client.getReservations()).containsExactly(reservation);
        assertThat(reservation.getClient()).isSameAs(client);
    }

    @Test
    void ajouterVehiculeEtEmployeRenseigneLAgence() {
        Agence agence = new Agence();
        Vehicule vehicule = new Vehicule();
        Employe employe = new Employe();

        agence.ajouterVehicule(vehicule);
        agence.ajouterEmploye(employe);

        assertThat(agence.getVehicules()).containsExactly(vehicule);
        assertThat(vehicule.getAgence()).isSameAs(agence);
        assertThat(agence.getEmployes()).containsExactly(employe);
        assertThat(employe.getAgence()).isSameAs(agence);
    }

    @Test
    void unEquipementPartageEstVisibleDesDeuxCotes() {
        Equipement gps = new Equipement();
        Vehicule clio = new Vehicule();
        Vehicule duster = new Vehicule();

        clio.ajouterEquipement(gps);
        duster.ajouterEquipement(gps);
        assertThat(gps.getVehicules()).containsExactlyInAnyOrder(clio, duster);

        clio.retirerEquipement(gps);
        assertThat(clio.getEquipements()).isEmpty();
        assertThat(gps.getVehicules()).containsExactly(duster);
    }

    @Test
    void ajouterUneMaintenanceRenseigneLeVehicule() {
        Vehicule vehicule = new Vehicule();
        Maintenance maintenance = new Maintenance();

        vehicule.ajouterMaintenance(maintenance);

        assertThat(vehicule.getMaintenances()).containsExactly(maintenance);
        assertThat(maintenance.getVehicule()).isSameAs(vehicule);
    }
}
