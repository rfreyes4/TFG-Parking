package com.tfg.parking.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

// Tabla de registros de acceso al sistema
@Entity
@Table(name = "accesos")
public class Acceso {

    // Primary Key: identificador único
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Matrícula detectada
    @Column(nullable = false)
    private String matricula;

    // Fecha y hora del intento de acceso
    @Column(nullable = false)
    private LocalDateTime fechaHora;

    // Indica si el acceso fue aceptado o denegado
    @Column(nullable = false)
    private boolean autorizado;

    public Acceso() {
    }

    public Acceso(String matricula, LocalDateTime fechaHora, boolean autorizado) {
        this.matricula = matricula;
        this.fechaHora = fechaHora;
        this.autorizado = autorizado;
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

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(LocalDateTime fechaHora) {
        this.fechaHora = fechaHora;
    }

    public boolean isAutorizado() {
        return autorizado;
    }

    public void setAutorizado(boolean autorizado) {
        this.autorizado = autorizado;
    }
}