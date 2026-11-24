package fr.ece.autoloc.web;

import fr.ece.autoloc.domain.Agence;
import fr.ece.autoloc.domain.CategorieVehicule;
import fr.ece.autoloc.domain.StatutReservation;
import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.repository.AgenceRepository;
import fr.ece.autoloc.repository.ClientRepository;
import fr.ece.autoloc.repository.ReservationRepository;
import fr.ece.autoloc.repository.VehiculeRepository;
import fr.ece.autoloc.support.ClientHttp;
import fr.ece.autoloc.support.Simultanement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;

import static fr.ece.autoloc.support.ClientHttp.champ;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parcours complets de réservation sur l'application démarrée (HTTP réel, base H2) :
 * devis, réservation, conflits, validation, consultation, annulation, back-office et concurrence.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ReservationApiTest {

    private static final LocalDate AUJOURDHUI = LocalDate.now();
    private static final String[] ADMIN = {"admin", "admin123"};
    private static final String[] STUDENT = {"student", "student123"};

    @LocalServerPort
    private int port;

    @Autowired
    private AgenceRepository agenceRepository;

    @Autowired
    private VehiculeRepository vehiculeRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    private ClientHttp http;
    private Long idDisponible;
    private Long idEnMaintenance;

    @BeforeEach
    void flotteDeTest() {
        http = new ClientHttp(port);
        Agence agence = new Agence();
        agence.setNom("AutoLoc Test");
        agence.setVille("Paris");
        agence.setAdresse("1 rue du Test");
        agenceRepository.save(agence);
        idDisponible = vehicule(agence, "RS-001-AA", StatutVehicule.DISPONIBLE);
        idEnMaintenance = vehicule(agence, "RS-002-BB", StatutVehicule.MAINTENANCE);
    }

    @AfterEach
    void nettoyer() {
        reservationRepository.deleteAll();
        clientRepository.deleteAll();
        vehiculeRepository.deleteAll();
        agenceRepository.deleteAll();
    }

    /* ---------- Devis et disponibilité ---------- */

    @Test
    void leDevisCalculeLeMontantCoteServeur() {
        HttpResponse<String> devis = http.get(devis(idDisponible, 5, 8));

        assertThat(devis.statusCode()).isEqualTo(200);
        assertThat(champ(devis.body(), "jours")).isEqualTo("3");
        assertThat(new BigDecimal(champ(devis.body(), "montantTotal"))).isEqualByComparingTo("150.00");
        assertThat(champ(devis.body(), "disponible")).isEqualTo("true");
    }

    @Test
    void leDevisSignaleUnVehiculeEnMaintenance() {
        HttpResponse<String> devis = http.get(devis(idEnMaintenance, 5, 8));

        assertThat(champ(devis.body(), "disponible")).isEqualTo("false");
        assertThat(champ(devis.body(), "motifIndisponibilite")).contains("maintenance");
    }

    @Test
    void laRechercheParDatesIndiqueLaDisponibiliteDeChaqueVehicule() {
        assertThat(reserver(idDisponible, 5, 8, "dispo@example.com", "P-DISPO").statusCode()).isEqualTo(201);

        String chevauchante = http.get("/api/public/vehicules?debut=" + jour(6) + "&fin=" + jour(9)).body();
        String apres = http.get("/api/public/vehicules?debut=" + jour(8) + "&fin=" + jour(9)).body();

        assertThat(disponibilite(chevauchante, "RS-001-AA")).isEqualTo("false");
        assertThat(disponibilite(chevauchante, "RS-002-BB")).as("maintenance").isEqualTo("false");
        assertThat(disponibilite(apres, "RS-001-AA")).as("départ le jour du retour").isEqualTo("true");
    }

    @Test
    void laRechercheRefuseUneSeuleDate() {
        assertThat(http.get("/api/public/vehicules?debut=" + jour(2)).statusCode()).isEqualTo(400);
    }

    /* ---------- Réservation ---------- */

    @Test
    void reserverCreeClientReservationEtContrat() {
        HttpResponse<String> reponse = reserver(idDisponible, 5, 8, "lea@example.com", "P-LEA");

        assertThat(reponse.statusCode()).isEqualTo(201);
        assertThat(reponse.headers().firstValue("Location")).hasValueSatisfying(
                location -> assertThat(location).contains("/api/public/reservations/" + champ(reponse.body(), "idReservation")));
        assertThat(champ(reponse.body(), "statut")).isEqualTo("CONFIRMEE");
        assertThat(new BigDecimal(champ(reponse.body(), "montantTotal"))).isEqualByComparingTo("150.00");
        assertThat(new BigDecimal(champ(reponse.body(), "resteAPayer"))).isEqualByComparingTo("150.00");
        assertThat(champ(reponse.body(), "contratValide")).isEqualTo("true");
        assertThat(clientRepository.findByEmailIgnoreCase("lea@example.com")).isPresent();
        assertThat(statutVehicule(idDisponible)).as("départ dans 5 jours").isEqualTo(StatutVehicule.DISPONIBLE);
    }

    @Test
    void uneLocationQuiCommenceAujourdhuiPasseLeVehiculeEnLoue() {
        assertThat(reserver(idDisponible, 0, 2, "today@example.com", "P-TODAY").statusCode()).isEqualTo(201);

        assertThat(statutVehicule(idDisponible)).isEqualTo(StatutVehicule.LOUE);
    }

    @Test
    void refuseUnChevauchementMaisAccepteUnDepartLeJourDuRetour() {
        assertThat(reserver(idDisponible, 5, 8, "a@example.com", "P-A").statusCode()).isEqualTo(201);

        HttpResponse<String> conflit = reserver(idDisponible, 7, 10, "b@example.com", "P-B");
        assertThat(conflit.statusCode()).isEqualTo(409);
        assertThat(conflit.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).contains("application/problem+json"));
        assertThat(champ(conflit.body(), "detail")).contains("déjà réservé");

        assertThat(reserver(idDisponible, 8, 10, "b@example.com", "P-B").statusCode()).isEqualTo(201);
    }

    @Test
    void refuseUnVehiculeEnMaintenance() {
        assertThat(reserver(idEnMaintenance, 5, 8, "m@example.com", "P-M").statusCode()).isEqualTo(409);
    }

    @Test
    void refuseUnVehiculeInconnu() {
        assertThat(reserver(999_999L, 5, 8, "x@example.com", "P-X").statusCode()).isEqualTo(404);
    }

    @Test
    void refuseDesDatesIncoherentesEnDesignantLeChamp() {
        HttpResponse<String> inversees = reserver(idDisponible, 8, 5, "c@example.com", "P-C");
        HttpResponse<String> passees = reserver(idDisponible, -2, 1, "c@example.com", "P-C");

        assertThat(inversees.statusCode()).isEqualTo(400);
        assertThat(inversees.body()).contains("\"dateFin\"");
        assertThat(passees.statusCode()).isEqualTo(400);
        assertThat(passees.body()).contains("\"dateDebut\"");
    }

    @Test
    void valideChaqueChampEtRenvoieTousLesMessages() {
        String json = """
                {"idVehicule": %d, "dateDebut": "%s", "dateFin": "%s",
                 "nom": "", "prenom": "Léa", "email": "pas-un-email", "telephone": "abc", "numPermis": "P-1"}
                """.formatted(idDisponible, jour(5), jour(8));

        HttpResponse<String> reponse = http.post("/api/public/reservations", json);

        assertThat(reponse.statusCode()).isEqualTo(400);
        assertThat(reponse.body()).contains("\"nom\"", "\"email\"", "\"telephone\"");
        assertThat(reservationRepository.count()).isZero();
    }

    @Test
    void unMemeEmailNePeutPasEtreAssocieADeuxPermis() {
        assertThat(reserver(idDisponible, 5, 6, "d@example.com", "P-D1").statusCode()).isEqualTo(201);

        HttpResponse<String> usurpation = reserver(idDisponible, 10, 11, "D@example.com", "P-D2");

        assertThat(usurpation.statusCode()).isEqualTo(409);
        assertThat(clientRepository.count()).isEqualTo(1);
    }

    @Test
    void unClientExistantReserveSansEtreDuplique() {
        assertThat(reserver(idDisponible, 5, 6, "e@example.com", "P-E").statusCode()).isEqualTo(201);
        assertThat(reserver(idDisponible, 10, 12, "E@Example.com", "p-e").statusCode()).isEqualTo(201);

        assertThat(clientRepository.count()).isEqualTo(1);
        assertThat(reservationRepository.count()).isEqualTo(2);
    }

    /* ---------- Consultation et annulation par le client ---------- */

    @Test
    void laReservationRenvoieUnCodeSecretImprevisible() {
        String premier = champ(reserver(idDisponible, 5, 6, "c1@example.com", "P-C1").body(), "codeAcces");
        String second = champ(reserver(idDisponible, 6, 7, "c2@example.com", "P-C2").body(), "codeAcces");

        assertThat(premier).matches("[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{10}");
        assertThat(second).matches("[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{10}").isNotEqualTo(premier);
    }

    @Test
    void consulterExigeLeCodeEtLeBonEmail() {
        String reponse = reserver(idDisponible, 5, 8, "f@example.com", "P-F").body();
        String code = champ(reponse, "codeAcces");
        String autreCode = champ(reserver(idDisponible, 10, 11, "pirate@example.com", "P-PIRATE").body(), "codeAcces");

        // Saisie « humaine » acceptée : minuscules, tiret au milieu
        HttpResponse<String> bonne = consulter(code.substring(0, 5).toLowerCase() + "-" + code.substring(5), "F@example.com");
        assertThat(bonne.statusCode()).isEqualTo(200);
        assertThat(champ(bonne.body(), "immatriculation")).isEqualTo("RS-001-AA");

        assertThat(consulter(code, "pirate@example.com").statusCode()).as("bon code, autre e-mail").isEqualTo(404);
        assertThat(consulter(autreCode, "f@example.com").statusCode()).as("son propre code sur l'e-mail d'un autre").isEqualTo(404);
        HttpResponse<String> malForme = consulter("ABC", "f@example.com");
        assertThat(malForme.statusCode()).isEqualTo(400);
        assertThat(malForme.body()).contains("\"code\"");
    }

    @Test
    void leNumeroSequentielNePermetPlusDAnnuler() {
        String id = champ(reserver(idDisponible, 5, 8, "n@example.com", "P-N").body(), "idReservation");

        int statut = http.post("/api/public/reservations/" + id + "/annulation", "{\"email\": \"n@example.com\"}").statusCode();

        assertThat(statut).isIn(404, 405);
        assertThat(reservationRepository.findAll()).allMatch(r -> r.getStatut() == StatutReservation.CONFIRMEE);
    }

    @Test
    void annulerLibereLeVehiculeEtNePeutPasEtreRepete() {
        String code = champ(reserver(idDisponible, 0, 2, "g@example.com", "P-G").body(), "codeAcces");
        assertThat(statutVehicule(idDisponible)).isEqualTo(StatutVehicule.LOUE);

        assertThat(annuler(code, "intrus@example.com").statusCode()).isEqualTo(404);
        HttpResponse<String> annulation = annuler(code, "g@example.com");
        assertThat(annulation.statusCode()).isEqualTo(200);
        assertThat(champ(annulation.body(), "statut")).isEqualTo("ANNULEE");
        assertThat(champ(annulation.body(), "contratValide")).isEqualTo("false");
        assertThat(statutVehicule(idDisponible)).isEqualTo(StatutVehicule.DISPONIBLE);

        assertThat(annuler(code, "g@example.com").statusCode()).isEqualTo(409);
        assertThat(reserver(idDisponible, 0, 2, "h@example.com", "P-H").statusCode())
                .as("la période est de nouveau libre").isEqualTo(201);
    }

    @Test
    void annulerAvecUnAutreEmailEstRefuse() {
        String code = champ(reserver(idDisponible, 5, 8, "i@example.com", "P-I").body(), "codeAcces");

        assertThat(annuler(code, "intrus@example.com").statusCode()).isEqualTo(404);
        assertThat(reservationRepository.findAll()).allMatch(r -> r.getStatut() == StatutReservation.CONFIRMEE);
    }

    /* ---------- Back-office ---------- */

    @Test
    void leBackOfficeEstReserveAuxAdministrateurs() {
        assertThat(http.get("/api/admin/reservations").statusCode()).isEqualTo(401);
        assertThat(http.get("/api/admin/reservations", STUDENT).statusCode()).isEqualTo(403);
        assertThat(http.get("/api/admin/reservations", ADMIN).statusCode()).isEqualTo(200);
    }

    @Test
    void encaisserPuisTerminerUneLocation() {
        String id = champ(reserver(idDisponible, 0, 3, "j@example.com", "P-J").body(), "idReservation");
        String chemin = "/api/admin/reservations/" + id;

        HttpResponse<String> acompte = http.post(chemin + "/paiements",
                "{\"montant\": 50.00, \"modePaiement\": \"CARTE\"}", ADMIN);
        assertThat(acompte.statusCode()).isEqualTo(200);
        assertThat(new BigDecimal(champ(acompte.body(), "resteAPayer"))).isEqualByComparingTo("100.00");

        assertThat(http.post(chemin + "/paiements", "{\"montant\": 100.01, \"modePaiement\": \"CARTE\"}", ADMIN)
                .statusCode()).as("trop-perçu").isEqualTo(409);
        assertThat(http.post(chemin + "/paiements", "{\"montant\": -1, \"modePaiement\": \"CARTE\"}", ADMIN)
                .statusCode()).as("montant négatif").isEqualTo(400);

        HttpResponse<String> solde = http.post(chemin + "/paiements", "{\"modePaiement\": \"VIREMENT\"}", ADMIN);
        assertThat(new BigDecimal(champ(solde.body(), "resteAPayer"))).isEqualByComparingTo("0");
        assertThat(http.post(chemin + "/paiements", "{\"modePaiement\": \"VIREMENT\"}", ADMIN).statusCode())
                .as("déjà soldé").isEqualTo(409);

        HttpResponse<String> fin = http.post(chemin + "/terminaison", "", ADMIN);
        assertThat(champ(fin.body(), "statut")).isEqualTo("TERMINEE");
        assertThat(statutVehicule(idDisponible)).isEqualTo(StatutVehicule.DISPONIBLE);
        assertThat(http.post(chemin + "/annulation", "", ADMIN).statusCode())
                .as("une location terminée ne s'annule pas").isEqualTo(409);
    }

    /* ---------- Concurrence ---------- */

    /**
     * Huit clients réservent au même instant le même véhicule sur la même période.
     * Le verrou pessimiste garantit qu'exactement une demande aboutit.
     */
    @Test
    void desReservationsSimultaneesNeProduisentJamaisDeDoubleReservation() throws Exception {
        List<Integer> statuts = Simultanement.executer(8,
                n -> reserver(idDisponible, 20, 23, "conc" + n + "@example.com", "P-CONC-" + n).statusCode());

        assertThat(statuts).filteredOn(s -> s == 201).hasSize(1);
        assertThat(statuts).filteredOn(s -> s == 409).hasSize(7);
        assertThat(reservationRepository.count()).isEqualTo(1);
    }

    /**
     * Huit agents encaissent 100 € au même instant sur un contrat de 150 € : sans verrou, plusieurs
     * verraient « reste 150 € » et le contrat serait payé 800 €. Avec le verrou, un seul passe.
     */
    @Test
    void desEncaissementsSimultanesNeDepassentJamaisLeMontantDuContrat() throws Exception {
        String reponse = reserver(idDisponible, 20, 23, "caisse@example.com", "P-CAISSE").body();
        String id = champ(reponse, "idReservation");

        List<Integer> statuts = Simultanement.executer(8, n -> http.post("/api/admin/reservations/" + id + "/paiements",
                "{\"montant\": 100.00, \"modePaiement\": \"CARTE\"}", ADMIN).statusCode());

        assertThat(statuts).filteredOn(s -> s == 200).hasSize(1);
        assertThat(statuts).filteredOn(s -> s == 409).hasSize(7);
        HttpResponse<String> detail = consulter(champ(reponse, "codeAcces"), "caisse@example.com");
        assertThat(new BigDecimal(champ(detail.body(), "montantPaye"))).isEqualByComparingTo("100.00");
        assertThat(new BigDecimal(champ(detail.body(), "resteAPayer"))).isEqualByComparingTo("50.00");
    }

    /* ---------- Outils ---------- */

    private HttpResponse<String> reserver(Long idVehicule, int debut, int fin, String email, String permis) {
        String json = """
                {"idVehicule": %d, "dateDebut": "%s", "dateFin": "%s",
                 "nom": "Durand", "prenom": "Léa", "email": "%s", "telephone": "06 12 34 56 78", "numPermis": "%s"}
                """.formatted(idVehicule, jour(debut), jour(fin), email, permis);
        return http.post("/api/public/reservations", json);
    }

    private HttpResponse<String> consulter(String code, String email) {
        return http.post("/api/public/reservations/consultation", acces(code, email));
    }

    private HttpResponse<String> annuler(String code, String email) {
        return http.post("/api/public/reservations/annulation", acces(code, email));
    }

    private static String acces(String code, String email) {
        return "{\"code\": \"" + code + "\", \"email\": \"" + email + "\"}";
    }

    private static String devis(Long idVehicule, int debut, int fin) {
        return "/api/public/vehicules/" + idVehicule + "/devis?debut=" + jour(debut) + "&fin=" + jour(fin);
    }

    private static String jour(int decalage) {
        return AUJOURDHUI.plusDays(decalage).toString();
    }

    /** Valeur de disponibleSurPeriode pour le véhicule d'immatriculation donnée dans la réponse de la flotte. */
    private static String disponibilite(String json, String immatriculation) {
        int debut = json.indexOf("\"immatriculation\":\"" + immatriculation + "\"");
        assertThat(debut).as("véhicule %s présent", immatriculation).isGreaterThanOrEqualTo(0);
        int fin = json.indexOf('}', json.indexOf("\"disponibleSurPeriode\"", debut));
        return champ(json.substring(debut, fin + 1), "disponibleSurPeriode");
    }

    private StatutVehicule statutVehicule(Long idVehicule) {
        return vehiculeRepository.findById(idVehicule).orElseThrow().getStatut();
    }

    private Long vehicule(Agence agence, String immatriculation, StatutVehicule statut) {
        Vehicule vehicule = new Vehicule();
        vehicule.setImmatriculation(immatriculation);
        vehicule.setMarque("Renault");
        vehicule.setModele("Clio");
        vehicule.setCategorie(CategorieVehicule.CITADINE);
        vehicule.setTarifJournalier(new BigDecimal("50.00"));
        vehicule.setStatut(statut);
        vehicule.setAgence(agence);
        return vehiculeRepository.save(vehicule).getIdVehicule();
    }
}
