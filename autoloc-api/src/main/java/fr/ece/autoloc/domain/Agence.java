package fr.ece.autoloc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "agence")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Agence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idAgence;

    @Column(nullable = false, length = 100)
    private String nom;

    @Column(nullable = false, length = 100)
    private String ville;

    @Column(nullable = false)
    private String adresse;

    @Column(length = 20)
    private String telephone;

    /** Pas de cascade : un véhicule survit à la suppression de son agence. */
    @OneToMany(mappedBy = "agence", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private List<Vehicule> vehicules = new ArrayList<>();

    /** Pas de cascade : un employé peut être réaffecté à une autre agence. */
    @OneToMany(mappedBy = "agence", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private List<Employe> employes = new ArrayList<>();

    public void ajouterVehicule(Vehicule vehicule) {
        vehicules.add(vehicule);
        vehicule.setAgence(this);
    }

    public void ajouterEmploye(Employe employe) {
        employes.add(employe);
        employe.setAgence(this);
    }
}
