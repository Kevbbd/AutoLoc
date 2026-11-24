package fr.ece.autoloc.service.compte;

import java.time.LocalDateTime;

/** Événement publié après un changement de mot de passe, pour prévenir le titulaire du compte. */
public record MotDePasseModifie(String email, String compte, boolean client, LocalDateTime le) {
}
