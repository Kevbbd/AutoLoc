package fr.ece.autoloc.service;

import fr.ece.autoloc.domain.Agence;
import fr.ece.autoloc.domain.Equipement;
import fr.ece.autoloc.domain.Periode;
import fr.ece.autoloc.domain.StatutReservation;
import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.repository.AgenceRepository;
import fr.ece.autoloc.repository.ReservationRepository;
import fr.ece.autoloc.repository.VehiculeRepository;
import fr.ece.autoloc.service.exception.RequeteInvalideException;
import fr.ece.autoloc.web.dto.AgenceVitrineDto;
import fr.ece.autoloc.web.dto.VehiculeVitrineDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Données en lecture seule de la vitrine publique.
 * Les entités sont converties en DTO à l'intérieur de la transaction :
 * aucune association LAZY n'est parcourue après sa fermeture (open-in-view est désactivé).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VitrineService {

    private final VehiculeRepository vehiculeRepository;
    private final AgenceRepository agenceRepository;
    private final ReservationRepository reservationRepository;
    private final ReglesLocation regles;

    /**
     * Flotte complète. Si une période est fournie, chaque véhicule indique s'il est libre sur
     * toute la période : deux requêtes au total, quelle que soit la taille de la flotte.
     */
    public List<VehiculeVitrineDto> listerVehicules(LocalDate debut, LocalDate fin) {
        Set<Long> occupes = null;
        if (debut != null || fin != null) {
            if (debut == null || fin == null) {
                throw new RequeteInvalideException(debut == null ? "dateDebut" : "dateFin",
                        "Indiquez à la fois la date de départ et la date de retour.");
            }
            Periode periode = regles.periodeReservable(debut, fin);
            occupes = new HashSet<>(reservationRepository.findIdVehiculesOccupes(
                    StatutReservation.ACTIFS, periode.debut(), periode.fin()));
        }
        Set<Long> vehiculesOccupes = occupes;
        return vehiculeRepository.findAllByOrderByMarqueAscModeleAsc().stream()
                .map(vehicule -> versDto(vehicule, disponibilite(vehicule, vehiculesOccupes)))
                .toList();
    }

    public List<AgenceVitrineDto> listerAgences() {
        return agenceRepository.findAll(Sort.by("nom")).stream()
                .map(VitrineService::versDto)
                .toList();
    }

    private static Boolean disponibilite(Vehicule vehicule, Set<Long> occupes) {
        if (occupes == null) {
            return null;
        }
        return vehicule.getStatut() != StatutVehicule.MAINTENANCE && !occupes.contains(vehicule.getIdVehicule());
    }

    private static VehiculeVitrineDto versDto(Vehicule vehicule, Boolean disponibleSurPeriode) {
        Agence agence = vehicule.getAgence();
        List<String> equipements = vehicule.getEquipements().stream()
                .map(Equipement::getLibelle)
                .sorted()
                .toList();
        return new VehiculeVitrineDto(
                vehicule.getIdVehicule(),
                vehicule.getImmatriculation(),
                vehicule.getMarque(),
                vehicule.getModele(),
                vehicule.getCategorie(),
                vehicule.getTarifJournalier(),
                vehicule.getStatut(),
                agence == null ? null : agence.getIdAgence(),
                agence == null ? null : agence.getNom(),
                agence == null ? null : agence.getVille(),
                equipements,
                disponibleSurPeriode);
    }

    private static AgenceVitrineDto versDto(Agence agence) {
        return new AgenceVitrineDto(
                agence.getIdAgence(),
                agence.getNom(),
                agence.getVille(),
                agence.getAdresse(),
                agence.getTelephone());
    }
}
