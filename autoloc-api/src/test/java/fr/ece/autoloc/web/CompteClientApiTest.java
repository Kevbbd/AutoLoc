package fr.ece.autoloc.web;

import fr.ece.autoloc.domain.Agence;
import fr.ece.autoloc.domain.CategorieVehicule;
import fr.ece.autoloc.domain.RoleUtilisateur;
import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Utilisateur;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.repository.AgenceRepository;
import fr.ece.autoloc.repository.ClientRepository;
import fr.ece.autoloc.repository.JetonInscriptionRepository;
import fr.ece.autoloc.repository.JetonReinitialisationRepository;
import fr.ece.autoloc.repository.ReservationRepository;
import fr.ece.autoloc.repository.UtilisateurRepository;
import fr.ece.autoloc.repository.VehiculeRepository;
import fr.ece.autoloc.service.compte.CompteClientService;
import fr.ece.autoloc.support.ClientHttp;
import fr.ece.autoloc.support.MessagerieEnMemoire;
import fr.ece.autoloc.support.MessagerieEnMemoire.Email;
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
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static fr.ece.autoloc.support.ClientHttp.champ;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comptes clients de bout en bout, en HTTP réel : inscription par lien e-mail, connexion par adresse,
 * réservation et annulation depuis le compte, cloisonnement entre clients et avec le personnel.
 * Chaque test utilise ses propres adresses : le limiteur « 3 demandes par heure et par adresse » est
 * partagé par tous les tests du même contexte Spring.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CompteClientApiTest {

    private static final Duration DELAI_EMAIL = Duration.ofSeconds(10);
    private static final Duration SILENCE = Duration.ofMillis(800);
    private static final Pattern LIEN = Pattern.compile("http://localhost:8080/inscription\\.html#jeton=([A-Za-z0-9_-]{43})");
    private static final Pattern LIEN_REINITIALISATION =
            Pattern.compile("http://localhost:8080/reinitialisation\\.html#jeton=([A-Za-z0-9_-]{43})");
    private static final AtomicInteger NUMERO = new AtomicInteger();
    private static final String MOT_DE_PASSE = "Voiture2026";
    private static final LocalDate AUJOURDHUI = LocalDate.now();

    @LocalServerPort
    private int port;

    @Autowired
    private UtilisateurRepository utilisateurRepository;
    @Autowired
    private JetonInscriptionRepository jetonInscriptionRepository;
    @Autowired
    private JetonReinitialisationRepository jetonReinitialisationRepository;
    @Autowired
    private ClientRepository clientRepository;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private VehiculeRepository vehiculeRepository;
    @Autowired
    private AgenceRepository agenceRepository;
    @Autowired
    private MessagerieEnMemoire boite;

    private ClientHttp http;
    private Long idVehicule;
    private Long idAutreVehicule;

    @BeforeEach
    void preparer() {
        http = new ClientHttp(port);
        boite.vider();
        Agence agence = new Agence();
        agence.setNom("AutoLoc Comptes");
        agence.setVille("Paris");
        agence.setAdresse("2 rue du Test");
        agenceRepository.save(agence);
        idVehicule = vehicule(agence, "CC-001-AA");
        idAutreVehicule = vehicule(agence, "CC-002-BB");
    }

    @AfterEach
    void nettoyer() throws InterruptedException {
        boite.recusApres(Duration.ZERO);
        jetonReinitialisationRepository.deleteAll();
        jetonInscriptionRepository.deleteAll();
        utilisateurRepository.deleteAll(utilisateurRepository.findAll().stream().filter(Utilisateur::estClient).toList());
        reservationRepository.deleteAll();
        clientRepository.deleteAll();
        vehiculeRepository.deleteAll();
        agenceRepository.deleteAll();
    }

    /* ---------- Inscription ---------- */

    @Test
    void parcoursCompletInscriptionConnexionReservation() throws Exception {
        String email = adresse();

        HttpResponse<String> demande = demander(email.toUpperCase());
        assertThat(demande.statusCode()).isEqualTo(202);
        Email recu = boite.attendre(DELAI_EMAIL);
        assertThat(recu.destinataire()).isEqualTo(email);
        assertThat(recu.texte()).contains("valable 24 heures", "une seule fois");
        String jeton = jetonDe(recu);

        HttpResponse<String> verification = verifier(jeton);
        assertThat(verification.statusCode()).isEqualTo(200);
        assertThat(champ(verification.body(), "email")).isEqualTo(email);
        assertThat(champ(verification.body(), "clientExistant")).isEqualTo("false");

        assertThat(finaliser(jeton, "Martin", "Lou", "p-" + NUMERO.get(), MOT_DE_PASSE).statusCode()).isEqualTo(201);
        assertThat(finaliser(jeton, "Martin", "Lou", "p-autre", MOT_DE_PASSE).statusCode())
                .as("le lien ne sert qu'une fois").isEqualTo(400);

        HttpResponse<String> profil = http.get("/api/client/profil", email.toUpperCase(), MOT_DE_PASSE);
        assertThat(profil.statusCode()).as("connexion par e-mail, casse indifférente").isEqualTo(200);
        assertThat(champ(profil.body(), "prenom")).isEqualTo("Lou");
        assertThat(champ(profil.body(), "numPermis")).isEqualTo("P-" + NUMERO.get());

        HttpResponse<String> reservation = reserverDepuisCompte(email, idVehicule, 3, 6);
        assertThat(reservation.statusCode()).isEqualTo(201);
        assertThat(new BigDecimal(champ(reservation.body(), "montantTotal"))).isEqualByComparingTo("150.00");

        HttpResponse<String> liste = http.get("/api/client/reservations", email, MOT_DE_PASSE);
        assertThat(liste.statusCode()).isEqualTo(200);
        assertThat(liste.body()).contains(champ(reservation.body(), "codeAcces"));
    }

    @Test
    void rienNestCreeAvantQueLAdresseSoitConfirmee() throws Exception {
        String email = adresse();

        demander(email);
        boite.attendre(DELAI_EMAIL);

        assertThat(utilisateurRepository.findByEmailIgnoreCase(email)).isEmpty();
        assertThat(clientRepository.findByEmailIgnoreCase(email)).isEmpty();
        assertThat(jetonInscriptionRepository.findAll()).allSatisfy(j -> assertThat(j.getEmpreinte()).hasSize(64))
                .as("seule l'empreinte SHA-256 du jeton est stockée").isNotEmpty();
    }

    @Test
    void uneAdresseDejaInscriteRecoitUnAvertissementEtLaReponseNeLeRevelePas() throws Exception {
        String email = compteClient();
        String inconnue = adresse();

        HttpResponse<String> pourCompteExistant = demander(email);
        Email avertissement = boite.attendre(DELAI_EMAIL);
        HttpResponse<String> pourAdresseNeuve = demander(inconnue);
        boite.attendre(DELAI_EMAIL);

        assertThat(pourCompteExistant.statusCode()).isEqualTo(pourAdresseNeuve.statusCode()).isEqualTo(202);
        assertThat(pourCompteExistant.body()).isEqualTo(pourAdresseNeuve.body());
        assertThat(avertissement.destinataire()).isEqualTo(email);
        assertThat(avertissement.sujet()).contains("déjà un compte");
        assertThat(avertissement.texte()).doesNotContain("jeton=");
    }

    @Test
    void uneAdresseDuPersonnelNeRecoitRienEtNeDevientPasClient() throws Exception {
        assertThat(demander("student@autoloc.test").statusCode()).isEqualTo(202);

        assertThat(boite.recusApres(SILENCE)).isEmpty();
        assertThat(jetonInscriptionRepository.findAll()).isEmpty();
    }

    @Test
    void auDelaDeTroisDemandesParHeureLesSuivantesSontIgnorees() throws Exception {
        String email = adresse();

        for (int i = 0; i < CompteClientService.DEMANDES_MAX_PAR_HEURE + 2; i++) {
            assertThat(demander(email).statusCode()).as("réponse identique, demande %d", i).isEqualTo(202);
        }

        assertThat(boite.recusApres(SILENCE)).hasSize(CompteClientService.DEMANDES_MAX_PAR_HEURE);
    }

    @Test
    void unNouveauLienAnnuleLePrecedent() throws Exception {
        String email = adresse();
        demander(email);
        String ancien = jetonDe(boite.attendre(DELAI_EMAIL));
        demander(email);
        String nouveau = jetonDe(boite.attendre(DELAI_EMAIL));

        assertThat(verifier(ancien).statusCode()).isEqualTo(400);
        assertThat(verifier(nouveau).statusCode()).isEqualTo(200);
    }

    @Test
    void uneErreurApresLaConsommationDuLienLeLaisseUtilisable() throws Exception {
        String email = adresse();
        demander(email);
        String jeton = jetonDe(boite.attendre(DELAI_EMAIL));

        HttpResponse<String> sansPermis = finaliser(jeton, "Martin", "Lou", "", MOT_DE_PASSE);
        assertThat(sansPermis.statusCode()).isEqualTo(400);
        assertThat(sansPermis.body()).contains("permis");
        HttpResponse<String> motDePasseFaible = finaliser(jeton, "Martin", "Lou", "P-X" + NUMERO.get(), "court");
        assertThat(motDePasseFaible.statusCode()).isEqualTo(400);

        assertThat(finaliser(jeton, "Martin", "Lou", "P-X" + NUMERO.get(), MOT_DE_PASSE).statusCode())
                .as("transaction annulée : le lien n'a pas été consommé").isEqualTo(201);
    }

    @Test
    void leMotDePasseNePeutPasContenirLeDebutDeLAdresse() throws Exception {
        String email = adresse();
        demander(email);
        String jeton = jetonDe(boite.attendre(DELAI_EMAIL));
        String debut = email.substring(0, email.indexOf('@'));

        HttpResponse<String> reponse = finaliser(jeton, "Martin", "Lou", "P-Y" + NUMERO.get(), debut + "X9");

        assertThat(reponse.statusCode()).isEqualTo(400);
    }

    /** Huit finalisations simultanées du même lien : la consommation est atomique, un seul compte est créé. */
    @Test
    void unLienEnvoyePlusieursFoisEnMemeTempsNeCreeQuUnCompte() throws Exception {
        String email = adresse();
        demander(email);
        String jeton = jetonDe(boite.attendre(DELAI_EMAIL));
        int n = NUMERO.get();

        List<Integer> statuts = Simultanement.executer(8,
                i -> finaliser(jeton, "Martin", "Lou", "P-PAR" + n + "-" + i, MOT_DE_PASSE + i).statusCode());

        assertThat(statuts).filteredOn(s -> s == 201).hasSize(1);
        assertThat(statuts).filteredOn(s -> s == 400).hasSize(7);
        assertThat(utilisateurRepository.findAll()).filteredOn(u -> email.equals(u.getEmail())).hasSize(1);
        assertThat(clientRepository.findAll()).filteredOn(c -> email.equals(c.getEmail())).hasSize(1);
    }

    /* ---------- Réservations sans compte rattachées ---------- */

    @Test
    void lesReservationsFaitesSansCompteApparaissentDansLeCompte() throws Exception {
        String email = adresse();
        String permis = "P-INV" + NUMERO.get();
        HttpResponse<String> sansCompte = reserverSansCompte(idVehicule, 2, 4, email, permis);
        assertThat(sansCompte.statusCode()).isEqualTo(201);

        demander(email);
        String jeton = jetonDe(boite.attendre(DELAI_EMAIL));
        HttpResponse<String> verification = verifier(jeton);
        assertThat(champ(verification.body(), "clientExistant")).isEqualTo("true");
        assertThat(champ(verification.body(), "prenom")).isEqualTo("Léa");

        assertThat(finaliser(jeton, null, null, null, MOT_DE_PASSE).statusCode())
                .as("fiche existante : seul le mot de passe est demandé").isEqualTo(201);

        String liste = http.get("/api/client/reservations", email, MOT_DE_PASSE).body();
        assertThat(liste).contains(champ(sansCompte.body(), "codeAcces"));
        assertThat(clientRepository.findAll()).filteredOn(c -> email.equals(c.getEmail())).hasSize(1);
    }

    /* ---------- Cloisonnement ---------- */

    @Test
    void lIdentiteVientDeLaConnexionJamaisDuCorpsDeLaRequete() throws Exception {
        String email = compteClient();
        String json = """
                {"idVehicule": %d, "dateDebut": "%s", "dateFin": "%s",
                 "email": "victime@example.com", "nom": "Usurpé", "numPermis": "P-VICTIME"}
                """.formatted(idVehicule, jour(3), jour(5));

        HttpResponse<String> reponse = http.post("/api/client/reservations", json, email, MOT_DE_PASSE);

        assertThat(reponse.statusCode()).isEqualTo(201);
        assertThat(clientRepository.findByEmailIgnoreCase("victime@example.com")).isEmpty();
        assertThat(reservationRepository.findByClientEmailIgnoreCaseOrderByDateDebutDesc(email)).hasSize(1);
    }

    @Test
    void unClientNeVoitNiNAnnuleLesReservationsDUnAutre() throws Exception {
        String alice = compteClient();
        String bob = compteClient();
        HttpResponse<String> deBob = reserverDepuisCompte(bob, idAutreVehicule, 4, 6);
        String idDeBob = champ(deBob.body(), "idReservation");

        assertThat(http.get("/api/client/reservations", alice, MOT_DE_PASSE).body())
                .doesNotContain(champ(deBob.body(), "codeAcces"));
        HttpResponse<String> tentative = http.post("/api/client/reservations/" + idDeBob + "/annulation", "{}", alice, MOT_DE_PASSE);
        assertThat(tentative.statusCode()).as("comme si elle n'existait pas").isEqualTo(404);

        HttpResponse<String> parBob = http.post("/api/client/reservations/" + idDeBob + "/annulation", "{}", bob, MOT_DE_PASSE);
        assertThat(parBob.statusCode()).isEqualTo(200);
        assertThat(champ(parBob.body(), "statut")).isEqualTo("ANNULEE");
    }

    @Test
    void clientsEtPersonnelNeSeMelangentPas() throws Exception {
        String email = compteClient();
        Utilisateur compte = utilisateurRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(compte.getRole()).isEqualTo(RoleUtilisateur.CLIENT);

        assertThat(http.get("/api/vehicules", email, MOT_DE_PASSE).statusCode()).as("API de l'agence").isEqualTo(403);
        assertThat(http.get("/api/admin/reservations", email, MOT_DE_PASSE).statusCode()).isEqualTo(403);
        assertThat(http.get("/api/client/profil", compte.getIdentifiant(), MOT_DE_PASSE).statusCode())
                .as("un client se connecte par son e-mail uniquement").isEqualTo(401);
        assertThat(http.get("/api/client/profil", "student", "student123").statusCode()).isEqualTo(403);
        assertThat(http.get("/api/client/profil", "admin@autoloc.test", "admin123").statusCode())
                .as("le personnel se connecte par son identifiant uniquement").isEqualTo(401);
    }

    /* ---------- Mot de passe oublié et blocage, étendus aux clients ---------- */

    @Test
    void motDePasseOubliePourUnClient() throws Exception {
        String email = compteClient();

        assertThat(http.post("/api/public/mot-de-passe/oubli", "{\"email\": \"" + email + "\"}").statusCode()).isEqualTo(202);
        Email recu = boite.attendre(DELAI_EMAIL);
        assertThat(recu.texte()).contains("« " + email + " »", "le site AutoLoc");
        Matcher m = LIEN_REINITIALISATION.matcher(recu.texte());
        assertThat(m.find()).isTrue();

        HttpResponse<String> reponse = http.post("/api/public/mot-de-passe/reinitialisation",
                "{\"jeton\": \"" + m.group(1) + "\", \"motDePasse\": \"Nouvelle2027\"}");
        assertThat(reponse.statusCode()).isEqualTo(200);
        assertThat(champ(reponse.body(), "espace")).isEqualTo("CLIENT");
        assertThat(http.get("/api/client/profil", email, "Nouvelle2027").statusCode()).isEqualTo(200);
        assertThat(http.get("/api/client/profil", email, MOT_DE_PASSE).statusCode()).isEqualTo(401);
    }

    @Test
    void cinqEchecsBloquentAussiUnCompteClient() throws Exception {
        String email = compteClient();

        for (int i = 1; i <= 5; i++) {
            assertThat(http.get("/api/client/profil", email, "mauvais" + i).statusCode()).as("essai %d", i).isEqualTo(401);
        }

        assertThat(http.get("/api/client/profil", email.toUpperCase(), MOT_DE_PASSE).statusCode()).isEqualTo(429);
    }

    /* ---------- Outils ---------- */

    private static String adresse() {
        return "client" + NUMERO.incrementAndGet() + "@exemple.test";
    }

    /** Crée un compte client complet (inscription, lien, finalisation) et renvoie son adresse. */
    private String compteClient() throws Exception {
        String email = adresse();
        demander(email);
        String jeton = jetonDe(boite.attendre(DELAI_EMAIL));
        assertThat(finaliser(jeton, "Client", "Test", "P-C" + NUMERO.get(), MOT_DE_PASSE).statusCode()).isEqualTo(201);
        return email;
    }

    private HttpResponse<String> demander(String email) {
        return http.post("/api/public/clients/inscription", "{\"email\": \"" + email + "\"}");
    }

    private HttpResponse<String> verifier(String jeton) {
        return http.post("/api/public/clients/inscription/verification", "{\"jeton\": \"" + jeton + "\"}");
    }

    private HttpResponse<String> finaliser(String jeton, String nom, String prenom, String permis, String motDePasse) {
        return http.post("/api/public/clients/inscription/finalisation", """
                {"jeton": "%s", "nom": %s, "prenom": %s, "numPermis": %s, "motDePasse": "%s"}
                """.formatted(jeton, texte(nom), texte(prenom), texte(permis), motDePasse));
    }

    private HttpResponse<String> reserverDepuisCompte(String email, Long vehicule, int debut, int fin) {
        return http.post("/api/client/reservations", """
                {"idVehicule": %d, "dateDebut": "%s", "dateFin": "%s"}
                """.formatted(vehicule, jour(debut), jour(fin)), email, MOT_DE_PASSE);
    }

    private HttpResponse<String> reserverSansCompte(Long vehicule, int debut, int fin, String email, String permis) {
        return http.post("/api/public/reservations", """
                {"idVehicule": %d, "dateDebut": "%s", "dateFin": "%s",
                 "nom": "Durand", "prenom": "Léa", "email": "%s", "telephone": "06 12 34 56 78", "numPermis": "%s"}
                """.formatted(vehicule, jour(debut), jour(fin), email, permis));
    }

    private static String texte(String valeur) {
        return valeur == null ? "null" : "\"" + valeur + "\"";
    }

    private static String jour(int decalage) {
        return AUJOURDHUI.plusDays(decalage).toString();
    }

    private static String jetonDe(Email email) {
        Matcher m = LIEN.matcher(email.texte());
        assertThat(m.find()).as("lien d'inscription dans : %s", email.texte()).isTrue();
        return m.group(1);
    }

    private Long vehicule(Agence agence, String immatriculation) {
        Vehicule vehicule = new Vehicule();
        vehicule.setImmatriculation(immatriculation);
        vehicule.setMarque("Peugeot");
        vehicule.setModele("208");
        vehicule.setCategorie(CategorieVehicule.CITADINE);
        vehicule.setTarifJournalier(new BigDecimal("50.00"));
        vehicule.setStatut(StatutVehicule.DISPONIBLE);
        vehicule.setAgence(agence);
        return vehiculeRepository.save(vehicule).getIdVehicule();
    }
}
