package com.tfg.parking.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.tfg.parking.model.Vehiculo;
import com.tfg.parking.repository.VehiculoRepository;


@Service 
public class VehiculoService {

    private final VehiculoRepository vehiculoRepository;

    public VehiculoService(VehiculoRepository vehiculoRepository) {
        this.vehiculoRepository = vehiculoRepository;
    }

    // Comprobar si la matricula esta en la base de datos
    public boolean estaRegistrado(String Matricula){
        return vehiculoRepository.findByMatricula(Matricula).isPresent();
    }

    //Guardar un nuevo vehículo

    public Vehiculo registrarVehiculo(String matricula) {
        Vehiculo vehiculo = new Vehiculo(matricula, LocalDateTime.now());
        return vehiculoRepository.save(vehiculo);
    }   

    //Devolver todos los vehiculos registrados

    public List<Vehiculo> listaVehiculos(){
        return vehiculoRepository.findAll();
    }





}
