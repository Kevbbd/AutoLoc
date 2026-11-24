package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.JetonInscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface JetonInscriptionRepository extends JpaRepository<JetonInscription, Long> {

    Optional<JetonInscription> findByEmpreinte(String empreinte);

    /** Consommation atomique : un seul de deux envois simultanés du même lien obtient 1. */
    @Modifying
    @Query("""
            update JetonInscription j set j.utiliseLe = :maintenant
            where j.idJeton = :id and j.utiliseLe is null and j.expireLe > :maintenant""")
    int consommer(@Param("id") Long idJeton, @Param("maintenant") LocalDateTime maintenant);

    /** Seul le dernier lien envoyé à une adresse fonctionne. */
    @Modifying
    @Query("""
            update JetonInscription j set j.expireLe = :maintenant
            where j.email = :email and j.utiliseLe is null and j.expireLe > :maintenant""")
    int expirerTous(@Param("email") String email, @Param("maintenant") LocalDateTime maintenant);
}
