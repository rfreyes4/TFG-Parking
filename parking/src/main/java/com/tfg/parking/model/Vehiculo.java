package com.tfg.parking.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "vehiculos")
public class Vehiculo {

    // Clave primaria: identificador único del vehículo
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Matrícula del vehículo autorizado
    @Column(nullable = false, unique = true)
    private String matricula;

    // Fecha de registro del vehículo
    @Column(nullable = false)
    private LocalDateTime fechaRegistro;

    public Vehiculo() {
    }

    public Vehiculo(String matricula, LocalDateTime fechaRegistro) {
        this.matricula = matricula;
        this.fechaRegistro = fechaRegistro;
    }

    public Long getId() {
        return id;
    }

    public String getMatricula() {
        return matricula;
    }

    public void setMatricula(String matricula) {
        this.matricula = matricula;
    }

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(LocalDateTime fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }
}