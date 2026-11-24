package fr.ece.autoloc.web;

import fr.ece.autoloc.domain.Agence;
import fr.ece.autoloc.domain.CategorieVehicule;
import fr.ece.autoloc.domain.Equipement;
import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.repository.AgenceRepository;
import fr.ece.autoloc.repository.EquipementRepository;
import fr.ece.autoloc.repository.VehiculeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Appelle les endpoints de la vitrine en HTTP réel, sans authentification ni transaction de test :
 * vérifie que la conversion en DTO ne déclenche aucune LazyInitializationException.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class VitrineApiTest {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private AgenceRepository agenceRepository;

    @Autowired
    private EquipementRepository equipementRepository;

    @Autowired
    private VehiculeRepository vehiculeRepository;

    @BeforeEach
    void enregistrerUnVehiculeEquipe() {
        Equipement gps = new Equipement();
        gps.setLibelle("GPS");
        equipementRepository.save(gps);

        Agence agence = new Agence();
        agence.setNom("AutoLoc Test");
        agence.setVille("Paris");
        agence.setAdresse("1 rue du Test");
        agenceRepository.save(agence);

        Vehicule vehicule = new Vehicule();
        vehicule.setImmatriculation("TS-001-AA");
        vehicule.setMarque("Renault");
        vehicule.setModele("Clio");
        vehicule.setCategorie(CategorieVehicule.CITADINE);
        vehicule.setTarifJournalier(new BigDecimal("39.90"));
        vehicule.setStatut(StatutVehicule.DISPONIBLE);
        vehicule.setAgence(agence);
        vehicule.ajouterEquipement(gps);
        vehiculeRepository.save(vehicule);
    }

    @AfterEach
    void nettoyer() {
        vehiculeRepository.deleteAll();
        equipementRepository.deleteAll();
        agenceRepository.deleteAll();
    }

    @Test
    void listeLesVehiculesAvecLeurAgenceEtLeursEquipements() throws Exception {
        HttpResponse<String> reponse = get("/api/public/vehicules");

        assertThat(reponse.statusCode()).isEqualTo(200);
        assertThat(reponse.body()).contains(
                "\"immatriculation\":\"TS-001-AA\"",
                "\"categorie\":\"CITADINE\"",
                "\"statut\":\"DISPONIBLE\"",
                "\"agence\":\"AutoLoc Test\"",
                "\"ville\":\"Paris\"",
                "\"equipements\":[\"GPS\"]");
    }

    @Test
    void listeLesAgences() throws Exception {
        HttpResponse<String> reponse = get("/api/public/agences");

        assertThat(reponse.statusCode()).isEqualTo(200);
        assertThat(reponse.body()).contains("\"nom\":\"AutoLoc Test\"", "\"adresse\":\"1 rue du Test\"");
    }

    @Test
    void sertLaPageDAccueilSansAuthentification() throws Exception {
        HttpResponse<String> reponse = get("/");

        assertThat(reponse.statusCode()).isEqualTo(200);
        assertThat(reponse.body()).contains("AutoLoc");
    }

    private HttpResponse<String> get(String chemin) throws Exception {
        HttpRequest requete = HttpRequest.newBuilder(URI.create("http://localhost:" + port + chemin)).GET().build();
        return httpClient.send(requete, HttpResponse.BodyHandlers.ofString());
    }
}
