package fr.ece.autoloc.service.compte;

/** Une inscription a été demandée pour une adresse qui a déjà un compte client : on prévient son titulaire. */
public record CompteDejaExistant(String email) {
}
