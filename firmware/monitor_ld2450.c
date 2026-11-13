/*
 * Monitor de radar HLK-LD2450 - C + raylib (Ubuntu/Linux)
 *
 * Compatible con el firmware ESP32-C6 que envia por USB (115200 baudios):
 *    INICIO
 *    OBJ,1,1200,2400,15
 *    OBJ,2,-500,3100,-7
 *    FIN
 *
 * Las coordenadas X/Y estan en mm; la velocidad esta en cm/s.
 * Este programa NO recibe directamente las tramas binarias del radar:
 * recibe el texto que ya produce el ESP32.
 *
 * Uso:
 *    ./build/monitor_ld2450_c /dev/ttyACM0
 *    ./build/monitor_ld2450_c --demo
 *
 * F11: pantalla completa | ESC: salir
 */

#define _DEFAULT_SOURCE
#include "raylib.h"

#include <errno.h>
#include <fcntl.h>
#include <math.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <termios.h>
#include <unistd.h>

#define N_OBJ 3
#define MAX_LINEA 256
#define PI_F 3.14159265358979323846f
#define BAUDRATE B115200
#define TIEMPO_CADUCIDAD 1.5

#define COLOR_FONDO    (Color){  9,  15,  26, 255}
#define COLOR_PANEL    (Color){ 16,  26,  42, 255}
#define COLOR_RECUADRO (Color){ 22,  36,  56, 255}
#define COLOR_TEXTO    (Color){221, 233, 246, 255}
#define COLOR_SUAVE    (Color){137, 160, 188, 255}
#define COLOR_REJILLA  (Color){ 42,  70,  91, 255}
#define COLOR_VERDE    (Color){ 89, 240, 174, 255}

static const Color COLORES_OBJ[N_OBJ] = {
    { 61, 207, 255, 255 },
    {255, 182,  65, 255 },
    {220, 118, 255, 255 }
};

typedef struct {
    bool activo;
    int x_mm;
    int y_mm;
    int velocidad_cms;
} Objetivo;

typedef struct {
    Objetivo actual[N_OBJ];
    Objetivo nuevo[N_OBJ];
    bool leyendo_trama;

    unsigned long total_tramas;
    unsigned long total_bytes;
    unsigned int tramas_ventana;
    float tramas_por_segundo;
    double inicio_ventana;
    double ultima_trama;
    double ultimo_intento;
    bool conectado;

    char linea[MAX_LINEA];
    size_t longitud_linea;
} EstadoRadar;

static int abrir_puerto(const char *nombre)
{
    int fd = open(nombre, O_RDWR | O_NOCTTY | O_NONBLOCK);
    if (fd < 0) return -1;

    struct termios opciones;
    if (tcgetattr(fd, &opciones) != 0) {
        close(fd);
        return -1;
    }

    cfmakeraw(&opciones);
    cfsetispeed(&opciones, BAUDRATE);
    cfsetospeed(&opciones, BAUDRATE);
    opciones.c_cflag |= (CLOCAL | CREAD);
    opciones.c_cflag &= ~CRTSCTS;
    opciones.c_cflag &= ~CSTOPB;
    opciones.c_cflag &= ~PARENB;
    opciones.c_cflag &= ~CSIZE;
    opciones.c_cflag |= CS8;
    opciones.c_cc[VMIN] = 0;
    opciones.c_cc[VTIME] = 0;

    if (tcsetattr(fd, TCSANOW, &opciones) != 0) {
        close(fd);
        return -1;
    }

    tcflush(fd, TCIFLUSH);
    return fd;
}

static void procesar_linea(EstadoRadar *radar, const char *linea, double ahora)
{
    if (strcmp(linea, "INICIO") == 0) {
        memset(radar->nuevo, 0, sizeof(radar->nuevo));
        radar->leyendo_trama = true;
        return;
    }

    if (strncmp(linea, "OBJ,", 4) == 0 && radar->leyendo_trama) {
        int id, x, y, vel;
        if (sscanf(linea, "OBJ,%d,%d,%d,%d", &id, &x, &y, &vel) == 4 &&
            id >= 1 && id <= N_OBJ) {
            radar->nuevo[id - 1] = (Objetivo){
                .activo = true,
                .x_mm = x,
                .y_mm = y,
                .velocidad_cms = vel
            };
        }
        return;
    }

    if (strcmp(linea, "FIN") == 0 && radar->leyendo_trama) {
        /* Publicar una trama completa, sustituyendo la anterior.
           Asi no hay una cola de posiciones antiguas para dibujar. */
        memcpy(radar->actual, radar->nuevo, sizeof(radar->actual));
        radar->leyendo_trama = false;
        radar->ultima_trama = ahora;
        radar->total_tramas++;
        radar->tramas_ventana++;
    }
}

