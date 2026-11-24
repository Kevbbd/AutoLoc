package fr.ece.autoloc.service.compte;

import java.time.Duration;

/** Lien de confirmation d'inscription à envoyer. {@link #toString()} masque le lien (il contient le jeton). */
public record LienInscriptionCree(String email, String lien, Duration validite) {

    @Override
    public String toString() {
        return "LienInscriptionCree[lien=<masqué>]";
    }
}
