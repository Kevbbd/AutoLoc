package fr.ece.autoloc.web.dto;

/**
 * Ce que la page d'inscription affiche une fois le lien vérifié. Si l'adresse a déjà servi à réserver sans
 * compte, la fiche client existe : seul le mot de passe est demandé, et ses réservations seront rattachées.
 */
public record InscriptionDto(String email, boolean clientExistant, String prenom, String nom) {
}
