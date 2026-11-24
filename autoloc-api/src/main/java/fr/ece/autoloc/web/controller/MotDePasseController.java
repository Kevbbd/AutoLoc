package fr.ece.autoloc.web.controller;

import fr.ece.autoloc.service.compte.MotDePasseService;
import fr.ece.autoloc.web.dto.DemandeReinitialisationRequest;
import fr.ece.autoloc.web.dto.JetonRequest;
import fr.ece.autoloc.web.dto.MessageDto;
import fr.ece.autoloc.web.dto.ReinitialisationDto;
import fr.ece.autoloc.web.dto.ReinitialisationRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Mot de passe oublié (agence et clients). Routes publiques : l'utilisateur, par définition, ne peut pas se connecter. */
@RestController
@RequestMapping("/api/public/mot-de-passe")
@RequiredArgsConstructor
public class MotDePasseController {

    private final MotDePasseService motDePasseService;

    /**
     * Toujours 202 avec le même message, que l'adresse corresponde à un compte ou non : la réponse
     * ne permet pas de savoir quelles adresses sont inscrites.
     */
    @PostMapping("/oubli")
    public ResponseEntity<MessageDto> demander(@Valid @RequestBody DemandeReinitialisationRequest demande) {
        motDePasseService.demanderReinitialisation(demande.email());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new MessageDto(
                "Si un compte correspond à cette adresse, un e-mail contenant un lien vient de lui être envoyé. "
                        + "Le lien est valable " + motDePasseService.validite().toMinutes() + " minutes."));
    }

    @PostMapping("/verification")
    public ResponseEntity<Void> verifier(@Valid @RequestBody JetonRequest demande) {
        motDePasseService.verifierJeton(demande.jeton());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reinitialisation")
    public ReinitialisationDto reinitialiser(@Valid @RequestBody ReinitialisationRequest demande) {
        return new ReinitialisationDto("Votre mot de passe a été modifié. Vous pouvez maintenant vous connecter.",
                motDePasseService.reinitialiser(demande.jeton(), demande.motDePasse()));
    }
}
