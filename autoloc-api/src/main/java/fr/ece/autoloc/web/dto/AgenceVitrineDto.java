package fr.ece.autoloc.web.dto;

/** Coordonnées publiques d'une agence. */
public record AgenceVitrineDto(
        Long idAgence,
        String nom,
        String ville,
        String adresse,
        String telephone) {
}
