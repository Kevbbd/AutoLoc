package fr.ece.autoloc.web;

import fr.ece.autoloc.domain.RoleUtilisateur;
import fr.ece.autoloc.domain.Utilisateur;
import fr.ece.autoloc.repository.UtilisateurRepository;
import fr.ece.autoloc.support.ClientHttp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Blocage après échecs de connexion, de bout en bout : vraies requêtes HTTP Basic, vrais événements
 * de Spring Security. Chaque test utilise ses propres identifiants (un blocage dure 15 minutes).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ProtectionConnexionApiTest {

    private static final AtomicInteger NUMERO = new AtomicInteger();
    private static final String MOT_DE_PASSE = "Verrou2026";

    @LocalServerPort
    private int port;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private ClientHttp http;
    private Utilisateur compte;

    @BeforeEach
    void preparer() {
        http = new ClientHttp(port);
        int n = NUMERO.incrementAndGet();
        compte = utilisateurRepository.save(new Utilisateur("verrou" + n + "-" + System.nanoTime(),
                "verrou" + n + "-" + System.nanoTime() + "@autoloc.test", passwordEncoder.encode(MOT_DE_PASSE), RoleUtilisateur.USER));
    }

    @AfterEach
    void nettoyer() {
        utilisateurRepository.delete(compte);
    }

    @Test
    void cinqEchecsBloquentLeCompteMemeAvecLeBonMotDePasse() {
        for (int i = 1; i <= 5; i++) {
            assertThat(connexion(compte.getIdentifiant(), "mauvais" + i).statusCode()).as("essai %d", i).isEqualTo(401);
        }

        HttpResponse<String> bloquee = connexion(compte.getIdentifiant(), MOT_DE_PASSE);

        assertThat(bloquee.statusCode()).isEqualTo(429);
        assertThat(bloquee.headers().firstValue("Retry-After")).hasValueSatisfying(
                secondes -> assertThat(Long.parseLong(secondes)).isBetween(1L, 900L));
        assertThat(bloquee.body()).contains("Trop de tentatives de connexion");
        assertThat(connexion(compte.getIdentifiant().toUpperCase(), MOT_DE_PASSE).statusCode())
                .as("la casse ne contourne pas le blocage").isEqualTo(429);
    }

    @Test
    void unIdentifiantInexistantEstTraiteCommeUnVrai() {
        String fantome = "fantome-" + System.nanoTime();
        for (int i = 1; i <= 5; i++) {
            assertThat(connexion(fantome, "x" + i).statusCode()).isEqualTo(401);
        }

        // Même réponse que pour un compte existant : le blocage ne révèle pas quels comptes existent
        assertThat(connexion(fantome, "x").statusCode()).isEqualTo(429);
    }

    @Test
    void uneConnexionReussieRemetLeCompteurAZero() {
        for (int i = 0; i < 4; i++) {
            assertThat(connexion(compte.getIdentifiant(), "mauvais").statusCode()).isEqualTo(401);
        }
        assertThat(connexion(compte.getIdentifiant(), MOT_DE_PASSE).statusCode()).isEqualTo(200);

        for (int i = 0; i < 4; i++) {
            assertThat(connexion(compte.getIdentifiant(), "mauvais").statusCode()).isEqualTo(401);
        }
        assertThat(connexion(compte.getIdentifiant(), MOT_DE_PASSE).statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> connexion(String identifiant, String motDePasse) {
        return http.get("/api/hello", identifiant, motDePasse);
    }
}