static void recibir_byte(EstadoRadar *radar, unsigned char byte, double ahora)
{
    if (byte == '\n' || byte == '\r') {
        if (radar->longitud_linea > 0) {
            radar->linea[radar->longitud_linea] = '\0';
            procesar_linea(radar, radar->linea, ahora);
            radar->longitud_linea = 0;
        }
        return;
    }

    if (radar->longitud_linea < MAX_LINEA - 1) {
        radar->linea[radar->longitud_linea++] = (char)byte;
    } else {
        /* Evitar que una linea corrupta desborde el buffer. */
        radar->longitud_linea = 0;
    }
}

static void leer_serie(int *fd, EstadoRadar *radar, const char *puerto, double ahora)
{
    if (*fd < 0) {
        if (ahora - radar->ultimo_intento >= 1.5) {
            radar->ultimo_intento = ahora;
            *fd = abrir_puerto(puerto);
            radar->conectado = (*fd >= 0);
            if (*fd >= 0) {
                radar->longitud_linea = 0;
                radar->leyendo_trama = false;
                printf("Conectado: %s (115200 baudios)\n", puerto);
            }
        }
        return;
    }

    unsigned char bloque[4096];
    for (;;) {
        ssize_t n = read(*fd, bloque, sizeof(bloque));
        if (n > 0) {
            radar->total_bytes += (unsigned long)n;
            double instante = GetTime();
            for (ssize_t i = 0; i < n; i++)
                recibir_byte(radar, bloque[i], instante);
            /* Seguir hasta vaciar lo pendiente en el buffer serie. */
        } else if (n == 0) {
            /* Un puerto no bloqueante puede devolver 0 sin desconectarse. */
            break;
        } else if (errno == EAGAIN || errno == EWOULDBLOCK) {
            break;
        } else if (errno == EINTR) {
            continue;
        } else {
            fprintf(stderr, "Error leyendo %s: %s\n", puerto, strerror(errno));
            close(*fd);
            *fd = -1;
            radar->conectado = false;
            radar->ultimo_intento = ahora;
            radar->leyendo_trama = false;
            radar->longitud_linea = 0;
            break;
        }
    }
}

static void datos_demostracion(EstadoRadar *radar, double ahora)
{
    static double ultimo_ejemplo = -1;
    if (ultimo_ejemplo >= 0 && ahora - ultimo_ejemplo < 0.10) return;
    ultimo_ejemplo = ahora;

    memset(radar->actual, 0, sizeof(radar->actual));
    radar->actual[0] = (Objetivo){true,
        (int)(1500.0 * sin(ahora * 0.7)),
        (int)(3000.0 + 1600.0 * cos(ahora * 0.6)), 25};
    radar->actual[1] = (Objetivo){true,
        (int)(-2300.0 + 450.0 * sin(ahora * 0.9)),
        (int)(2200.0 + 800.0 * sin(ahora * 0.45)), -12};
    radar->ultima_trama = ahora;
    radar->total_tramas++;
    radar->tramas_ventana++;
    radar->conectado = true;
}

static void dibujar_etiqueta(const char *texto, int x, int y, int tam, Color color)
{
    DrawText(texto, x, y, tam, color);
}

