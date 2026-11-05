package com.tfg.parking.service;

import java.awt.image.BufferedImage;
import java.util.Locale;

import org.bytedeco.opencv.opencv_core.Mat;
import org.springframework.stereotype.Service;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;


import static org.bytedeco.opencv.global.opencv_imgproc.*;


@Service 
public class OcrService {

    public String leerMatricula(Mat matriculaRecortada)throws TesseractException{

        //1.- Convertir la matricula a una escala de grises
        Mat gris = new Mat();

        try{
            cvtColor(matriculaRecortada, gris, COLOR_BGR2GRAY);

            //2.- Convertir Mat a BufferedImage

            //Obteniendo las dimensiones de la imagen
            int ancho = gris.cols();
            int alto = gris.rows();

            //Creacion de array para almacenar los valores de los pixeles
            //Cada pixel de nuestra imagen en escala de grises ocupa 8bits = 1 byte
            byte[] pixeles = new byte[ancho * alto];

            //Copiamos los pixeles desde Mat de OpenCV al array recien creado
            gris.data().get(pixeles);

            // BufferedImage : objeto de java que representa una imagen almacenada en memoria RAM
            //Crea una imagen en memoria con dichas dimensiones e indicamos que la imagen es de escala de grises
            BufferedImage imagenOCR = new BufferedImage(ancho, alto,BufferedImage.TYPE_BYTE_GRAY);

            //setDataElements: copia los valores del array a la imagen
            //Copiamos los pixeles a esa nueva imagen
            //0,0 es indicar que comienza a copiar los pixeles en la posicion 0,0
            //ancho y alto: numero de pixeles que copiamos horizontalmente y verticalmente
            //pixeles: array que contiene los valores de los pixeles

            imagenOCR.getRaster().setDataElements(0, 0, ancho,alto, pixeles);

            //Configurar Tessereact
            //Crear un objeto del motor de OCR
            Tesseract tesseract = new Tesseract();
            //Ubicacion de Modelos previamente entrenados
            tesseract.setDatapath("/usr/share/tesseract-ocr/5/tessdata");
            //Utilizamos el modelo de reconocimiento en Ingles. Solo caracteres, no hay que interpretar palabras en ingles
            tesseract.setLanguage("eng");

            //Indicacion a Tessereact que la imagen contiene una unica linea de texto
            tesseract.setPageSegMode(7);

            //Lista de caracteres para reconocer para Tessereact
            tesseract.setVariable("tessedit_char_whitelist",
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789");

            //4.- Reconocer los caracteres
            String texto = tesseract.doOCR(imagenOCR);

            //5.- Limpiar los resultados
            return texto.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        }finally{
            gris.close();
        }


    }


}
