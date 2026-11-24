package fr.ece.autoloc.domain;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Optional;

/**
 * Code secret d'une réservation, remis au client : avec son adresse e-mail, c'est ce qui lui permet de la
 * consulter et de l'annuler. Le numéro de réservation, séquentiel, se devine ; l'e-mail d'un client n'est pas
 * un secret. Le code, lui, est tiré au hasard : 10 symboles parmi 31, soit 31^10 ≈ 8 × 10^14 possibilités
 * (≈ 49 bits), hors de portée d'essais successifs, d'autant que les requêtes publiques sont limitées par IP.
 *
 * <p>L'alphabet exclut les caractères qui se confondent à la lecture (0/O, 1/I/L).
 */
public final class CodeAcces {

    static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    public static final int LONGUEUR = 10;

    private static final SecureRandom ALEA = new SecureRandom();

    private CodeAcces() {
    }

    public static String nouveau() {
        StringBuilder code = new StringBuilder(LONGUEUR);
        for (int i = 0; i < LONGUEUR; i++) {
            code.append(ALPHABET.charAt(ALEA.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /** Accepte la saisie d'un humain (minuscules, espaces, tirets) ; vide si ce ne peut pas être un code. */
    public static Optional<String> normaliser(String saisie) {
        if (saisie == null) {
            return Optional.empty();
        }
        String code = saisie.replace(" ", "").replace("-", "").toUpperCase(Locale.ROOT);
        boolean valide = code.length() == LONGUEUR && code.chars().allMatch(c -> ALPHABET.indexOf(c) >= 0);
        return valide ? Optional.of(code) : Optional.empty();
    }

    /** {@code ABCDE-FGHJK} : plus facile à recopier. */
    public static String lisible(String code) {
        return code.substring(0, LONGUEUR / 2) + "-" + code.substring(LONGUEUR / 2);
    }
}