static void dibujar_grafica(Rectangle area, const EstadoRadar *radar, bool fresco)
{
    DrawRectangleRounded(area, 0.025f, 8, COLOR_PANEL);
    DrawRectangleLinesEx(area, 1.0f, (Color){31, 53, 77, 255});

    float escala = fminf((area.width - 94.0f) / 8.0f,
                         (area.height - 94.0f) / 6.0f);
    float centro_x = area.x + area.width / 2.0f;
    float origen_y = area.y + area.height - 47.0f;

    BeginScissorMode((int)area.x + 1, (int)area.y + 1,
                     (int)area.width - 2, (int)area.height - 2);

    for (int x = -4; x <= 4; x++) {
        float px = centro_x + x * escala;
        DrawLineEx((Vector2){px, origen_y},
                   (Vector2){px, origen_y - 6.0f * escala},
                   x == 0 ? 2.0f : 1.0f,
                   x == 0 ? (Color){69, 122, 148, 255} : COLOR_REJILLA);
    }
    for (int y = 0; y <= 6; y++) {
        float py = origen_y - y * escala;
        DrawLineEx((Vector2){centro_x - 4 * escala, py},
                   (Vector2){centro_x + 4 * escala, py},
                   y == 0 ? 2.0f : 1.0f,
                   y == 0 ? (Color){69, 122, 148, 255} : COLOR_REJILLA);
    }

    for (int r = 1; r <= 6; r++) {
        DrawCircleLines((int)centro_x, (int)origen_y,
                        r * escala, (Color){34, 90, 93, 125});
    }

    for (int grados = -60; grados <= 60; grados += 30) {
        float a = grados * PI_F / 180.0f;
        Vector2 fin = {centro_x + 6.0f * escala * sinf(a),
                       origen_y - 6.0f * escala * cosf(a)};
        DrawLineEx((Vector2){centro_x, origen_y}, fin, 1.0f,
                   (Color){40, 100, 103, 130});
    }

    DrawCircleV((Vector2){centro_x, origen_y}, 9, COLOR_VERDE);
    DrawCircleLines((int)centro_x, (int)origen_y, 15, COLOR_VERDE);

    if (fresco) {
        for (int i = 0; i < N_OBJ; i++) {
            Objetivo objetivo = radar->actual[i];
            if (!objetivo.activo) continue;
            float x = objetivo.x_mm / 1000.0f;
            float y = objetivo.y_mm / 1000.0f;
            if (x < -4 || x > 4 || y < 0 || y > 6) continue;

            Vector2 punto = {centro_x + x * escala,
                             origen_y - y * escala};
            Color c = COLORES_OBJ[i];
            DrawLineEx((Vector2){centro_x, origen_y}, punto, 2.0f,
                       (Color){c.r, c.g, c.b, 110});
            DrawCircleV(punto, 19, (Color){c.r, c.g, c.b, 35});
            DrawCircleLines((int)punto.x, (int)punto.y, 14, c);
            DrawCircleV(punto, 7, c);
            dibujar_etiqueta(TextFormat("O%d", i + 1),
                             (int)punto.x + 17, (int)punto.y - 24, 19, c);
            dibujar_etiqueta(TextFormat("%.2f m",
                hypotf(x, y)), (int)punto.x + 17, (int)punto.y - 1, 16, COLOR_TEXTO);
        }
    }
    EndScissorMode();

    for (int x = -4; x <= 4; x += 2) {
        dibujar_etiqueta(TextFormat("%+d m", x),
                         (int)(centro_x + x * escala - 22),
                         (int)(origen_y + 12), 15, COLOR_SUAVE);
    }
    for (int y = 1; y <= 6; y++) {
        dibujar_etiqueta(TextFormat("%d m", y),
                         (int)(centro_x - 4 * escala - 38),
                         (int)(origen_y - y * escala - 10), 15, COLOR_SUAVE);
    }

    dibujar_etiqueta("FRENTE (Y+)", (int)(centro_x - 62),
                     (int)area.y + 14, 18, COLOR_SUAVE);
    dibujar_etiqueta("RADAR", (int)centro_x - 29,
                     (int)(origen_y - 44), 15, COLOR_VERDE);
}

static void dibujar_tarjeta_objetivo(int i, Rectangle rect,
                                    const Objetivo *obj, bool fresco)
{
    Color c = COLORES_OBJ[i];
    DrawRectangleRounded(rect, 0.07f, 6, COLOR_RECUADRO);
    DrawRectangleRounded((Rectangle){rect.x + 10, rect.y + 12, 5, 23},
                         0.3f, 4, c);
    dibujar_etiqueta(TextFormat("OBJETIVO %d", i + 1),
                     (int)rect.x + 26, (int)rect.y + 13, 20, c);

    if (!fresco || !obj->activo) {
        dibujar_etiqueta("Sin deteccion", (int)rect.x + 19,
                         (int)rect.y + 56, 18, COLOR_SUAVE);
        return;
    }

    float x = obj->x_mm / 1000.0f;
    float y = obj->y_mm / 1000.0f;
    float distancia = hypotf(x, y);
    float angulo = atan2f(x, y) * 180.0f / PI_F;

    dibujar_etiqueta(TextFormat("X: %+.2f m    Y: %.2f m", x, y),
                     (int)rect.x + 18, (int)rect.y + 47, 17, COLOR_TEXTO);
    dibujar_etiqueta(TextFormat("Distancia: %.2f m   Ang: %+.1f", distancia, angulo),
                     (int)rect.x + 18, (int)rect.y + 75, 16, COLOR_TEXTO);
    dibujar_etiqueta(TextFormat("Velocidad: %+.2f m/s", obj->velocidad_cms / 100.0f),
                     (int)rect.x + 18, (int)rect.y + 101, 16, COLOR_TEXTO);
}

