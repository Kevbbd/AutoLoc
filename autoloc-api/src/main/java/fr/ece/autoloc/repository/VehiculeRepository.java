package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.Vehicule;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VehiculeRepository extends JpaRepository<Vehicule, Long> {

    /** Véhicules avec leur agence et leurs équipements, en une seule requête (pas de N+1). */
    @EntityGraph(attributePaths = {"agence", "equipements"})
    List<Vehicule> findAllByOrderByMarqueAscModeleAsc();

    /**
     * Verrou exclusif sur la ligne du véhicule : deux réservations simultanées du même véhicule
     * sont sérialisées, la seconde voit la première.
     *
     * <p>Requête native volontaire. Avec {@code @Lock(PESSIMISTIC_WRITE)}, Hibernate (dialecte MySQL 8)
     * génère {@code FOR UPDATE OF v1_0}, syntaxe que MariaDB (XAMPP) refuse. Un {@code FOR UPDATE}
     * simple est compris par MySQL, MariaDB et H2 : le verrou ne dépend plus du dialecte.
     */
    @Query(value = "select v.* from vehicule v where v.id_vehicule = :id for update", nativeQuery = true)
    Optional<Vehicule> findByIdPourMiseAJour(@Param("id") Long idVehicule);
}
