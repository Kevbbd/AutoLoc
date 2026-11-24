package fr.ece.autoloc.service.compte;

import java.time.Duration;

/**
 * Événement publié quand un lien de réinitialisation est créé. {@code compte} est ce que la personne
 * saisit pour se connecter (identifiant ou e-mail). Le lien contient le jeton en clair : {@link #toString()}
 * le masque pour qu'il ne finisse jamais dans un journal par mégarde.
 */
public record LienReinitialisationCree(String email, String compte, boolean client, String lien, Duration validite) {

    @Override
    public String toString() {
        return "LienReinitialisationCree[compte=" + compte + ", lien=<masqué>]";
    }
}