static void dibujar_interfaz(const EstadoRadar *radar, const char *puerto,
                             bool demostracion)
{
    int w = GetScreenWidth();
    int h = GetScreenHeight();
    double ahora = GetTime();
    bool fresco = radar->ultima_trama > 0 &&
                  (ahora - radar->ultima_trama) < TIEMPO_CADUCIDAD;

    BeginDrawing();
    ClearBackground(COLOR_FONDO);

    dibujar_etiqueta("LD2450  |  MONITOR EN C", 28, 23, 30, COLOR_TEXTO);
    dibujar_etiqueta("ESP32-C6  -  raylib  -  sin suavizado", 30, 61, 17, COLOR_SUAVE);

    Color indicador = demostracion ? COLORES_OBJ[1] :
                      (radar->conectado && fresco ? COLOR_VERDE :
                       (Color){255, 111, 111, 255});
    DrawCircle(w - 355, 39, 7, indicador);
    dibujar_etiqueta(demostracion ? "MODO DEMO" :
                      (radar->conectado ? "PUERTO ABIERTO" : "DESCONECTADO"),
                      w - 339, 29, 18, indicador);

    Rectangle area = {25, 108, (float)w - 391, (float)h - 166};
    if (area.width < 250) area.width = 250;
    if (area.height < 280) area.height = 280;
    dibujar_grafica(area, radar, fresco);

    float lateral_x = (float)w - 350;
    Rectangle panel = {lateral_x, 108, 325, 132};
    DrawRectangleRounded(panel, 0.07f, 6, COLOR_PANEL);
    dibujar_etiqueta("ESTADO DEL SISTEMA", (int)lateral_x + 16,
                     121, 19, COLOR_TEXTO);
    dibujar_etiqueta(TextFormat("Pantalla: %d FPS", GetFPS()),
                     (int)lateral_x + 16, 151, 17, COLOR_SUAVE);
    dibujar_etiqueta(TextFormat("Radar: %.1f tramas/s", radar->tramas_por_segundo),
                     (int)lateral_x + 16, 176, 17, COLOR_SUAVE);
    if (fresco) {
        dibujar_etiqueta(TextFormat("Ultimo dato: %.0f ms", 1000.0 *
                          (ahora - radar->ultima_trama)),
                          (int)lateral_x + 16, 202, 17, COLOR_VERDE);
    } else {
        dibujar_etiqueta("Sin datos recientes", (int)lateral_x + 16,
                         202, 17, (Color){255, 132, 132, 255});
    }

    for (int i = 0; i < N_OBJ; i++) {
        dibujar_tarjeta_objetivo(i,
            (Rectangle){lateral_x, 251 + i * 142.0f, 325, 132},
            &radar->actual[i], fresco);
    }

    dibujar_etiqueta(demostracion ? "SIMULACION (sin radar real)" : puerto,
                     29, h - 41, 16, COLOR_SUAVE);
    dibujar_etiqueta(TextFormat("Tramas recibidas: %lu", radar->total_tramas),
                     (int)(w * 0.43f), h - 41, 16, COLOR_SUAVE);
    dibujar_etiqueta("F11: pantalla completa  |  ESC: salir", w - 385,
                     h - 41, 16, COLOR_SUAVE);

    EndDrawing();
}

int main(int argc, char **argv)
{
    const char *puerto = (argc > 1) ? argv[1] : "/dev/ttyACM0";
    bool demostracion = strcmp(puerto, "--demo") == 0;

    EstadoRadar radar = {0};
    int fd = -1;

    SetConfigFlags(FLAG_WINDOW_RESIZABLE | FLAG_MSAA_4X_HINT);
    InitWindow(1366, 768, "Radar HLK-LD2450 - C + raylib");
    MaximizeWindow();
    SetTargetFPS(120);
    SetExitKey(KEY_NULL);
    radar.inicio_ventana = GetTime();
    radar.ultimo_intento = -10.0;

    printf("=== MONITOR LD2450 EN C ===\n");
    if (demostracion) printf("Modo demostracion (datos simulados)\n");
    else printf("Intentando conectar a %s a 115200 baudios...\n", puerto);
    printf("F11 = pantalla completa; ESC = cerrar.\n");

    while (!WindowShouldClose()) {
        if (IsKeyPressed(KEY_ESCAPE)) break;
        if (IsKeyPressed(KEY_F11)) ToggleFullscreen();

        double ahora = GetTime();
        if (demostracion) datos_demostracion(&radar, ahora);
        else leer_serie(&fd, &radar, puerto, ahora);

        double intervalo = ahora - radar.inicio_ventana;
        if (intervalo >= 1.0) {
            radar.tramas_por_segundo = (float)(radar.tramas_ventana / intervalo);
            radar.tramas_ventana = 0;
            radar.inicio_ventana = ahora;
        }

        dibujar_interfaz(&radar, puerto, demostracion);
    }

    if (fd >= 0) close(fd);
    CloseWindow();
    return 0;
}
