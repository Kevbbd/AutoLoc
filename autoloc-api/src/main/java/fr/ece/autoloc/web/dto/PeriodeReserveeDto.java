package fr.ece.autoloc.web.dto;

import java.time.LocalDate;

/** Période déjà réservée d'un véhicule : [dateDebut, dateFin), sans aucune information sur le client. */
public record PeriodeReserveeDto(LocalDate dateDebut, LocalDate dateFin) {
}
