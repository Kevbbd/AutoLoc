package fr.ece.autoloc.web.dto;

import fr.ece.autoloc.service.compte.MotDePasseService.Espace;

/** Réponse à une réinitialisation : le message, et l'espace où se connecter ensuite (agence ou client). */
public record ReinitialisationDto(String message, Espace espace) {
}
