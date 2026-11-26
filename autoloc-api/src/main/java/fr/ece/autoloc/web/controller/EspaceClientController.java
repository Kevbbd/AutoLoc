package fr.ece.autoloc.web.controller;

import fr.ece.autoloc.service.ReservationService;
import fr.ece.autoloc.service.compte.CompteClientService;
import fr.ece.autoloc.web.dto.ClientProfilDto;
import fr.ece.autoloc.web.dto.ReservationCompteRequest;
import fr.ece.autoloc.web.dto.ReservationDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

/**
 * Espace du client connecté (rôle CLIENT). Le client n'est jamais désigné par un paramètre de la requête :
 * c'est toujours l'adresse authentifiée ({@link Principal#getName()}). Il ne peut donc ni lire ni modifier
 * les réservations d'un autre.
 */
@Tag(name = "Espace client (CLIENT)", description = "Profil et réservations du client connecté.")
@RestController
@RequestMapping("/api/client")
@RequiredArgsConstructor
public class EspaceClientController {

    private final CompteClientService compteClientService;
    private final ReservationService reservationService;

    @GetMapping("/profil")
    public ClientProfilDto profil(Principal client) {
        return compteClientService.profil(client.getName());
    }

    @GetMapping("/reservations")
    public List<ReservationDto> reservations(Principal client) {
        return reservationService.reservationsDuCompte(client.getName());
    }

    @PostMapping("/reservations")
    public ResponseEntity<ReservationDto> reserver(Principal client, @Valid @RequestBody ReservationCompteRequest demande) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservationService.reserverPourCompte(client.getName(), demande));
    }

    @PostMapping("/reservations/{idReservation}/annulation")
    public ReservationDto annuler(Principal client, @PathVariable Long idReservation) {
        return reservationService.annulerPourCompte(client.getName(), idReservation);
    }
}
