package fr.ece.autoloc.web.dto;

import fr.ece.autoloc.domain.CategorieVehicule;
import fr.ece.autoloc.domain.ModePaiement;
import fr.ece.autoloc.domain.StatutReservation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Réservation avec son contrat, son véhicule et ses paiements, prête à afficher. */
public record ReservationDto(
        Long idReservation,
        /** Code secret du client, à lui seul communiqué (réponse à sa réservation, consultation) et à l'agence. */
        String codeAcces,
        StatutReservation statut,
        LocalDate dateDebut,
        LocalDate dateFin,
        long jours,
        BigDecimal montantTotal,
        BigDecimal montantPaye,
        BigDecimal resteAPayer,
        boolean contratValide,
        boolean annulableParClient,
        VehiculeResume vehicule,
        ClientResume client,
        List<PaiementResume> paiements) {

    public record VehiculeResume(
            Long idVehicule,
            String marque,
            String modele,
            String immatriculation,
            CategorieVehicule categorie,
            BigDecimal tarifJournalier,
            String agence,
            String ville) {
    }

    public record ClientResume(String prenom, String nom, String email, String telephone) {
    }

    public record PaiementResume(Long idPaiement, BigDecimal montant, ModePaiement modePaiement, LocalDateTime datePaiement) {
    }
}
