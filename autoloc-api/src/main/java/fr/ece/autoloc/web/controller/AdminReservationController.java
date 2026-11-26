package fr.ece.autoloc.web.controller;

import fr.ece.autoloc.service.ReservationService;
import fr.ece.autoloc.web.dto.EncaissementRequest;
import fr.ece.autoloc.web.dto.ReservationDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Back-office de l'agence : réservé au rôle ADMIN par SecurityConfig (/api/admin/**). */
@Tag(name = "Espace agence (ADMIN)", description = "Suivi des réservations, encaissements, restitutions et annulations.")
@RestController
@RequestMapping("/api/admin/reservations")
@RequiredArgsConstructor
public class AdminReservationController {

    private final ReservationService reservationService;

    @GetMapping
    public List<ReservationDto> toutes() {
        return reservationService.toutes();
    }

    @PostMapping("/{idReservation}/paiements")
    public ReservationDto encaisser(@PathVariable Long idReservation, @Valid @RequestBody EncaissementRequest demande) {
        return reservationService.encaisser(idReservation, demande);
    }

    @PostMapping("/{idReservation}/terminaison")
    public ReservationDto terminer(@PathVariable Long idReservation) {
        return reservationService.terminer(idReservation);
    }

    @PostMapping("/{idReservation}/annulation")
    public ReservationDto annuler(@PathVariable Long idReservation) {
        return reservationService.annulerParAgence(idReservation);
    }
}
