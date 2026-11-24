package fr.ece.autoloc.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Deuxième étape de l'inscription, depuis le lien reçu par e-mail. Prénom, nom et permis ne sont exigés
 * que pour un nouveau client (contrôle dans le service) ; leur format est vérifié ici s'ils sont fournis.
 */
public record FinalisationInscriptionRequest(
        @NotBlank(message = "Le lien est incomplet.")
        @Size(max = 100, message = "Le lien n'est pas valide.")
        String jeton,

        @Size(max = 50, message = "Le prénom ne doit pas dépasser 50 caractères.")
        String prenom,

        @Size(max = 50, message = "Le nom ne doit pas dépasser 50 caractères.")
        String nom,

        @Size(max = 20, message = "Le téléphone ne doit pas dépasser 20 caractères.")
        @Pattern(regexp = "^[0-9+ .()-]*$", message = "Le téléphone ne doit contenir que des chiffres, espaces, points, tirets ou +.")
        String telephone,

        @Size(max = 30, message = "Le numéro de permis ne doit pas dépasser 30 caractères.")
        @Pattern(regexp = "^[A-Za-z0-9 -]*$", message = "Le numéro de permis ne doit contenir que des lettres, chiffres, espaces ou tirets.")
        String numPermis,

        @NotBlank(message = "Choisissez un mot de passe.")
        String motDePasse) {
}
