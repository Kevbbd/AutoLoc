package fr.ece.autoloc.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rejoue la matrice 200 / 401 / 403 du TP avec de vraies requêtes HTTP
 * sur l'application démarrée (aucun mock).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SecurityRulesTest {

    private static final Map<String, String> MOTS_DE_PASSE = Map.of(
            "student", "student123",
            "admin", "admin123");

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @ParameterizedTest(name = "GET {0} en tant que {1} -> {2}")
    @CsvSource({
            "/api/public/ping,     anonyme, 200",
            "/api/public/ping,     student, 200",
            "/api/public/ping,     admin,   200",
            "/api/hello,           anonyme, 401",
            "/api/hello,           student, 200",
            "/api/hello,           admin,   200",
            "/api/admin/report,    anonyme, 401",
            "/api/admin/report,    student, 403",
            "/api/admin/report,    admin,   200",
            "/actuator/health,     anonyme, 200",
            "/actuator/prometheus, anonyme, 200",
            "/,                    anonyme, 200",
            "/css/autoloc.css,     anonyme, 200",
            "/api/public/vehicules, anonyme, 200",
            "/admin.html,          anonyme, 200",
            "/api/admin/reservations, anonyme, 401",
            "/api/admin/reservations, student, 403",
            "/api/admin/reservations, admin,   200",
            "/reinitialisation.html, anonyme, 200",
            "/inscription.html,      anonyme, 200",
            "/api/client/profil,     anonyme, 401",
            "/api/client/profil,     student, 403",
            "/api/client/profil,     admin,   403",
            "/api/client/reservations, student, 403",
            "/js/reinitialisation.js, anonyme, 200",
            "/swagger-ui/index.html, anonyme, 200",
            "/v3/api-docs,           anonyme, 200"
    })
    void appliqueLesReglesDAcces(String chemin, String utilisateur, int statutAttendu) throws Exception {
        HttpRequest.Builder requete = HttpRequest.newBuilder(URI.create("http://localhost:" + port + chemin)).GET();
        if (MOTS_DE_PASSE.containsKey(utilisateur)) {
            requete.header("Authorization", basicAuth(utilisateur, MOTS_DE_PASSE.get(utilisateur)));
        }

        HttpResponse<String> reponse = httpClient.send(requete.build(), HttpResponse.BodyHandlers.ofString());

        assertThat(reponse.statusCode()).isEqualTo(statutAttendu);
    }

    /** Les comptes sont lus en base : un mauvais mot de passe ou un compte inconnu donne 401. */
    @ParameterizedTest(name = "{0} / {1} -> 401")
    @CsvSource({"admin, mauvais", "inconnu, admin123", "admin, ADMIN123"})
    void refuseDesIdentifiantsIncorrects(String utilisateur, String motDePasse) throws Exception {
        HttpRequest requete = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/hello"))
                .header("Authorization", basicAuth(utilisateur, motDePasse))
                .GET().build();

        assertThat(httpClient.send(requete, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
    }

    @Test
    void lIdentifiantNeTientPasCompteDeLaCasse() throws Exception {
        HttpRequest requete = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/hello"))
                .header("Authorization", basicAuth("ADMIN", "admin123"))
                .GET().build();

        assertThat(httpClient.send(requete, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
    }

    /** En-têtes de protection présents sur les pages comme sur l'API. */
    @ParameterizedTest(name = "en-têtes de sécurité sur {0}")
    @CsvSource({"/", "/admin.html", "/inscription.html", "/api/public/vehicules"})
    void ajouteLesEnTetesDeProtection(String chemin) throws Exception {
        HttpResponse<String> reponse = httpClient.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + chemin)).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(reponse.headers().firstValue("Content-Security-Policy")).hasValueSatisfying(csp -> assertThat(csp)
                .contains("default-src 'self'", "script-src 'self'", "object-src 'none'", "frame-ancestors 'none'")
                .doesNotContain("unsafe-inline", "unsafe-eval"));
        assertThat(reponse.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        assertThat(reponse.headers().firstValue("X-Frame-Options")).contains("DENY");
        assertThat(reponse.headers().firstValue("Referrer-Policy")).contains("same-origin");
        assertThat(reponse.headers().firstValue("Permissions-Policy")).hasValueSatisfying(p -> assertThat(p).contains("camera=()"));
    }

    /**
     * Swagger UI : un 401 sans le défi WWW-Authenticate, pour que le navigateur n'ouvre pas sa fenêtre de
     * connexion pendant un « Try it out ». Postman, curl et les navigateurs reçoivent toujours le défi Basic.
     */
    @Test
    void unAppelDepuisSwaggerUiNeDeclenchePasLaFenetreDeConnexion() throws Exception {
        URI hello = URI.create("http://localhost:" + port + "/api/hello");

        HttpResponse<String> direct = httpClient.send(HttpRequest.newBuilder(hello).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> depuisSwagger = httpClient.send(HttpRequest.newBuilder(hello)
                        .header("Referer", "http://localhost:" + port + "/swagger-ui/index.html").GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(direct.statusCode()).isEqualTo(401);
        assertThat(direct.headers().firstValue("WWW-Authenticate")).hasValueSatisfying(v -> assertThat(v).startsWith("Basic"));
        assertThat(depuisSwagger.statusCode()).isEqualTo(401);
        assertThat(depuisSwagger.headers().firstValue("WWW-Authenticate")).isEmpty();
    }

    /** La description OpenAPI contient les routes du TP et le schéma HTTP Basic ; la page Swagger garde une CSP sans script en ligne. */
    @Test
    void documenteLApiEtProtegeLaPageSwagger() throws Exception {
        HttpResponse<String> description = httpClient.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> page = httpClient.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/swagger-ui/index.html")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(description.body()).contains("/api/public/ping", "/api/hello", "/api/admin/report", "basicAuth");
        assertThat(page.headers().firstValue("Content-Security-Policy")).hasValueSatisfying(csp -> assertThat(csp)
                .contains("script-src 'self'", "object-src 'none'", "frame-ancestors 'none'")
                .doesNotContain("unsafe-eval"));
    }

    private static String basicAuth(String utilisateur, String motDePasse) {
        String identifiants = utilisateur + ":" + motDePasse;
        return "Basic " + Base64.getEncoder().encodeToString(identifiants.getBytes(StandardCharsets.UTF_8));
    }
}
