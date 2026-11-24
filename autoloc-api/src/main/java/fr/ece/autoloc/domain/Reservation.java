package fr.ece.autoloc.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "reservation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idReservation;

    /** Code secret remis au client (voir {@link CodeAcces}). Attribué à l'enregistrement, jamais modifié. */
    @Column(unique = true, length = CodeAcces.LONGUEUR)
    @Setter(AccessLevel.NONE)
    private String codeAcces;

    @Column(nullable = false)
    private LocalDate dateDebut;

    @Column(nullable = false)
    private LocalDate dateFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutReservation statut;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_client", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_vehicule", nullable = false)
    private Vehicule vehicule;

    /**
     * Côté inverse (la clé étrangère est dans contrat). Sans bytecode enhancement,
     * Hibernate ne peut pas rendre ce côté réellement LAZY : voir docs/fetch-strategy.md.
     */
    @OneToOne(mappedBy = "reservation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Contrat contrat;

    @PrePersist
    void attribuerCodeAcces() {
        if (codeAcces == null) {
            codeAcces = CodeAcces.nouveau();
        }
    }

    public void attacherContrat(Contrat contrat) {
        this.contrat = contrat;
        contrat.setReservation(this);
    }

    public Periode periode() {
        return new Periode(dateDebut, dateFin);
    }

    public boolean estActive() {
        return statut != null && statut.estActif();
    }

    /** Vrai si le véhicule est sorti ce jour-là au titre de cette réservation. */
    public boolean couvre(LocalDate jour) {
        return estActive() && periode().contient(jour);
    }

    /** Un client peut annuler une réservation active qui n'a pas encore commencé (ou qui commence aujourd'hui). */
    public boolean estAnnulableParClient(LocalDate aujourdhui) {
        return estActive() && !dateDebut.isBefore(aujourdhui);
    }

    /** Transition vers ANNULEE : le contrat associé n'est plus valide. */
    public void annuler() {
        if (!estActive()) {
            throw new RegleMetierException("Cette réservation est déjà " + statut.libelle() + " : elle ne peut plus être annulée.");
        }
        statut = StatutReservation.ANNULEE;
        if (contrat != null) {
            contrat.setValide(false);
        }
    }

    /** Transition CONFIRMEE vers TERMINEE : le véhicule a été restitué. */
    public void terminer() {
        if (statut != StatutReservation.CONFIRMEE) {
            throw new RegleMetierException("Seule une réservation confirmée peut être terminée (celle-ci est " + statut.libelle() + ").");
        }
        statut = StatutReservation.TERMINEE;
    }
}
