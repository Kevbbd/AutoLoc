package fr.ece.autoloc.web.dto;

import fr.ece.autoloc.domain.ModePaiement;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** Paiement saisi en agence. Sans montant, le reste à payer est encaissé. */
public record EncaissementRequest(
        @Positive(message = "Le montant doit être strictement positif.")
        @Digits(integer = 8, fraction = 2, message = "Le montant doit avoir au plus 2 décimales.")
        BigDecimal montant,

        @NotNull(message = "Le mode de paiement est obligatoire.")
        ModePaiement modePaiement) {
}
