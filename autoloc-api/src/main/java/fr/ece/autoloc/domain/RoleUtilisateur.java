package fr.ece.autoloc.domain;

/**
 * Rôle d'un compte. Spring Security le reçoit préfixé : {@code ROLE_ADMIN}, {@code ROLE_USER}, {@code ROLE_CLIENT}.
 * <ul>
 *   <li>{@code USER}, {@code ADMIN} : personnel de l'agence, connexion par identifiant ;</li>
 *   <li>{@code CLIENT} : client de la vitrine, connexion par adresse e-mail, accès à ses seules réservations.</li>
 * </ul>
 */
public enum RoleUtilisateur {
    USER,
    ADMIN,
    CLIENT
}
