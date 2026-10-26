package com.tfg.parking.repository;

import com.tfg.parking.model.Vehiculo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VehiculoRepository extends  JpaRepository<Vehiculo, Long>{
    
    Optional<Vehiculo> findByMatricula(String matricula);
}
 