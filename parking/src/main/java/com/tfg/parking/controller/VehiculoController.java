package com.tfg.parking.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tfg.parking.model.Vehiculo;
import com.tfg.parking.service.VehiculoService;

@RestController 
@RequestMapping("/api/vehiculos")
public class VehiculoController {
    
    private final VehiculoService vehiculoService;

    public VehiculoController(VehiculoService vehiculoService){
        this.vehiculoService = vehiculoService;
    }

    //EN ESTE METODO VAMOS A REGISTRAR UN NUEVO VEHICULO MEDIANTE SU MATRICULA
    @PostMapping
    public Vehiculo registrarVehiculo(@RequestParam String matricula){
        return vehiculoService.registrarVehiculo(matricula);
    }


    //EN ESTE METODO VAMOS A OBTENER TODOS LOS VEHICULOS REGISTRADOS
    @GetMapping 
    public List<Vehiculo> listarVehiculos(){
        return vehiculoService.listaVehiculos();
    }

}


