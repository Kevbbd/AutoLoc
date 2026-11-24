package fr.ece.autoloc.securite;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Réponse 429 au format RFC 9457, comme les autres erreurs de l'API, avec l'en-tête Retry-After. */
final class ReponseTropDeRequetes {

    static final int STATUT = 429;

    private ReponseTropDeRequetes() {
    }

    static void ecrire(HttpServletResponse reponse, Duration attente, String detail) throws IOException {
        long secondes = Math.max(1, (attente.toMillis() + 999) / 1000);
        reponse.setStatus(STATUT);
        reponse.setHeader("Retry-After", Long.toString(secondes));
        reponse.setContentType("application/problem+json");
        reponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
        reponse.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Trop de requêtes\",\"status\":429,\"detail\":\""
                + echapper(detail) + "\"}");
    }

    /** Durée lisible arrondie à la minute supérieure (« 1 minute », « 15 minutes »). */
    static String enMinutes(Duration attente) {
        long minutes = Math.max(1, (attente.toSeconds() + 59) / 60);
        return minutes + (minutes > 1 ? " minutes" : " minute");
    }

    private static String echapper(String texte) {
        return texte.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
