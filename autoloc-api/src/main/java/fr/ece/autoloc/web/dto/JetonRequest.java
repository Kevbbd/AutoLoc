package fr.ece.autoloc.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JetonRequest(
        @NotBlank(message = "Le lien est incomplet.")
        @Size(max = 100, message = "Le lien n'est pas valide.")
        String jeton) {
}
