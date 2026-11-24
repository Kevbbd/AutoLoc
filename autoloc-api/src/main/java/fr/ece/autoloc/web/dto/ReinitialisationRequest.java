package fr.ece.autoloc.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Les règles du mot de passe lui-même sont dans {@code RegleMotDePasse} (elles dépendent du compte). */
public record ReinitialisationRequest(
        @NotBlank(message = "Le lien est incomplet.")
        @Size(max = 100, message = "Le lien n'est pas valide.")
        String jeton,

        @NotBlank(message = "Choisissez un nouveau mot de passe.")
        String motDePasse) {
}
