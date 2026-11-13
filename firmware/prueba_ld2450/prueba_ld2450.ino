#include <Arduino.h>
#include <string.h>

// =====================================================
// CONFIGURACION DEL RADAR HLK-LD2450
// ESP32-C6 DEV MODULE
// =====================================================

// Pines ESP32-C6
// TX del radar -> GPIO20 (RX del ESP32-C6)
// RX del radar -> GPIO21 (TX del ESP32-C6)

const int RADAR_RX = 20;
const int RADAR_TX = 21;

// Velocidades
const uint32_t BAUD_USB = 115200;
const uint32_t BAUD_RADAR = 256000;

// Protocolo LD2450
const size_t TAM_TRAMA = 30;
const int MAX_OBJETIVOS = 3;

// Buffer
uint8_t trama[TAM_TRAMA];

size_t bytesRecibidos = 0;

// Estadisticas
uint32_t tramasValidas = 0;
uint32_t tramasIncorrectas = 0;


// =====================================================
// COMPROBAR CABECERA
// =====================================================

bool cabeceraCorrecta() {

    return trama[0] == 0xAA &&
           trama[1] == 0xFF &&
           trama[2] == 0x03 &&
           trama[3] == 0x00;
}


// =====================================================
// CONVERTIR DOS BYTES A NUMERO CON SIGNO
// =====================================================

int16_t leerValor(size_t posicion) {

    uint16_t bruto =
        (uint16_t)trama[posicion] |
        ((uint16_t)trama[posicion + 1] << 8);

    int16_t magnitud =
        (int16_t)(bruto & 0x7FFF);

    return (bruto & 0x8000)
        ? magnitud
        : -magnitud;
}


// =====================================================
// COMPROBAR SI EXISTE UN OBJETIVO
// =====================================================

bool objetivoPresente(size_t posicion) {

    // No filtramos por distancia ni velocidad.
    // Solo ignoramos bloques completamente vacios.

    for (size_t i = 0; i < 8; i++) {

        if (trama[posicion + i] != 0) {
            return true;
        }
    }

    return false;
}


// =====================================================
// ENVIAR OBJETIVOS AL ORDENADOR
// =====================================================

void enviarObjetivos() {

    Serial.println("INICIO");

    for (int i = 0; i < MAX_OBJETIVOS; i++) {

        size_t posicion = 4 + i * 8;

        if (!objetivoPresente(posicion)) {
            continue;
        }

        // Coordenadas en milimetros
        int16_t x = leerValor(posicion);
        int16_t y = leerValor(posicion + 2);

        // Velocidad en centimetros por segundo
        int16_t velocidad = leerValor(posicion + 4);

        // Enviar datos al ordenador
        Serial.printf(
            "OBJ,%d,%d,%d,%d\n",
            i + 1,
            x,
            y,
            velocidad
        );
    }

    Serial.println("FIN");
}


// =====================================================
// DESCARTAR PRIMER BYTE
// =====================================================

void descartarPrimerByte() {

    if (bytesRecibidos == 0) {
        return;
    }

    bytesRecibidos--;

    if (bytesRecibidos > 0) {

        memmove(
            trama,
            trama + 1,
            bytesRecibidos
        );
    }
}


// =====================================================
// PROCESAR LOS DATOS DEL RADAR
// =====================================================

void procesarByte(uint8_t dato) {

    if (bytesRecibidos >= TAM_TRAMA) {
        bytesRecibidos = 0;
    }

    trama[bytesRecibidos++] = dato;

    // Buscar cabecera correcta
    while (
        bytesRecibidos >= 4 &&
        !cabeceraCorrecta()
    ) {

        descartarPrimerByte();
    }

    // Comprobar si tenemos una trama completa
    if (bytesRecibidos == TAM_TRAMA) {

        bool finCorrecto =
            trama[28] == 0x55 &&
            trama[29] == 0xCC;

        if (finCorrecto) {

            tramasValidas++;

            enviarObjetivos();

            bytesRecibidos = 0;

        } else {

            tramasIncorrectas++;

            descartarPrimerByte();

            while (
                bytesRecibidos >= 4 &&
                !cabeceraCorrecta()
            ) {

                descartarPrimerByte();
            }
        }
    }
}


// =====================================================
// INICIALIZAR ESP32-C6 DEV MODULE
// =====================================================

void setup() {

    // Comunicacion USB con el ordenador
    Serial.begin(BAUD_USB);

    delay(1000);

    // Buffer para recibir datos del radar
    Serial1.setRxBufferSize(2048);

    // UART1 para comunicar con HLK-LD2450
    Serial1.begin(
        BAUD_RADAR,
        SERIAL_8N1,
        RADAR_RX,
        RADAR_TX
    );

    Serial.println("================================");
    Serial.println("ESP32-C6 INICIADO");
    Serial.println("RADAR HLK-LD2450 LISTO");
    Serial.println("================================");

    Serial.println("Configuracion:");
    Serial.println("RX: GPIO20");
    Serial.println("TX: GPIO21");
    Serial.println("Radar: 256000 baudios");
    Serial.println("USB: 115200 baudios");

    Serial.println("================================");
}


// =====================================================
// BUCLE PRINCIPAL
// =====================================================

void loop() {

    // Recibir todos los bytes disponibles del radar
    while (Serial1.available() > 0) {

        int dato = Serial1.read();

        if (dato >= 0) {
            procesarByte((uint8_t)dato);
        }
    }

    yield();
}