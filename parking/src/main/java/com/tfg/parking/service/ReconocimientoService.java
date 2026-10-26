package com.tfg.parking.service;

import com.tfg.parking.controller.AccesoController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.MatVector;

import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Size;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import static org.bytedeco.opencv.global.opencv_imgcodecs.IMREAD_COLOR;
import static org.bytedeco.opencv.global.opencv_imgcodecs.imread;
import static org.bytedeco.opencv.global.opencv_imgcodecs.imwrite;
import static org.bytedeco.opencv.global.opencv_imgproc.*;
import org.bytedeco.opencv.opencv_core.Scalar;
import static org.bytedeco.opencv.global.opencv_core.countNonZero;


@Service 
public class ReconocimientoService {

    private final AccesoController accesoController;
    private static final Path CARPETA_IMAGENES = Paths.get("imagenes_paso_a_paso");


    ReconocimientoService(AccesoController accesoController) {
        this.accesoController = accesoController;
    }


    public String procesarImagen(MultipartFile imagen){
        
        if(imagen.isEmpty()){
            return "Imagen inexistente o fallo en el envío de la imagen";
        }

        try{

            Path carpetaProceso = crearCarpetaProcedimiento();
            
            //Cargar la imagen original
            Mat original = cargarImagen(imagen, carpetaProceso);

            //Paso 1: Escala de grises
            Mat gris = convertirAGris(original);
            guardarPaso(carpetaProceso, "01_gris.jpg", gris);

            //Paso 2: Redimensionar
            int anchoProceso = Math.min(800, original.cols());
            Mat redimensionada = redimensionar(gris, anchoProceso);
            guardarPaso(carpetaProceso, "02_redimensionada.jpg", redimensionada);

            //Paso 3: Filtro gausiano
            Mat gaussiana = aplicarFiltroGaussiano(redimensionada);
            guardarPaso(carpetaProceso, "03_gaussiana.jpg", gaussiana);

            //Paso 4: Filtro bilateral
            Mat bilateral = aplicarFiltroBilateral(gaussiana);
            guardarPaso(carpetaProceso, "04_bilateral.jpg", bilateral);

            //Paso 5: Deteccion de bordes
            Mat bordes = detectarBordes(bilateral);
            guardarPaso(carpetaProceso, "05_canny.jpg", bordes);

            //Paso 6: Dilatación

            Mat dilatada = aplicarDilatacion(bordes);
            guardarPaso(carpetaProceso, "06_dilatacion.jpg", dilatada);

            Rect matricula = detectarMatricula(dilatada, redimensionada);

            //Paso 7: Comprobar si hay un rectangulo y poner el rectangulo en una imagen redimensionada original
            if(matricula == null){
                return "No se ha podido capturar la matrícula";
            }

            Mat imagenMatriculaRedimensionada = redimensionar(original, anchoProceso);

            Mat matriculaDetectada = marcarMatricula(imagenMatriculaRedimensionada, matricula);

            guardarPaso(carpetaProceso,"07_matricula_detectada.jpg",matriculaDetectada);

            //Paso 8: Recortar la matricula detectada
            Mat matriculaRecortada = recortarMatricula(imagenMatriculaRedimensionada, matricula);
            guardarPaso(carpetaProceso,"08_matricula_recortada.jpg",matriculaRecortada);

            return "Procesamiento completado: " + carpetaProceso.toAbsolutePath();

        }catch (IOException e){
            return "Error durante el proceso de reconocimiento: " + e.getMessage(); 
        }

    }

