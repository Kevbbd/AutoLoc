package fr.ece.autoloc.web.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Réservation depuis un compte client : pas d'identité dans la requête, elle vient de la connexion. */
public record ReservationCompteRequest(
        @NotNull(message = "Choisissez un véhicule.")
        Long idVehicule,

        @NotNull(message = "La date de départ est obligatoire.")
        LocalDate dateDebut,

        @NotNull(message = "La date de retour est obligatoire.")
        LocalDate dateFin) {
}
