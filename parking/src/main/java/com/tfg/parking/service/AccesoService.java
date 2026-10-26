package com.tfg.parking.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.tfg.parking.model.Acceso;
import com.tfg.parking.repository.AccesoRepository;


@Service 
public class AccesoService {

    private final AccesoRepository accesoRepository;
    private final VehiculoService vehiculoService;

    public AccesoService(AccesoRepository accesoRepository, VehiculoService vehiculoService){
        this.accesoRepository = accesoRepository;
        this.vehiculoService = vehiculoService;
    }

    //Registrar el intento de acceso del vehiculo

    public Acceso registrarAcceso(String matricula){
        boolean estaAutorizado = vehiculoService.estaRegistrado(matricula);

        Acceso acceso = new Acceso(matricula, LocalDateTime.now(), estaAutorizado);

        return accesoRepository.save(acceso);
    }

    public List<Acceso> obtenerAccesos() {
        return accesoRepository.findAll();
}
}
