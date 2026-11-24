package fr.ece.autoloc.service;

import fr.ece.autoloc.domain.Agence;
import fr.ece.autoloc.domain.Client;
import fr.ece.autoloc.domain.CodeAcces;
import fr.ece.autoloc.domain.Contrat;
import fr.ece.autoloc.domain.Paiement;
import fr.ece.autoloc.domain.Periode;
import fr.ece.autoloc.domain.RegleMetierException;
import fr.ece.autoloc.domain.Reservation;
import fr.ece.autoloc.domain.StatutReservation;
import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.repository.ClientRepository;
import fr.ece.autoloc.repository.PaiementRepository;
import fr.ece.autoloc.repository.ReservationRepository;
import fr.ece.autoloc.repository.VehiculeRepository;
import fr.ece.autoloc.service.exception.RequeteInvalideException;
import fr.ece.autoloc.service.exception.RessourceIntrouvableException;
import fr.ece.autoloc.web.dto.AnnulationRequest;
import fr.ece.autoloc.web.dto.ConsultationRequest;
import fr.ece.autoloc.web.dto.DevisDto;
import fr.ece.autoloc.web.dto.EncaissementRequest;
import fr.ece.autoloc.web.dto.NouvelleReservationRequest;
import fr.ece.autoloc.web.dto.PeriodeReserveeDto;
import fr.ece.autoloc.web.dto.ReservationCompteRequest;
import fr.ece.autoloc.web.dto.ReservationDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Cycle de vie d'une réservation : devis, création, consultation, annulation, encaissement, restitution.
 *
 * <p>Invariants garantis :
 * <ul>
 *   <li>deux réservations actives d'un même véhicule ne se chevauchent jamais
 *       (verrou pessimiste sur le véhicule, puis contrôle de chevauchement) ;</li>
 *   <li>toute réservation possède un contrat dont le montant vaut tarif journalier × nombre de jours ;</li>
 *   <li>la somme des paiements d'un contrat ne dépasse jamais son montant total ;</li>
 *   <li>un véhicule est « LOUE » tant qu'une réservation active couvre la date du jour.</li>
 * </ul>
 *
 * <p>Concurrence : toute opération qui modifie des données prend ses verrous AVANT de lire quoi que ce soit,
 * toujours dans le même ordre — la réservation (s'il y en a une), puis le véhicule. La création, qui n'a pas
 * encore de réservation, ne verrouille que le véhicule : aucun cycle d'attente, donc aucun interblocage.
 * L'isolation READ COMMITTED garantit que les lectures faites après l'obtention des verrous voient tout ce
 * que les transactions précédentes sur ce véhicule ont validé (en REPEATABLE READ, l'instantané pourrait
 * dater d'avant l'attente du verrou).
 */
@Service
@RequiredArgsConstructor
@Transactional(isolation = Isolation.READ_COMMITTED)
public class ReservationService {

    private static final String RESERVATION_INTROUVABLE =
            "Aucune réservation ne correspond à ce code et à cette adresse e-mail.";

    private final ReservationRepository reservationRepository;
    private final VehiculeRepository vehiculeRepository;
    private final ClientRepository clientRepository;
    private final PaiementRepository paiementRepository;
    private final ReglesLocation regles;

    /* ===================== Côté client (vitrine) ===================== */

    @Transactional(readOnly = true)
    public DevisDto devis(Long idVehicule, LocalDate debut, LocalDate fin) {
        Periode periode = regles.periodeReservable(debut, fin);
        Vehicule vehicule = vehiculeRepository.findById(idVehicule)
                .orElseThrow(() -> vehiculeIntrouvable(idVehicule));
        String motif = motifIndisponibilite(vehicule, periode);
        return new DevisDto(
                vehicule.getIdVehicule(),
                periode.debut(),
                periode.fin(),
                periode.jours(),
                vehicule.getTarifJournalier(),
                regles.montant(vehicule.getTarifJournalier(), periode),
                motif == null,
                motif);
    }

    @Transactional(readOnly = true)
    public List<PeriodeReserveeDto> periodesReservees(Long idVehicule) {
        if (!vehiculeRepository.existsById(idVehicule)) {
            throw vehiculeIntrouvable(idVehicule);
        }
        return reservationRepository
                .findByVehiculeIdVehiculeAndStatutInAndDateFinAfterOrderByDateDebutAsc(
                        idVehicule, StatutReservation.ACTIFS, regles.aujourdhui())
                .stream()
                .map(r -> new PeriodeReserveeDto(r.getDateDebut(), r.getDateFin()))
                .toList();
    }

    /** Réservation sans compte : l'identité est saisie dans le formulaire. */
    public ReservationDto reserver(NouvelleReservationRequest demande) {
        return creerReservation(demande.idVehicule(), demande.dateDebut(), demande.dateFin(), () -> clientPour(demande));
    }

    /**
     * Réservation depuis un compte client : l'identité vient de la connexion (adresse authentifiée),
     * jamais du corps de la requête. Un client connecté ne peut donc réserver qu'à son propre nom.
     */
    public ReservationDto reserverPourCompte(String emailConnecte, ReservationCompteRequest demande) {
        return creerReservation(demande.idVehicule(), demande.dateDebut(), demande.dateFin(), () -> clientDuCompte(emailConnecte));
    }

    private ReservationDto creerReservation(Long idVehicule, LocalDate debut, LocalDate fin, Supplier<Client> titulaire) {
        Periode periode = regles.periodeReservable(debut, fin);

        // Premier accès à la base : le verrou sur le véhicule. Une demande concurrente pour ce véhicule
        // attend ici que la précédente soit validée, puis voit sa réservation dans le contrôle ci-dessous.
        Vehicule vehicule = vehiculeRepository.findByIdPourMiseAJour(idVehicule)
                .orElseThrow(() -> vehiculeIntrouvable(idVehicule));
        String motif = motifIndisponibilite(vehicule, periode);
        if (motif != null) {
            throw new RegleMetierException(motif);
        }

        Client client = titulaire.get();

        Reservation reservation = new Reservation();
        reservation.setDateDebut(periode.debut());
        reservation.setDateFin(periode.fin());
        reservation.setStatut(StatutReservation.CONFIRMEE);
        reservation.setVehicule(vehicule);
        client.ajouterReservation(reservation);

        Contrat contrat = new Contrat();
        contrat.setDateSignature(regles.aujourdhui());
        contrat.setMontantTotal(regles.montant(vehicule.getTarifJournalier(), periode));
        contrat.setValide(true);
        reservation.attacherContrat(contrat);

        reservationRepository.save(reservation);

        if (reservation.couvre(regles.aujourdhui())) {
            vehicule.setStatut(StatutVehicule.LOUE);
        }
        return versDto(reservation);
    }

    @Transactional(readOnly = true)
    public ReservationDto consulter(ConsultationRequest demande) {
        return versDto(reservationRepository.findByCodeAccesAndClientEmailIgnoreCase(codeValide(demande.code()), demande.email().trim())
                .orElseThrow(() -> new RessourceIntrouvableException(RESERVATION_INTROUVABLE)));
    }

    public ReservationDto annulerParClient(AnnulationRequest demande) {
        // Verrou sur la réservation d'abord (ordre fixe : réservation, puis véhicule), contrôle de l'e-mail ensuite.
        Reservation reservation = reservationRepository.findByCodeAccesPourMiseAJour(codeValide(demande.code()))
                .filter(r -> r.getClient().getEmail().equalsIgnoreCase(demande.email().trim()))
                .map(this::verrouillerVehiculeEtCharger)
                .orElseThrow(() -> new RessourceIntrouvableException(RESERVATION_INTROUVABLE));
        return annulerSiPossible(reservation);
    }

    /* ===================== Côté client connecté (compte) ===================== */

    @Transactional(readOnly = true)
    public List<ReservationDto> reservationsDuCompte(String emailConnecte) {
        return reservationRepository.findByClientEmailIgnoreCaseOrderByDateDebutDesc(emailConnecte).stream()
                .map(this::versDto)
                .toList();
    }

    /** Une réservation d'un autre client répond 404, comme si elle n'existait pas. */
    public ReservationDto annulerPourCompte(String emailConnecte, Long idReservation) {
        Reservation reservation = reservationRepository.findByIdPourMiseAJour(idReservation)
                .filter(r -> r.getClient().getEmail().equalsIgnoreCase(emailConnecte))
                .map(this::verrouillerVehiculeEtCharger)
                .orElseThrow(() -> new RessourceIntrouvableException("Cette réservation ne figure pas dans votre compte."));
        return annulerSiPossible(reservation);
    }

    private ReservationDto annulerSiPossible(Reservation reservation) {
        if (!reservation.estAnnulableParClient(regles.aujourdhui())) {
            throw new RegleMetierException(reservation.estActive()
                    ? "La location a déjà commencé : contactez votre agence pour l'interrompre."
                    : "Cette réservation est déjà " + reservation.getStatut().libelle() + ".");
        }
        reservation.annuler();
        libererVehiculeSiPossible(reservation);
        return versDto(reservation);
    }

    /* ===================== Côté agence (administration) ===================== */

    @Transactional(readOnly = true)
    public List<ReservationDto> toutes() {
        return reservationRepository.findAllByOrderByIdReservationDesc().stream()
                .map(this::versDto)
                .toList();
    }

    public ReservationDto annulerParAgence(Long idReservation) {
        Reservation reservation = verrouillerPourAgence(idReservation);
        reservation.annuler();
        libererVehiculeSiPossible(reservation);
        return versDto(reservation);
    }

    public ReservationDto terminer(Long idReservation) {
        Reservation reservation = verrouillerPourAgence(idReservation);
        reservation.terminer();
        libererVehiculeSiPossible(reservation);
        return versDto(reservation);
    }

    public ReservationDto encaisser(Long idReservation, EncaissementRequest demande) {
        // Le verrou rend le contrôle « montant ≤ reste à payer » atomique : deux encaissements
        // simultanés ne peuvent pas, ensemble, dépasser le montant du contrat.
        Reservation reservation = verrouillerPourAgence(idReservation);
        Contrat contrat = reservation.getContrat();
        if (contrat == null) {
            throw new RegleMetierException("Cette réservation n'a pas de contrat : aucun paiement possible.");
        }
        BigDecimal montant = demande.montant() != null ? demande.montant() : contrat.resteAPayer();
        if (montant.signum() == 0) {
            throw new RegleMetierException("Ce contrat est déjà entièrement payé.");
        }
        Paiement paiement = contrat.encaisser(
                montant.setScale(2, RoundingMode.HALF_UP), demande.modePaiement(), regles.maintenant());
        paiementRepository.save(paiement);
        return versDto(reservation);
    }

    /* ===================== Règles internes ===================== */

    /** {@code null} si le véhicule peut être réservé sur la période, sinon la raison lisible par le client. */
    private String motifIndisponibilite(Vehicule vehicule, Periode periode) {
        if (vehicule.getStatut() == StatutVehicule.MAINTENANCE) {
            return "Ce véhicule est en maintenance : il ne peut pas être réservé pour le moment.";
        }
        boolean chevauchement = reservationRepository.existsByVehiculeAndStatutInAndDateDebutLessThanAndDateFinGreaterThan(
                vehicule, StatutReservation.ACTIFS, periode.fin(), periode.debut());
        return chevauchement ? "Ce véhicule est déjà réservé sur tout ou partie de cette période." : null;
    }

    /**
     * Retrouve le client par son e-mail ou le crée. Un e-mail et un numéro de permis identifient
     * la même personne : une demande qui associerait l'un à l'autre d'un tiers est refusée.
     */
    private Client clientPour(NouvelleReservationRequest demande) {
        String email = demande.email().trim().toLowerCase(Locale.ROOT);
        String numPermis = demande.numPermis().trim().toUpperCase(Locale.ROOT);

        return clientRepository.findByEmailIgnoreCase(email)
                .map(existant -> {
                    if (!existant.getNumPermis().equalsIgnoreCase(numPermis)) {
                        throw new RegleMetierException(
                                "Cette adresse e-mail est déjà associée à un autre numéro de permis.");
                    }
                    return existant;
                })
                .orElseGet(() -> {
                    if (clientRepository.existsByNumPermisIgnoreCase(numPermis)) {
                        throw new RegleMetierException(
                                "Ce numéro de permis est déjà associé à une autre adresse e-mail.");
                    }
                    Client nouveau = new Client();
                    nouveau.setNom(demande.nom().trim());
                    nouveau.setPrenom(demande.prenom().trim());
                    nouveau.setEmail(email);
                    nouveau.setTelephone(demande.telephone() == null || demande.telephone().isBlank()
                            ? null : demande.telephone().trim());
                    nouveau.setNumPermis(numPermis);
                    nouveau.setDateInscription(regles.aujourdhui());
                    return clientRepository.save(nouveau);
                });
    }

    /** Remet le véhicule « DISPONIBLE » s'il était loué et qu'aucune autre réservation active ne couvre aujourd'hui. */
    private void libererVehiculeSiPossible(Reservation reservation) {
        Vehicule vehicule = reservation.getVehicule();
        LocalDate aujourdhui = regles.aujourdhui();
        if (vehicule.getStatut() != StatutVehicule.LOUE) {
            return;
        }
        boolean encoreLoue = reservationRepository
                .existsByVehiculeAndStatutInAndDateDebutLessThanEqualAndDateFinGreaterThanAndIdReservationNot(
                        vehicule, StatutReservation.ACTIFS, aujourdhui, aujourdhui, reservation.getIdReservation());
        if (!encoreLoue) {
            vehicule.setStatut(StatutVehicule.DISPONIBLE);
        }
    }

    private Client clientDuCompte(String emailConnecte) {
        return clientRepository.findByEmailIgnoreCase(emailConnecte)
                .orElseThrow(() -> new RessourceIntrouvableException("Fiche client introuvable."));
    }

    private static String codeValide(String saisie) {
        return CodeAcces.normaliser(saisie).orElseThrow(() -> new RequeteInvalideException("code",
                "Le code de réservation comporte " + CodeAcces.LONGUEUR + " lettres et chiffres (il figure sur votre confirmation)."));
    }

    /**
     * Verrouille la réservation puis son véhicule (ordre fixe, voir la Javadoc de la classe), puis charge
     * en une requête tout ce que la réponse affiche.
     */
    private Optional<Reservation> verrouiller(Long idReservation) {
        return reservationRepository.findByIdPourMiseAJour(idReservation).map(this::verrouillerVehiculeEtCharger);
    }

    /** La réservation est déjà verrouillée : verrouille son véhicule, puis charge tout ce que la réponse affiche. */
    private Reservation verrouillerVehiculeEtCharger(Reservation reservation) {
        vehiculeRepository.findByIdPourMiseAJour(reservation.getVehicule().getIdVehicule());
        return reservationRepository.findWithDetailsByIdReservation(reservation.getIdReservation()).orElseThrow();
    }

    private Reservation verrouillerPourAgence(Long idReservation) {
        return verrouiller(idReservation)
                .orElseThrow(() -> new RessourceIntrouvableException("Réservation n°" + idReservation + " introuvable."));
    }

    private static RessourceIntrouvableException vehiculeIntrouvable(Long idVehicule) {
        return new RessourceIntrouvableException("Véhicule n°" + idVehicule + " introuvable.");
    }

    private ReservationDto versDto(Reservation reservation) {
        Vehicule vehicule = reservation.getVehicule();
        Agence agence = vehicule.getAgence();
        Client client = reservation.getClient();
        Contrat contrat = reservation.getContrat();
        Periode periode = reservation.periode();

        BigDecimal montantTotal = contrat != null
                ? contrat.getMontantTotal()
                : regles.montant(vehicule.getTarifJournalier(), periode);
        BigDecimal montantPaye = contrat != null ? contrat.montantPaye() : BigDecimal.ZERO;
        BigDecimal resteAPayer = contrat != null ? contrat.resteAPayer() : montantTotal;
        List<ReservationDto.PaiementResume> paiements = contrat == null ? List.of() : contrat.getPaiements().stream()
                .sorted(Comparator.comparing(Paiement::getDatePaiement))
                .map(p -> new ReservationDto.PaiementResume(
                        p.getIdPaiement(), p.getMontant(), p.getModePaiement(), p.getDatePaiement()))
                .toList();

        return new ReservationDto(
                reservation.getIdReservation(),
                reservation.getCodeAcces(),
                reservation.getStatut(),
                reservation.getDateDebut(),
                reservation.getDateFin(),
                periode.jours(),
                montantTotal,
                montantPaye,
                resteAPayer,
                contrat != null && contrat.isValide(),
                reservation.estAnnulableParClient(regles.aujourdhui()),
                new ReservationDto.VehiculeResume(
                        vehicule.getIdVehicule(),
                        vehicule.getMarque(),
                        vehicule.getModele(),
                        vehicule.getImmatriculation(),
                        vehicule.getCategorie(),
                        vehicule.getTarifJournalier(),
                        agence == null ? null : agence.getNom(),
                        agence == null ? null : agence.getVille()),
                new ReservationDto.ClientResume(
                        client.getPrenom(), client.getNom(), client.getEmail(), client.getTelephone()),
                paiements);
    }
}
