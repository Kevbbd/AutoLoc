package fr.ece.autoloc.web.controller;

import fr.ece.autoloc.service.ReservationService;
import fr.ece.autoloc.service.VitrineService;
import fr.ece.autoloc.web.dto.AgenceVitrineDto;
import fr.ece.autoloc.web.dto.DevisDto;
import fr.ece.autoloc.web.dto.PeriodeReserveeDto;
import fr.ece.autoloc.web.dto.VehiculeVitrineDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Endpoints publics (sans authentification) qui alimentent la vitrine. */
@Tag(name = "Vitrine (public)", description = "Flotte, disponibilités, devis et agences.")
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class VitrineController {

    private final VitrineService vitrineService;
    private final ReservationService reservationService;

    /** Flotte ; avec {@code debut} et {@code fin}, indique aussi la disponibilité sur cette période. */
    @GetMapping("/vehicules")
    public List<VehiculeVitrineDto> vehicules(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return vitrineService.listerVehicules(debut, fin);
    }

    @GetMapping("/vehicules/{idVehicule}/devis")
    public DevisDto devis(
            @PathVariable Long idVehicule,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return reservationService.devis(idVehicule, debut, fin);
    }

    @GetMapping("/vehicules/{idVehicule}/periodes-reservees")
    public List<PeriodeReserveeDto> periodesReservees(@PathVariable Long idVehicule) {
        return reservationService.periodesReservees(idVehicule);
    }

    @GetMapping("/agences")
    public List<AgenceVitrineDto> agences() {
        return vitrineService.listerAgences();
    }
}
