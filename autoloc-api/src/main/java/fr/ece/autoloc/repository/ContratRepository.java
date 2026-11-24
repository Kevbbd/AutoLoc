package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.Contrat;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ContratRepository extends JpaRepository<Contrat, Long> {

    /**
     * Charge le contrat et ses paiements en une seule requête (LEFT JOIN),
     * sans passer la collection en EAGER pour tous les autres usages.
     */
    @EntityGraph(attributePaths = "paiements")
    Optional<Contrat> findWithPaiementsByIdContrat(Long idContrat);
}
