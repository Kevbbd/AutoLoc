package fr.ece.autoloc.web.controller;

import fr.ece.autoloc.service.compte.CompteClientService;
import fr.ece.autoloc.web.dto.DemandeInscriptionRequest;
import fr.ece.autoloc.web.dto.FinalisationInscriptionRequest;
import fr.ece.autoloc.web.dto.InscriptionDto;
import fr.ece.autoloc.web.dto.JetonRequest;
import fr.ece.autoloc.web.dto.MessageDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Création d'un compte client, en deux temps (voir CompteClientService). Routes publiques. */
@Tag(name = "Inscription client (public)", description = "Création de compte confirmée par e-mail.")
@RestController
@RequestMapping("/api/public/clients/inscription")
@RequiredArgsConstructor
public class InscriptionController {

    private final CompteClientService compteClientService;

    /** Toujours 202 et le même message : la réponse ne dit pas si l'adresse a déjà un compte. */
    @PostMapping
    public ResponseEntity<MessageDto> demander(@Valid @RequestBody DemandeInscriptionRequest demande) {
        compteClientService.demanderInscription(demande.email());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new MessageDto(
                "Un e-mail vient d'être envoyé à cette adresse. Ouvrez le lien qu'il contient pour finaliser votre "
                        + "inscription (valable " + compteClientService.validite().toHours() + " heures)."));
    }

    @PostMapping("/verification")
    public InscriptionDto verifier(@Valid @RequestBody JetonRequest demande) {
        return compteClientService.verifier(demande.jeton());
    }

    @PostMapping("/finalisation")
    public ResponseEntity<MessageDto> finaliser(@Valid @RequestBody FinalisationInscriptionRequest demande) {
        compteClientService.finaliser(demande);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new MessageDto("Votre compte est créé. Vous pouvez maintenant vous connecter avec votre adresse e-mail."));
    }
}
