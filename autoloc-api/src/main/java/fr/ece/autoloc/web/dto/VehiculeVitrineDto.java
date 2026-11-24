package fr.ece.autoloc.web.dto;

import fr.ece.autoloc.domain.CategorieVehicule;
import fr.ece.autoloc.domain.StatutVehicule;

import java.math.BigDecimal;
import java.util.List;

/**
 * Véhicule tel qu'il est exposé publiquement sur la vitrine (aucune donnée sensible).
 * {@code disponibleSurPeriode} n'est renseigné que si une période a été demandée.
 */
public record VehiculeVitrineDto(
        Long idVehicule,
        String immatriculation,
        String marque,
        String modele,
        CategorieVehicule categorie,
        BigDecimal tarifJournalier,
        StatutVehicule statut,
        Long idAgence,
        String agence,
        String ville,
        List<String> equipements,
        Boolean disponibleSurPeriode) {
}
