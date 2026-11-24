package fr.ece.autoloc.service.compte;

import fr.ece.autoloc.service.exception.RequeteInvalideException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Règles d'un nouveau mot de passe. Les mêmes règles sont affichées en direct dans la page de
 * réinitialisation ; elles sont de toute façon revérifiées ici.
 */
public final class RegleMotDePasse {

    public static final int LONGUEUR_MIN = 8;
    public static final int LONGUEUR_MAX = 64;
    /** BCrypt n'utilise que les 72 premiers octets : au-delà, Spring Security refuse le mot de passe. */
    static final int OCTETS_MAX_BCRYPT = 72;

    private static final String CHAMP = "motDePasse";

    private RegleMotDePasse() {
    }

    public static void verifier(String motDePasse, String identifiant) {
        if (motDePasse == null || motDePasse.length() < LONGUEUR_MIN) {
            throw refus("Le mot de passe doit contenir au moins " + LONGUEUR_MIN + " caractères.");
        }
        if (motDePasse.length() > LONGUEUR_MAX || motDePasse.getBytes(StandardCharsets.UTF_8).length > OCTETS_MAX_BCRYPT) {
            throw refus("Le mot de passe est trop long (" + LONGUEUR_MAX + " caractères au maximum).");
        }
        if (motDePasse.chars().noneMatch(Character::isLetter) || motDePasse.chars().noneMatch(Character::isDigit)) {
            throw refus("Le mot de passe doit contenir au moins une lettre et un chiffre.");
        }
        if (motDePasse.strip().length() != motDePasse.length()) {
            throw refus("Le mot de passe ne doit pas commencer ni finir par un espace.");
        }
        // En dessous de 3 caractères (« jo » dans jo@exemple.fr), la règle refuserait presque tout.
        if (identifiant != null && identifiant.length() >= 3
                && motDePasse.toLowerCase(Locale.ROOT).contains(identifiant.toLowerCase(Locale.ROOT))) {
            throw refus("Le mot de passe ne doit pas contenir votre identifiant.");
        }
    }

    private static RequeteInvalideException refus(String message) {
        return new RequeteInvalideException(CHAMP, message);
    }
}
