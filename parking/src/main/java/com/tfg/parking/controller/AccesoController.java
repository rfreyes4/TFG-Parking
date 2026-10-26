package com.tfg.parking.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tfg.parking.model.Acceso;
import com.tfg.parking.service.AccesoService;


@RestController 
@RequestMapping("/api/accesos")
public class AccesoController {

    private final AccesoService accesoService;
    
    public AccesoController(AccesoService accesoService){
        this.accesoService = accesoService;
    }

    //REGISTRAR UN INTENTO DE ACCESO
    @PostMapping
    public Acceso registrarAcceso(@RequestParam String matricula){
        return accesoService.registrarAcceso(matricula);
    }

    @GetMapping
    public List<Acceso> listaAccesos(){
        return accesoService.obtenerAccesos();
    }
}
