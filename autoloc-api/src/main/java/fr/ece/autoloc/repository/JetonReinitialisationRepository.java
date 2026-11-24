package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.JetonReinitialisation;
import fr.ece.autoloc.domain.Utilisateur;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface JetonReinitialisationRepository extends JpaRepository<JetonReinitialisation, Long> {

    /** Le jeton et son compte, en une requête. */
    @EntityGraph(attributePaths = "utilisateur")
    Optional<JetonReinitialisation> findByEmpreinte(String empreinte);

    /** Nombre de demandes récentes d'un compte : sert à limiter les envois d'e-mails. */
    long countByUtilisateurAndCreeLeAfter(Utilisateur utilisateur, LocalDateTime depuis);

    /**
     * Consomme le jeton de façon atomique : la mise à jour ne touche la ligne que si le jeton est
     * encore utilisable. Deux envois simultanés du même lien : un seul obtient 1, l'autre 0.
     */
    @Modifying
    @Query("""
            update JetonReinitialisation j set j.utiliseLe = :maintenant
            where j.idJeton = :id and j.utiliseLe is null and j.expireLe > :maintenant""")
    int consommer(@Param("id") Long idJeton, @Param("maintenant") LocalDateTime maintenant);

    /** Rend inutilisables tous les liens encore valides d'un compte (nouvelle demande, ou mot de passe changé). */
    @Modifying
    @Query("""
            update JetonReinitialisation j set j.expireLe = :maintenant
            where j.utilisateur = :utilisateur and j.utiliseLe is null and j.expireLe > :maintenant""")
    int expirerTous(@Param("utilisateur") Utilisateur utilisateur, @Param("maintenant") LocalDateTime maintenant);
}
