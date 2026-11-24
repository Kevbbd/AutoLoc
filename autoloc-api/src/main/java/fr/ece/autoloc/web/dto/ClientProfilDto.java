package fr.ece.autoloc.web.dto;

/** Fiche du client connecté (espace client, formulaire de réservation pré-rempli). */
public record ClientProfilDto(String prenom, String nom, String email, String telephone, String numPermis) {
}
