package fr.ece.autoloc.web.controller;

import fr.ece.autoloc.service.ReservationService;
import fr.ece.autoloc.web.dto.AnnulationRequest;
import fr.ece.autoloc.web.dto.ConsultationRequest;
import fr.ece.autoloc.web.dto.NouvelleReservationRequest;
import fr.ece.autoloc.web.dto.ReservationDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * Réservation en ligne, sans compte : le client retrouve sa réservation avec le code secret remis à la
 * réservation et son adresse e-mail, toujours transmis dans le corps de la requête.
 */
@RestController
@RequestMapping("/api/public/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    public ResponseEntity<ReservationDto> reserver(@Valid @RequestBody NouvelleReservationRequest demande) {
        ReservationDto reservation = reservationService.reserver(demande);
        URI emplacement = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/public/reservations/{id}")
                .buildAndExpand(reservation.idReservation())
                .toUri();
        return ResponseEntity.created(emplacement).body(reservation);
    }

    @PostMapping("/consultation")
    public ReservationDto consulter(@Valid @RequestBody ConsultationRequest demande) {
        return reservationService.consulter(demande);
    }

    @PostMapping("/annulation")
    public ReservationDto annuler(@Valid @RequestBody AnnulationRequest demande) {
        return reservationService.annulerParClient(demande);
    }
}
