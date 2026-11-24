package fr.ece.autoloc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Demande de réinitialisation de mot de passe.
 *
 * <p>La base ne contient que l'<b>empreinte SHA-256</b> du jeton envoyé par e-mail, jamais le jeton
 * lui-même : quelqu'un qui lirait cette table (sauvegarde, injection SQL…) ne pourrait pas s'en
 * servir pour changer un mot de passe. Un jeton est utilisable une seule fois, avant son expiration.
 */
@Entity
@Table(name = "jeton_reinitialisation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JetonReinitialisation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idJeton;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_utilisateur", nullable = false)
    private Utilisateur utilisateur;

    /** SHA-256 du jeton, en hexadécimal (64 caractères). */
    @Column(nullable = false, unique = true, length = 64)
    private String empreinte;

    @Column(nullable = false)
    private LocalDateTime creeLe;

    @Column(nullable = false)
    private LocalDateTime expireLe;

    /** Renseigné quand le jeton a servi : il ne peut plus resservir. */
    @Column
    private LocalDateTime utiliseLe;

    public JetonReinitialisation(Utilisateur utilisateur, String empreinte, LocalDateTime creeLe, LocalDateTime expireLe) {
        this.utilisateur = utilisateur;
        this.empreinte = empreinte;
        this.creeLe = creeLe;
        this.expireLe = expireLe;
    }

    public boolean estUtilisable(LocalDateTime maintenant) {
        return utiliseLe == null && maintenant.isBefore(expireLe);
    }
}
