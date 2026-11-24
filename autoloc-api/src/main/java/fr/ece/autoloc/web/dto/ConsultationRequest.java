package fr.ece.autoloc.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Accès d'un client à sa réservation : son code secret et son adresse e-mail. Transmis dans le corps
 * d'un POST pour n'apparaître ni dans l'URL ni dans les journaux du serveur.
 */
public record ConsultationRequest(
        @NotBlank(message = "Le code de réservation est obligatoire.")
        @Size(max = 20, message = "Le code de réservation n'est pas valide.")
        String code,

        @NotBlank(message = "L'adresse e-mail est obligatoire.")
        @Email(message = "L'adresse e-mail n'est pas valide.")
        @Size(max = 150, message = "L'adresse e-mail n'est pas valide.")
        String email) {
}
