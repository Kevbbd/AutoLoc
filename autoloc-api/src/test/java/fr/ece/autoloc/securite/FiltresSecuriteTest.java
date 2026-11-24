package fr.ece.autoloc.securite;

import fr.ece.autoloc.support.HorlogeReglable;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/** Les deux filtres de sécurité, avec de vraies requêtes servlet simulées. */
class FiltresSecuriteTest {

    private final HorlogeReglable horloge = new HorlogeReglable(Instant.parse("2027-03-10T09:00:00Z"));

    @Test
    void lesFormulairesPublicsSontLimitesParAdresse() throws Exception {
        FiltreLimiteDebit filtre = new FiltreLimiteDebit(new LimiteurDebit(2, Duration.ofMinutes(1), horloge));

        assertThat(passer(filtre, "POST", "/api/public/reservations", "10.0.0.1").getStatus()).isEqualTo(200);
        assertThat(passer(filtre, "POST", "/api/public/reservations/consultation", "10.0.0.1").getStatus()).isEqualTo(200);
        MockHttpServletResponse refusee = passer(filtre, "POST", "/api/public/mot-de-passe/oubli", "10.0.0.1");

        assertThat(refusee.getStatus()).isEqualTo(429);
        assertThat(refusee.getHeader("Retry-After")).isEqualTo("60");
        assertThat(refusee.getContentType()).startsWith("application/problem+json");
        assertThat(refusee.getContentAsString(StandardCharsets.UTF_8)).contains("\"status\":429", "Réessayez dans 1 minute");
        assertThat(passer(filtre, "POST", "/api/public/reservations", "10.0.0.2").getStatus()).as("autre adresse").isEqualTo(200);
    }

    @Test
    void lesLecturesEtLEspaceAgenceNeSontPasComptes() throws Exception {
        FiltreLimiteDebit filtre = new FiltreLimiteDebit(new LimiteurDebit(1, Duration.ofMinutes(1), horloge));

        for (int i = 0; i < 5; i++) {
            assertThat(passer(filtre, "GET", "/api/public/vehicules", "10.0.0.1").getStatus()).isEqualTo(200);
            assertThat(passer(filtre, "POST", "/api/admin/reservations/1/paiements", "10.0.0.1").getStatus()).isEqualTo(200);
        }
    }

    @Test
    void unIdentifiantBloqueRecoit429SansVerificationDuMotDePasse() throws Exception {
        ProtectionConnexion protection = new ProtectionConnexion(horloge, 5, 20, Duration.ofMinutes(15), Duration.ofMinutes(15));
        FiltreProtectionConnexion filtre = new FiltreProtectionConnexion(protection);
        for (int i = 0; i < 5; i++) {
            protection.surEchec(echec("Admin", "10.0.0.1"));
        }

        MockHttpServletResponse reponse = passerAvecIdentifiants(filtre, "admin", "le-bon-mot-de-passe", "10.0.0.9");

        assertThat(reponse.getStatus()).isEqualTo(429);
        assertThat(reponse.getHeader("Retry-After")).isEqualTo("900");
        assertThat(reponse.getContentAsString(StandardCharsets.UTF_8)).contains("Réessayez dans 15 minutes");
        assertThat(passerAvecIdentifiants(filtre, "student", "x", "10.0.0.9").getStatus()).as("autre compte").isEqualTo(200);
    }

    @Test
    void uneAdresseQuiEssaieBeaucoupDIdentifiantsEstBloquee() throws Exception {
        ProtectionConnexion protection = new ProtectionConnexion(horloge, 5, 20, Duration.ofMinutes(15), Duration.ofMinutes(15));
        FiltreProtectionConnexion filtre = new FiltreProtectionConnexion(protection);
        for (int i = 0; i < 20; i++) {
            protection.surEchec(echec("victime" + i, "10.0.0.66"));
        }

        assertThat(passerAvecIdentifiants(filtre, "personne", "x", "10.0.0.66").getStatus()).isEqualTo(429);
        assertThat(passerAvecIdentifiants(filtre, "personne", "x", "10.0.0.67").getStatus()).isEqualTo(200);
    }

    @Test
    void lIdentifiantEstLuDansLEnTeteBasic() {
        assertThat(FiltreProtectionConnexion.identifiantBasic(basic("admin", "a:b:c"))).isEqualTo("admin");
        assertThat(FiltreProtectionConnexion.identifiantBasic("basic " + base64("Élodie:x"))).isEqualTo("Élodie");
        assertThat(FiltreProtectionConnexion.identifiantBasic(null)).isNull();
        assertThat(FiltreProtectionConnexion.identifiantBasic("Bearer abc")).isNull();
        assertThat(FiltreProtectionConnexion.identifiantBasic("Basic !!!pas-du-base64")).isNull();
        assertThat(FiltreProtectionConnexion.identifiantBasic("Basic " + base64("sans-deux-points"))).isNull();
    }

    /* ---------- Outils ---------- */

    private static MockHttpServletResponse passer(Filter filtre, String methode, String chemin, String ip)
            throws Exception {
        MockHttpServletRequest requete = new MockHttpServletRequest(methode, chemin);
        requete.setRemoteAddr(ip);
        MockHttpServletResponse reponse = new MockHttpServletResponse();
        filtre.doFilter(requete, reponse, new MockFilterChain());
        return reponse;
    }

    private static MockHttpServletResponse passerAvecIdentifiants(Filter filtre, String identifiant,
            String motDePasse, String ip) throws Exception {
        MockHttpServletRequest requete = new MockHttpServletRequest("GET", "/api/admin/reservations");
        requete.setRemoteAddr(ip);
        requete.addHeader("Authorization", basic(identifiant, motDePasse));
        MockHttpServletResponse reponse = new MockHttpServletResponse();
        filtre.doFilter(requete, reponse, new MockFilterChain());
        return reponse;
    }

    private static AuthenticationFailureBadCredentialsEvent echec(String identifiant, String ip) {
        MockHttpServletRequest requete = new MockHttpServletRequest();
        requete.setRemoteAddr(ip);
        UsernamePasswordAuthenticationToken tentative = UsernamePasswordAuthenticationToken.unauthenticated(identifiant, "x");
        tentative.setDetails(new WebAuthenticationDetails(requete));
        return new AuthenticationFailureBadCredentialsEvent(tentative, new BadCredentialsException("mauvais mot de passe"));
    }

    private static String basic(String identifiant, String motDePasse) {
        return "Basic " + base64(identifiant + ":" + motDePasse);
    }

    private static String base64(String texte) {
        return Base64.getEncoder().encodeToString(texte.getBytes(StandardCharsets.UTF_8));
    }
}
