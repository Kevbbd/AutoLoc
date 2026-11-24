package fr.ece.autoloc.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Demande de réservation envoyée par la vitrine. Les règles de dates sont vérifiées par {@code ReglesLocation}. */
public record NouvelleReservationRequest(
        @NotNull(message = "Choisissez un véhicule.")
        Long idVehicule,

        @NotNull(message = "La date de départ est obligatoire.")
        LocalDate dateDebut,

        @NotNull(message = "La date de retour est obligatoire.")
        LocalDate dateFin,

        @NotBlank(message = "Le nom est obligatoire.")
        @Size(max = 50, message = "Le nom ne doit pas dépasser 50 caractères.")
        String nom,

        @NotBlank(message = "Le prénom est obligatoire.")
        @Size(max = 50, message = "Le prénom ne doit pas dépasser 50 caractères.")
        String prenom,

        @NotBlank(message = "L'adresse e-mail est obligatoire.")
        @Email(message = "L'adresse e-mail n'est pas valide.")
        @Size(max = 150, message = "L'adresse e-mail ne doit pas dépasser 150 caractères.")
        String email,

        @Size(max = 20, message = "Le téléphone ne doit pas dépasser 20 caractères.")
        @Pattern(regexp = "^[0-9+ .()-]*$", message = "Le téléphone ne doit contenir que des chiffres, espaces, points, tirets ou +.")
        String telephone,

        @NotBlank(message = "Le numéro de permis est obligatoire.")
        @Size(max = 30, message = "Le numéro de permis ne doit pas dépasser 30 caractères.")
        @Pattern(regexp = "^[A-Za-z0-9 -]+$", message = "Le numéro de permis ne doit contenir que des lettres, chiffres, espaces ou tirets.")
        String numPermis) {
}
