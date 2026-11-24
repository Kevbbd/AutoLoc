package fr.ece.autoloc.web;

import fr.ece.autoloc.domain.JetonReinitialisation;
import fr.ece.autoloc.domain.RoleUtilisateur;
import fr.ece.autoloc.domain.Utilisateur;
import fr.ece.autoloc.repository.JetonReinitialisationRepository;
import fr.ece.autoloc.repository.UtilisateurRepository;
import fr.ece.autoloc.service.compte.Jetons;
import fr.ece.autoloc.service.compte.MotDePasseService;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static fr.ece.autoloc.support.ClientHttp.champ;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mot de passe oublié de bout en bout, en HTTP réel : demande, e-mail, lien, nouveau mot de passe,
 * connexion. Chaque test travaille sur son propre compte « testeur », supprimé ensuite : les comptes
 * student et admin, utilisés par les autres classes de test, ne sont jamais modifiés.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MotDePasseApiTest {

    private static final Duration DELAI_EMAIL = Duration.ofSeconds(10);
    private static final Duration SILENCE = Duration.ofMillis(800);
    private static final Pattern LIEN = Pattern.compile("(http://localhost:8080/reinitialisation\\.html#jeton=([A-Za-z0-9_-]{43}))");
    private static final String EMAIL = "testeur@autoloc.test";
    private static final String MOT_DE_PASSE_INITIAL = "Depart2026";

    @LocalServerPort
    private int port;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private JetonReinitialisationRepository jetonRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MessagerieEnMemoire boite;

    private ClientHttp http;
    private Utilisateur compte;

    @BeforeEach
    void preparer() {
        http = new ClientHttp(port);
        boite.vider();
        compte = utilisateurRepository.save(
                new Utilisateur("testeur", EMAIL, passwordEncoder.encode(MOT_DE_PASSE_INITIAL), RoleUtilisateur.USER));
    }

    @AfterEach
    void nettoyer() {
        jetonRepository.deleteAll();
        utilisateurRepository.delete(compte);
    }

    @Test
    void lesComptesDuTpSontEnBaseAvecUneEmpreinteBCrypt() {
        Utilisateur admin = utilisateurRepository.findByIdentifiant("admin").orElseThrow();

        assertThat(admin.getRole()).isEqualTo(RoleUtilisateur.ADMIN);
        assertThat(admin.getMotDePasse()).startsWith("$2").isNotEqualTo("admin123");
        assertThat(passwordEncoder.matches("admin123", admin.getMotDePasse())).isTrue();
    }

    @Test
    void parcoursComplet() throws Exception {
        HttpResponse<String> demande = demander(EMAIL.toUpperCase());

        assertThat(demande.statusCode()).isEqualTo(202);
        Email email = boite.attendre(DELAI_EMAIL);
        assertThat(email.destinataire()).isEqualTo(EMAIL);
        assertThat(email.texte()).contains("« testeur »", "valable 30 minutes", "une seule fois");
        String jeton = jetonDe(email);

        assertThat(verifier(jeton).statusCode()).isEqualTo(204);

        // Mot de passe refusé : le lien reste utilisable
        HttpResponse<String> refus = reinitialiser(jeton, "court");
        assertThat(refus.statusCode()).isEqualTo(400);
        assertThat(refus.body()).contains("\"motDePasse\"");
        assertThat(verifier(jeton).statusCode()).isEqualTo(204);

        HttpResponse<String> succes = reinitialiser(jeton, "Nouveau2026");
        assertThat(succes.statusCode()).isEqualTo(200);
        assertThat(champ(succes.body(), "message")).contains("modifié");

        // Le titulaire est prévenu du changement
        Email alerte = boite.attendre(DELAI_EMAIL);
        assertThat(alerte.destinataire()).isEqualTo(EMAIL);
        assertThat(alerte.sujet()).contains("modifié");
        assertThat(alerte.texte()).contains("Si ce n'est pas vous").doesNotContain("jeton=");

        assertThat(http.get("/api/hello", "testeur", MOT_DE_PASSE_INITIAL).statusCode()).isEqualTo(401);
        assertThat(http.get("/api/hello", "testeur", "Nouveau2026").statusCode()).isEqualTo(200);

        // Usage unique
        HttpResponse<String> reutilisation = reinitialiser(jeton, "Encore2026");
        assertThat(reutilisation.statusCode()).isEqualTo(400);
        assertThat(reutilisation.body()).contains("\"jeton\"");
        assertThat(http.get("/api/hello", "testeur", "Nouveau2026").statusCode()).isEqualTo(200);
    }

    @Test
    void uneAdresseInconnueRecoitLaMemeReponseEtAucunEmail() throws Exception {
        HttpResponse<String> inconnue = demander("personne@autoloc.test");
        HttpResponse<String> connue = demander(EMAIL);

        assertThat(inconnue.statusCode()).isEqualTo(connue.statusCode()).isEqualTo(202);
        assertThat(inconnue.body()).isEqualTo(connue.body());
        List<Email> recus = boite.recusApres(SILENCE);
        assertThat(recus).extracting(Email::destinataire).containsExactly(EMAIL);
    }

    @Test
    void uneAdresseMalFormeeEstRefusee() {
        HttpResponse<String> reponse = demander("pas-une-adresse");

        assertThat(reponse.statusCode()).isEqualTo(400);
        assertThat(champ(reponse.body(), "email")).isEqualTo("L'adresse e-mail n'est pas valide.");
    }

    @Test
    void laBaseNeContientQueLEmpreinteDuJeton() throws Exception {
        demander(EMAIL);
        String jeton = jetonDe(boite.attendre(DELAI_EMAIL));

        List<JetonReinitialisation> lignes = jetonRepository.findAll();
        assertThat(lignes).hasSize(1);
        assertThat(lignes.get(0).getEmpreinte()).isEqualTo(Jetons.empreinte(jeton)).isNotEqualTo(jeton);
    }

    @Test
    void uneNouvelleDemandeInvalideLeLienPrecedent() throws Exception {
        demander(EMAIL);
        String premier = jetonDe(boite.attendre(DELAI_EMAIL));
        demander(EMAIL);
        String second = jetonDe(boite.attendre(DELAI_EMAIL));

        assertThat(verifier(premier).statusCode()).isEqualTo(400);
        assertThat(verifier(second).statusCode()).isEqualTo(204);
    }

    @Test
    void unLienExpireEstRefuse() {
        String jeton = Jetons.nouveau();
        LocalDateTime maintenant = LocalDateTime.now();
        jetonRepository.save(new JetonReinitialisation(compte, Jetons.empreinte(jeton),
                maintenant.minusMinutes(31), maintenant.minusMinutes(1)));

        assertThat(verifier(jeton).statusCode()).isEqualTo(400);
        assertThat(reinitialiser(jeton, "Nouveau2026").statusCode()).isEqualTo(400);
        assertThat(http.get("/api/hello", "testeur", MOT_DE_PASSE_INITIAL).statusCode()).isEqualTo(200);
    }

    @Test
    void unJetonInventeEstRefuse() {
        assertThat(verifier(Jetons.nouveau()).statusCode()).isEqualTo(400);
        assertThat(reinitialiser("n'importe quoi", "Nouveau2026").statusCode()).isEqualTo(400);
    }

    @Test
    void lesDemandesSontLimiteesParHeure() throws Exception {
        for (int i = 0; i < MotDePasseService.DEMANDES_MAX_PAR_HEURE + 2; i++) {
            assertThat(demander(EMAIL).statusCode()).isEqualTo(202);
        }

        assertThat(boite.recusApres(SILENCE)).hasSize(MotDePasseService.DEMANDES_MAX_PAR_HEURE);
    }

    @Test
    void leMotDePasseNePeutPasContenirLIdentifiant() throws Exception {
        demander(EMAIL);
        String jeton = jetonDe(boite.attendre(DELAI_EMAIL));

        HttpResponse<String> reponse = reinitialiser(jeton, "Testeur2026");

        assertThat(reponse.statusCode()).isEqualTo(400);
        assertThat(champ(reponse.body(), "motDePasse")).contains("identifiant");
    }

    /**
     * Huit envois simultanés du même lien : la consommation du jeton est une mise à jour
     * conditionnelle atomique, un seul envoi réussit.
     */
    @Test
    void unLienEnvoyePlusieursFoisEnMemeTempsNeSertQuUneFois() throws Exception {
        demander(EMAIL);
        String jeton = jetonDe(boite.attendre(DELAI_EMAIL));

        List<Integer> statuts = Simultanement.executer(8, n -> reinitialiser(jeton, "Parallele" + n).statusCode());

        assertThat(statuts).filteredOn(s -> s == 200).hasSize(1);
        assertThat(statuts).filteredOn(s -> s == 400).hasSize(7);
        assertThat(boite.attendre(DELAI_EMAIL).sujet()).as("une seule alerte de changement").contains("modifié");
        assertThat(boite.recusApres(SILENCE)).isEmpty();
    }

    /* ---------- Outils ---------- */

    private HttpResponse<String> demander(String email) {
        return http.post("/api/public/mot-de-passe/oubli", "{\"email\": \"" + email + "\"}");
    }

    private HttpResponse<String> verifier(String jeton) {
        return http.post("/api/public/mot-de-passe/verification", "{\"jeton\": \"" + jeton + "\"}");
    }

    private HttpResponse<String> reinitialiser(String jeton, String motDePasse) {
        return http.post("/api/public/mot-de-passe/reinitialisation",
                "{\"jeton\": \"" + jeton + "\", \"motDePasse\": \"" + motDePasse + "\"}");
    }

    private static String jetonDe(Email email) {
        Matcher m = LIEN.matcher(email.texte());
        assertThat(m.find()).as("lien de réinitialisation dans : %s", email.texte()).isTrue();
        return m.group(2);
    }
}
