package fr.ece.autoloc.securite;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Placé avant l'authentification HTTP Basic : une tentative depuis un identifiant ou une adresse bloqués
 * reçoit 429 sans que le mot de passe soit même vérifié. N'est volontairement pas un bean Spring : il n'est
 * ajouté qu'à la chaîne de Spring Security (sinon Spring Boot l'enregistrerait une seconde fois).
 */
public class FiltreProtectionConnexion extends OncePerRequestFilter {

    private final ProtectionConnexion protection;

    public FiltreProtectionConnexion(ProtectionConnexion protection) {
        this.protection = protection;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain chaine)
            throws ServletException, IOException {
        String identifiant = identifiantBasic(requete.getHeader("Authorization"));
        if (identifiant != null) {
            var attente = protection.blocage(identifiant, requete.getRemoteAddr());
            if (attente.isPresent()) {
                ReponseTropDeRequetes.ecrire(reponse, attente.get(), "Trop de tentatives de connexion échouées. Réessayez dans "
                        + ReponseTropDeRequetes.enMinutes(attente.get()) + ".");
                return;
            }
        }
        chaine.doFilter(requete, reponse);
    }

    /** Identifiant d'un en-tête {@code Authorization: Basic …}, ou {@code null} s'il n'y en a pas de lisible. */
    static String identifiantBasic(String entete) {
        if (entete == null || !entete.regionMatches(true, 0, "Basic ", 0, 6)) {
            return null;
        }
        try {
            String decode = new String(Base64.getDecoder().decode(entete.substring(6).trim()), StandardCharsets.UTF_8);
            int separateur = decode.indexOf(':');
            return separateur < 0 ? null : decode.substring(0, separateur);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
