package fr.ece.autoloc.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Annulation par le client : son code secret et son adresse e-mail prouvent qu'il est titulaire de la réservation. */
public record AnnulationRequest(
        @NotBlank(message = "Le code de réservation est obligatoire.")
        @Size(max = 20, message = "Le code de réservation n'est pas valide.")
        String code,

        @NotBlank(message = "L'adresse e-mail est obligatoire.")
        @Email(message = "L'adresse e-mail n'est pas valide.")
        @Size(max = 150, message = "L'adresse e-mail n'est pas valide.")
        String email) {
}
