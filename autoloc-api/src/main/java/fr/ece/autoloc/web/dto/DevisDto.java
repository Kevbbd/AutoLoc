package fr.ece.autoloc.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Prix et disponibilité d'un véhicule pour une période, calculés par le serveur (source de vérité). */
public record DevisDto(
        Long idVehicule,
        LocalDate dateDebut,
        LocalDate dateFin,
        long jours,
        BigDecimal tarifJournalier,
        BigDecimal montantTotal,
        boolean disponible,
        String motifIndisponibilite) {
}
