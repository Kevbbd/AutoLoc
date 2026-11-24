package fr.ece.autoloc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Lien de confirmation d'inscription envoyé à une adresse e-mail. Rien d'autre n'est créé avant que la
 * personne l'ouvre : ni compte, ni fiche client. Cliquer prouve qu'elle reçoit bien les e-mails de cette
 * adresse ; c'est seulement alors qu'elle choisit son mot de passe. Comme pour la réinitialisation, la
 * base ne contient que l'empreinte SHA-256 du jeton.
 */
@Entity
@Table(name = "jeton_inscription")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JetonInscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idJeton;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(nullable = false, unique = true, length = 64)
    private String empreinte;

    @Column(nullable = false)
    private LocalDateTime creeLe;

    @Column(nullable = false)
    private LocalDateTime expireLe;

    @Column
    private LocalDateTime utiliseLe;

    public JetonInscription(String email, String empreinte, LocalDateTime creeLe, LocalDateTime expireLe) {
        this.email = email.trim().toLowerCase(Locale.ROOT);
        this.empreinte = empreinte;
        this.creeLe = creeLe;
        this.expireLe = expireLe;
    }

    public boolean estUtilisable(LocalDateTime maintenant) {
        return utiliseLe == null && maintenant.isBefore(expireLe);
    }
}
