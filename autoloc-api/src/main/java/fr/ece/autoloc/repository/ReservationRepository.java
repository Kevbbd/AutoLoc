package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.Reservation;
import fr.ece.autoloc.domain.StatutReservation;
import fr.ece.autoloc.domain.Vehicule;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /**
     * Chevauchement de périodes semi-ouvertes : [d1, f1) et [d2, f2) se chevauchent
     * si et seulement si d1 &lt; f2 et d2 &lt; f1. Les dates limites ne se chevauchent donc pas :
     * un véhicule rendu le 26 peut repartir le 26.
     */
    boolean existsByVehiculeAndStatutInAndDateDebutLessThanAndDateFinGreaterThan(
            Vehicule vehicule, Collection<StatutReservation> statuts, LocalDate fin, LocalDate debut);

    /** Une autre réservation active couvre-t-elle ce jour (debut ≤ jour &lt; fin) ? */
    boolean existsByVehiculeAndStatutInAndDateDebutLessThanEqualAndDateFinGreaterThanAndIdReservationNot(
            Vehicule vehicule, Collection<StatutReservation> statuts, LocalDate jour, LocalDate memeJour, Long idReservation);

    /** Identifiants des véhicules occupés sur la période, en une seule requête pour toute la flotte. */
    @Query("""
            select distinct r.vehicule.idVehicule from Reservation r
            where r.statut in :statuts and r.dateDebut < :fin and r.dateFin > :debut
            """)
    List<Long> findIdVehiculesOccupes(@Param("statuts") Collection<StatutReservation> statuts,
                                      @Param("debut") LocalDate debut,
                                      @Param("fin") LocalDate fin);

    /** Périodes encore à venir ou en cours d'un véhicule (pour griser le calendrier). */
    List<Reservation> findByVehiculeIdVehiculeAndStatutInAndDateFinAfterOrderByDateDebutAsc(
            Long idVehicule, Collection<StatutReservation> statuts, LocalDate apres);

    /**
     * Verrou exclusif sur la ligne de la réservation (SELECT … FOR UPDATE) : sérialise toutes les
     * opérations qui la modifient (annulation, restitution, encaissement). Requête native pour la
     * même raison que {@link VehiculeRepository#findByIdPourMiseAJour} : portable MySQL / MariaDB / H2.
     */
    @Query(value = "select r.* from reservation r where r.id_reservation = :id for update", nativeQuery = true)
    Optional<Reservation> findByIdPourMiseAJour(@Param("id") Long idReservation);

    /** Même verrou, pour l'annulation par le client qui ne connaît que son code. */
    @Query(value = "select r.* from reservation r where r.code_acces = :code for update", nativeQuery = true)
    Optional<Reservation> findByCodeAccesPourMiseAJour(@Param("code") String codeAcces);

    /** Réservations d'un client connecté, les plus récentes d'abord, avec tout ce que l'écran affiche. */
    @EntityGraph(attributePaths = {"client", "vehicule", "vehicule.agence", "contrat", "contrat.paiements"})
    List<Reservation> findByClientEmailIgnoreCaseOrderByDateDebutDesc(String email);

    /** Consultation par le client : il faut le code secret ET l'adresse e-mail de la réservation. */
    @EntityGraph(attributePaths = {"client", "vehicule", "vehicule.agence", "contrat", "contrat.paiements"})
    Optional<Reservation> findByCodeAccesAndClientEmailIgnoreCase(String codeAcces, String email);

    @EntityGraph(attributePaths = {"client", "vehicule", "vehicule.agence", "contrat", "contrat.paiements"})
    Optional<Reservation> findWithDetailsByIdReservation(Long idReservation);

    /** Back-office : tout est chargé en une requête (une seule collection, donc pas de produit cartésien). */
    @EntityGraph(attributePaths = {"client", "vehicule", "vehicule.agence", "contrat", "contrat.paiements"})
    List<Reservation> findAllByOrderByIdReservationDesc();
}
