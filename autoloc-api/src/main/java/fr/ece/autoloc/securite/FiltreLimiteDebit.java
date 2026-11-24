package fr.ece.autoloc.securite;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Limite le débit des formulaires publics ({@code POST /api/public/**} : réservation, consultation, annulation,
 * mot de passe oublié) par adresse IP. Empêche d'essayer des codes en série, d'inonder la flotte de fausses
 * réservations ou de bombarder le serveur de mail. Les lectures (GET) ne sont pas limitées.
 *
 * <p>L'adresse est celle de la connexion TCP ({@code getRemoteAddr}), jamais l'en-tête X-Forwarded-For :
 * n'importe quel client peut écrire cet en-tête. Derrière un proxy, il faudrait configurer
 * {@code server.forward-headers-strategy} pour le seul proxy de confiance.
 */
public class FiltreLimiteDebit extends OncePerRequestFilter {

    private final LimiteurDebit limiteur;

    public FiltreLimiteDebit(LimiteurDebit limiteur) {
        this.limiteur = limiteur;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest requete) {
        String chemin = requete.getRequestURI().substring(requete.getContextPath().length());
        return !("POST".equalsIgnoreCase(requete.getMethod()) && chemin.startsWith("/api/public/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain chaine)
            throws ServletException, IOException {
        var attente = limiteur.enregistrer(requete.getRemoteAddr());
        if (attente.isPresent()) {
            ReponseTropDeRequetes.ecrire(reponse, attente.get(),
                    "Trop de demandes envoyées depuis votre connexion. Réessayez dans " + ReponseTropDeRequetes.enMinutes(attente.get()) + ".");
            return;
        }
        chaine.doFilter(requete, reponse);
    }
}