    //Metodo para crear una carpeta y guardar el proceso de las imagenes en el reconocimiento de matriculas
    // Usamos fecha y hora como nombre de la carpeta
    private Path crearCarpetaProcedimiento() throws IOException {
        String fecha = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));

        Path carpetaProcedimiento = CARPETA_IMAGENES.resolve("prueba_" + fecha);

        Files.createDirectories(carpetaProcedimiento);

        return carpetaProcedimiento;
    }

    //METODO DE CARGAR IMAGEN: GUARDA IMAGEN EN LA CARPETA DE IMAGENES Y LUEGO LO CARGA A OPEN CV
    //MAT: matriz en OPENCV
    private Mat cargarImagen(MultipartFile imagen, Path carpetaProcedimiento) throws IOException{
        
        Path imagenOriginal = carpetaProcedimiento.resolve("imagenOriginal.jpg");

        Files.write(imagenOriginal, imagen.getBytes());

        Mat original = imread(imagenOriginal.toAbsolutePath().toString(),IMREAD_COLOR);

        if (original.empty()){
            throw new IOException("Fallo en la hora de cargar la imagen");
        }

        return original;
    }

    //Metodo donde recibe una imagen de formato Mat y lo transforma a Escala de Grises

    private Mat convertirAGris(Mat imagen){
        Mat escalaGris = new Mat();
        //Funcion para convertir los colores
        cvtColor(imagen,escalaGris,COLOR_BGR2GRAY);

        return escalaGris;
    }

    //Metodo para redimensionar una imagen, este paso se hace para que OpenCV procese la imagen 
    //más facil, sin eliminar parte de la imagen
    private Mat redimensionar(Mat imagen, int ancho){
        
        //Formula para calular el alto a partir de un ancho dado
        int alto = (int) ((double) imagen.rows() * ancho / imagen.cols());

        Mat redmens = new Mat();

        resize(imagen,redmens,new Size(ancho, alto));

        return redmens;
    }   

    // METODO de filtro Gausiano que se usa:
    // SUAVIZAR LA IMAGEN Y REDUCIR RUIDO ANTES DE HACER LA DETECCIÓN DE
    private Mat aplicarFiltroGaussiano(Mat imagen){
        Mat resultado = new Mat();

        //Imagen de entrada, salida, tamaño de filtro, sigma
        //new Size(5,5) indica la ventana de pixeles que utiliza OpenCV alrededor de cada pixel
        //+ numero, + es el suavizado
        //sigma: sirve para saber cuanto se extiende el suavizado dentro de esa zona, usando 0 opencv calcula uno
        GaussianBlur(imagen, resultado, new Size(5, 5), 0);
        return resultado;
    }
    // Metodo de filtro bilateral para reducir ruido pero manteniendo los bordes
    private Mat aplicarFiltroBilateral(Mat imagen){

        Mat resultado = new Mat();
        // 9: pixeles cercanos que se tiene en cuenta alrededor de cada pixel
        // 75 SigmaColor: Indica cuanto pueden diferenciarse los valores de los pixeles para que se mezclen en el suavizado
        // 75: SigmaSpace: Indica cuanto de alejados pueden estar los pixeles para que se mezclen en el suavizado 
        bilateralFilter(imagen,resultado,9, 75,75);
 
        return resultado;
    }

    // METODO DE DETECTAR BORDES: usamos el algoritmo Canny para detectar bordes de una imagen
    // Los valores 50 y 150:
    // 50 -> umbral bajo: sirve para detectar los bordes más débiles
    // 150 -> umbral alto: si encuentra un cambio grande lo detecta como borde
    private Mat detectarBordes(Mat Imagen){
        Mat bordes = new Mat();
        Canny(Imagen, bordes, 50, 150);

        return bordes;
    }

    // Aplicamos el filtro de dilatacion: con este filtro hacemos para hacer más gruesas las zonas blancas de una imagen de bordes
    private Mat aplicarDilatacion(Mat Imagen){

        Mat resultado = new Mat();
        // El kernel de 3x3 sirve para que cada pixel blanco pueda extenderse aproximadamente 1 pixel arriba, abajo, izquierda y derecha, ademas de las diagonales.
        Mat kernel = getStructuringElement(MORPH_RECT, new Size(3,3));

        dilate(Imagen, resultado, kernel);

        return resultado;
    }

    //Guardar una imagen Mat en una carpeta del disco
    private void guardarPaso(Path carpeta, String nombre, Mat imagen){
        Path ruta = carpeta.resolve(nombre);

        imwrite(ruta.toAbsolutePath().toString(), imagen);
    }

    //Metodo que busca las formas cerradas de una imagen y comprueba cual tiene una forma parecida a una matricula
    //Se queda con el rectangulo mas grande
    private Rect detectarMatricula(Mat imagen, Mat imagenGris){

        // Lugar donde guardar los contornos. MatVector es una coleccion de objetos Mat
        MatVector contornos = new MatVector();

        //Este metodo recorre la imagen y busca los grupos de pixeles que forman una figura
        //RETR_LIST: Indica que contornos queremos poner en el vector, en este caso ponemos todos
        //CHAIN_APPROX_SIMPLE = no se guardan todos los puntos, si no los necesarios para describir su forma
        
        Mat copia = imagen.clone();

        findContours(copia, contornos, RETR_LIST, CHAIN_APPROX_SIMPLE);

        
        //Variables para el mejor candidato
        Rect mejorCandidato = null;

        //Variable para la puntuacion mas alta
        double mejorPuntuacion = -1;

        //Area de la imagen y su proporcion ideal
        double areaImagen = (double) imagen.cols() * imagen.rows();
        double proporcionIdeal = 4.7; //Ancho / alto

        //Recorremos todos los contornos de la coleccion
        for (long i = 0; i < contornos.size(); i++){

            //Contorno del indice actual
            Mat contorno = contornos.get(i);
            //Calcula el rectangulo mas pequeño que contiene todos los puntos del contorno
            Rect rectangulo = boundingRect(contorno);

            //Dimensiones del rectangulo
            int ancho = rectangulo.width(); //anchura
            int alto = rectangulo.height(); //altura

            //Se descarta objetos pequeños
            if(ancho < 45 || alto < 10){
                continue;
            }

            // Proporcion entre ancho y alto. Y tambien calculamos el area

            double proporcion = (double) ancho/alto;
            double areaRectangulo = (double) ancho * alto;

            //Filtro por proporción. Comprobamos que los rectangulos tengan una proporcion aproximada a ello
            if(proporcion < 3.0 || proporcion > 5.5){
                continue;
            }

            // Area minima permitida, area minima para poder contar matriculas y asi evitar superficies pequeñas
            double areaMinima = areaImagen * 0.0008;

            if (areaRectangulo < areaMinima){
                continue;
            }
            
            // Calcular rectangularidad
            double areaContorno = contourArea(contorno); //Calcula el area geometrica encerrada por el contorno

            //Calculamos el area del contorno y el area del rectangulo envolvente
            double rectangularidad = areaContorno / areaRectangulo;

            //Entorno que ocupa menos del 65% del area del rectangulo, se ignora y pasa al siguiente contorno
            if(rectangularidad < 0.65){
                continue;
            }

            //Extraer la región correspondiente en la imagen gris redimensionada
            Mat regionGris = new Mat(imagenGris, rectangulo);
            //Creamos una mascara para los pixeles claros
            //Creamos una matriz vacia donde guardaremos el resultado al aplicar un umbral de luminosidad
            Mat mascaraClaros = new Mat();


            //Aqui aplicamos el umbral de luminosidad
            threshold(regionGris,mascaraClaros,155,255,THRESH_BINARY);
            //155: Umbral de luminosidad
            //255: Valor asignado que los asignen a los pixeles que superen el umbral
            //TRESH_BINARY: Tipo de umbralizacion binaria

            //Este filtro lo que hace es comparar cada pixel con el valor 155, si el pixel es superior a 155 lo convierte en blanco
            //y si es igual o inferior lo convierte en negro


            //Calculo del porcentaje de pixeles claros
            //- countNonZero: pixeles que tienen valor distinto de 0, o sea solo los blancos
            double porcentajeClaro =(double) countNonZero(mascaraClaros) / areaRectangulo;
            //Liberamos los recursos asociados a esto , ya que ya lo hemos usado
            mascaraClaros.close();
            regionGris.close();


            //Puntuacion segun la proporcion: va de 0 a 1 
            //Primero calculamos la diferencia de nuestra prporcion respecto a 4.7 , luego se pasa a valor absoluto
            //Se divide entre 1.7, y cogemos el minimo entre el resultado aquel y 1.0
            // Y a 1 restamos lo obtenido y obtenemos la puntuacion

            //1.7 es un valor que se ha escogido para ver como desciende la puntuacion de cuando la proporcion mas
            // se aleja de 4.7
            double puntuacionProporcion =1.0 - Math.min(1.0,Math.abs(proporcion - proporcionIdeal) / 1.7);

            //Puntuacion segun el area
            //Calculamos el 3% del area de la imagen
            //Comparamos el valor del rectangulo con ese 3%
            //Limitamos a puntuacion maxima de 1

            double puntuacionTamano = Math.min(1.0, areaRectangulo / (areaImagen * 0.03));

            //Puntuacion final

            double puntuacion = 0.35 * puntuacionProporcion + 0.25 * rectangularidad +0.35* porcentajeClaro + 0.05 * puntuacionTamano;

            if (puntuacion > mejorPuntuacion){

                mejorPuntuacion = puntuacion;
                mejorCandidato = rectangulo;
            }
        }
        copia.close();
        return mejorCandidato;
    }

    //Metodo donde recibe una imagen y un Rect que indica donde esta la matricula, dibuja ese rectangulo en esa imagen clonada
    private Mat marcarMatricula(Mat imagen, Rect matricula){
        //Copia de la imagen pasada como argumento
        Mat resultado = imagen.clone();

        //new Scalar = color del triangulo en BGR de tres canales. 
        //2: grosor de 2 pixeles
        //LINE_8 = tipo de línea estándar de OpenCV para dibujar el rectángulo
        //0: usar las coordenadas como enteros normales, sin parte decimal
        rectangle(resultado,matricula, new Scalar(255, 0, 0, 0),2,LINE_8,0);

        return  resultado;
    }

    //Metodo para recortar solamente la zona donde esta la matricula
    private Mat recortarMatricula(Mat imagen, Rect matricula){  
        return new Mat(imagen, matricula);
    }
}
