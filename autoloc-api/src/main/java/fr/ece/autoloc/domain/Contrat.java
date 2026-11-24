package fr.ece.autoloc.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "contrat")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Contrat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idContrat;

    @Column(nullable = false)
    private LocalDate dateSignature;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal montantTotal;

    @Column(nullable = false)
    private boolean valide;

    /** Côté propriétaire : unique = true garantit au plus un contrat par réservation. */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_reservation", nullable = false, unique = true)
    private Reservation reservation;

    /** Composition : un paiement n'existe pas sans son contrat. */
    @OneToMany(mappedBy = "contrat", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private List<Paiement> paiements = new ArrayList<>();

    public void ajouterPaiement(Paiement paiement) {
        paiements.add(paiement);
        paiement.setContrat(this);
    }

    public void retirerPaiement(Paiement paiement) {
        paiements.remove(paiement);
        paiement.setContrat(null);
    }

    public BigDecimal montantPaye() {
        return paiements.stream()
                .map(Paiement::getMontant)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal resteAPayer() {
        return montantTotal.subtract(montantPaye()).max(BigDecimal.ZERO);
    }

    /**
     * Enregistre un paiement. Invariant : la somme des paiements ne dépasse jamais le montant total.
     *
     * @return le paiement créé (persisté par cascade avec le contrat)
     */
    public Paiement encaisser(BigDecimal montant, ModePaiement mode, LocalDateTime date) {
        if (!valide) {
            throw new RegleMetierException("Ce contrat n'est plus valide : aucun paiement ne peut être enregistré.");
        }
        if (montant.signum() <= 0) {
            throw new RegleMetierException("Le montant d'un paiement doit être strictement positif.");
        }
        if (montant.compareTo(resteAPayer()) > 0) {
            throw new RegleMetierException("Le montant dépasse le reste à payer (" + resteAPayer() + " €).");
        }
        Paiement paiement = new Paiement();
        paiement.setMontant(montant);
        paiement.setModePaiement(mode);
        paiement.setDatePaiement(date);
        ajouterPaiement(paiement);
        return paiement;
    }
}
