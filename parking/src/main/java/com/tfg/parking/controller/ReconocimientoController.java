package com.tfg.parking.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.tfg.parking.service.ReconocimientoService;


@RestController
@RequestMapping("/api/reconocimiento")
public class ReconocimientoController {

    private final ReconocimientoService reconocimientoService;

    public ReconocimientoController (ReconocimientoService reconocimientoService){
        this.reconocimientoService = reconocimientoService;
    }


    @PostMapping
    public String recibirImagen(@RequestParam MultipartFile imagen){
        return reconocimientoService.procesarImagen(imagen);
    }

}
