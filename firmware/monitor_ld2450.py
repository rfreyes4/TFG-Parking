#!/usr/bin/env python3
"""Visor en tiempo real de HLK-LD2450 + ESP32-C6 (Arduino por USB).

Uso:
    python monitor_ld2450_mejorado.py --port /dev/ttyACM0
    python monitor_ld2450_mejorado.py --demo  # Solo probar la interfaz

F11: pantalla completa | Esc: salir de pantalla completa.
"""

import argparse
from collections import deque
import math
import queue
import threading
import time
import tkinter as tk

try:
    import serial
except ImportError:
    raise SystemExit("Instala pyserial: pip install pyserial")

FONDO = "#0b1220"
PANEL = "#111d2e"
REJILLA = "#24364b"
TEXTO = "#e9f4ff"
SUAVE = "#9eb3ca"
VERDE = "#34e7a0"
COLORES = {1: "#4bd6ff", 2: "#ffcc66", 3: "#cf9aff"}


class VisorRadar:
    def __init__(self, root, puerto, baudios, demo):
        self.root = root
        self.puerto = puerto
        self.baudios = baudios
        self.demo = demo
        self.parar = threading.Event()
        self.cola = queue.Queue(maxsize=120)
        self.objetivos = {}
        self.trayectorias = {i: deque(maxlen=25) for i in range(1, 4)}
        self.instantes = deque(maxlen=50)
        self.ultima_trama = 0.0
        self.total_tramas = 0
        self.pantalla_completa = False
        self.inicio_demo = time.monotonic()

        root.title("TFG Parking | Radar HLK-LD2450")
        root.configure(bg=FONDO)
        root.geometry(f"{root.winfo_screenwidth()}x{root.winfo_screenheight() - 65}+0+0")
        root.minsize(900, 620)
        try:
            root.attributes("-zoomed", True)  # Ventana maximizada en Linux
        except tk.TclError:
            pass
        root.bind("<F11>", self.alternar_pantalla_completa)
        root.bind("<Escape>", self.salir_pantalla_completa)
        root.protocol("WM_DELETE_WINDOW", self.cerrar)

        encabezado = tk.Frame(root, bg=FONDO)
        encabezado.pack(fill="x", padx=22, pady=(16, 8))
        tk.Label(encabezado, text="RADAR HLK-LD2450  ·  ESP32-C6",
                 font=("DejaVu Sans", 21, "bold"), bg=FONDO, fg=TEXTO
                 ).pack(anchor="w")
        self.estado = tk.StringVar(value="Iniciando conexión...")
        tk.Label(encabezado, textvariable=self.estado, bg=FONDO, fg=SUAVE,
                 font=("DejaVu Sans", 11)).pack(anchor="w", pady=(5, 0))

        cuerpo = tk.Frame(root, bg=FONDO)
        cuerpo.pack(fill="both", expand=True, padx=18, pady=(0, 16))

        self.canvas = tk.Canvas(cuerpo, bg=PANEL, highlightthickness=0)
        self.canvas.pack(side="left", fill="both", expand=True, padx=(0, 14))
        self.canvas.bind("<Configure>", lambda _e: self.dibujar())

        derecha = tk.Frame(cuerpo, bg=FONDO, width=355)
        derecha.pack(side="right", fill="y")
        derecha.pack_propagate(False)

        self.resumen = tk.StringVar(value="Esperando datos del radar...")
        self.tarjeta(derecha, "ESTADO DEL RADAR", self.resumen, VERDE)

        self.etiquetas = {}
        for i in range(1, 4):
            self.etiquetas[i] = tk.StringVar(value="Sin objetivos detectados")
            self.tarjeta(derecha, f"OBJETIVO {i}", self.etiquetas[i], COLORES[i])

        tk.Label(derecha, bg=FONDO, fg=SUAVE, justify="left",
                 font=("DejaVu Sans", 10),
                 text="ZONA DE ACCESO\n±1,5 m de ancho lateral\n0,5 a 4 m de distancia\n\n"
                      "F11: pantalla completa\nEsc: salir de pantalla completa\n\n"
                      "El radar detecta objetivos,\nno identifica vehículos.").pack(
                         anchor="w", padx=13, pady=10)

        if demo:
            self.estado.set("MODO DEMOSTRACIÓN  ·  datos simulados")
            self.root.after(100, self.datos_demo)
        else:
            threading.Thread(target=self.leer_serial, daemon=True).start()

        self.root.after(40, self.actualizar)

    def tarjeta(self, padre, titulo, contenido, color):
        caja = tk.Frame(padre, bg=PANEL, padx=15, pady=12)
        caja.pack(fill="x", pady=(0, 11))
        tk.Label(caja, text=titulo, bg=PANEL, fg=color,
                 font=("DejaVu Sans", 12, "bold")).pack(anchor="w")
        tk.Label(caja, textvariable=contenido, bg=PANEL, fg=TEXTO,
                 justify="left", anchor="w", wraplength=320,
                 font=("DejaVu Sans", 11)).pack(anchor="w", pady=(9, 0))

    def publicar(self, evento):
        try:
            self.cola.put_nowait(evento)
        except queue.Full:
            try:
                self.cola.get_nowait()  # Prima la información más reciente
                self.cola.put_nowait(evento)
            except queue.Empty:
                pass

    def leer_serial(self):
        """Lectura UART/USB en un hilo; reconexión automática ante errores."""
        while not self.parar.is_set():
            try:
                with serial.Serial(self.puerto, self.baudios,
                                   timeout=0.3, exclusive=True) as conexion:
                    self.publicar(("estado", f"CONECTADO  ·  {self.puerto}  ·  USB CDC"))
                    bloque = {}
                    while not self.parar.is_set():
                        linea = conexion.readline().decode("utf-8", errors="replace").strip()
                        if linea == "INICIO":
                            bloque = {}
                        elif linea.startswith("OBJ,"):
                            partes = linea.split(",")
                            try:
                                if len(partes) == 5:
                                    num, x, y, v = map(int, partes[1:])
                                    if num in (1, 2, 3):
                                        bloque[num] = (x, y, v)
                            except ValueError:
                                pass
                        elif linea == "FIN":
                            self.publicar(("trama", bloque.copy()))
                            bloque.clear()
            except (serial.SerialException, OSError) as error:
                self.publicar(("estado", f"SIN CONEXIÓN: {error}  ·  reintentando en 2 s"))
                if self.parar.wait(2):
                    return

    def recibir_trama(self, objetivos):
        ahora = time.monotonic()
        self.objetivos = objetivos
        self.ultima_trama = ahora
        self.total_tramas += 1
        self.instantes.append(ahora)
        for i in range(1, 4):
            if i in objetivos:
                x, y, _ = objetivos[i]
                if self.trayectorias[i]:
                    x0, y0 = self.trayectorias[i][-1]
                    if math.hypot(x - x0, y - y0) > 1500:
                        self.trayectorias[i].clear()
                self.trayectorias[i].append((x, y))
            else:
                self.trayectorias[i].clear()

    def datos_demo(self):
        if self.parar.is_set():
            return
        t = time.monotonic() - self.inicio_demo
        x = int(1250 * math.sin(t * 0.6))
        y = int(2800 + 900 * math.cos(t * 0.45))
        datos = {1: (x, y, -25)}
        if int(t) % 12 < 8:
            datos[2] = (int(2100 + 350 * math.sin(t)), int(3800 + 650 * math.sin(t * 0.65)), 18)
        self.recibir_trama(datos)
        self.root.after(100, self.datos_demo)

    def actualizar(self):
        try:
            while True:
                evento = self.cola.get_nowait()
                if evento[0] == "estado":
                    self.estado.set(evento[1])
                elif evento[0] == "trama":
                    self.recibir_trama(evento[1])
        except queue.Empty:
            pass

        if not self.demo and self.ultima_trama and time.monotonic() - self.ultima_trama > 1.5:
            self.objetivos.clear()
            for historial in self.trayectorias.values():
                historial.clear()

        n_zona = 0
        for numero in range(1, 4):
            if numero not in self.objetivos:
                self.etiquetas[numero].set("Sin objetivo en esta posición")
                continue
            x, y, velocidad = self.objetivos[numero]
            distancia = math.hypot(x, y) / 1000.0
            angulo = math.degrees(math.atan2(x, y))
            zona = y > 0 and abs(x) <= 1500 and 0.5 <= distancia <= 4.0
            n_zona += int(zona)
            self.etiquetas[numero].set(
                f"X: {x / 1000:+.2f} m   ·   Y: {y / 1000:.2f} m\n"
                f"Distancia: {distancia:.2f} m\n"
                f"Ángulo: {angulo:+.1f}°\n"
                f"Velocidad: {velocidad / 100:+.2f} m/s\n"
                f"{'● EN ZONA DE ACCESO' if zona else 'Fuera de la zona'}"
            )
        hz = 0.0
        if len(self.instantes) > 1 and self.instantes[-1] > self.instantes[0]:
            hz = (len(self.instantes) - 1) / (self.instantes[-1] - self.instantes[0])
        self.resumen.set(
            f"Objetivos activos: {len(self.objetivos)}\n"
            f"Dentro de la zona: {n_zona}\n"
            f"Datos recibidos: {hz:.1f} Hz\n"
            f"Tramas completas: {self.total_tramas}"
        )
        self.dibujar()
        if not self.parar.is_set():
            self.root.after(40, self.actualizar)

    def dibujar(self):
        c = self.canvas
        w, h = c.winfo_width(), c.winfo_height()
        if w < 250 or h < 250:
            return
        c.delete("all")

        # Escala uniforme: un metro ocupa lo mismo en X que en Y.
        escala = min((w - 105) / 9.0, (h - 120) / 6.0)
        ox, oy = w / 2, h - 55

        def punto(x_m, y_m):
            return ox + x_m * escala, oy - y_m * escala

        # Rejilla y etiquetas de distancia.
        for x_m in range(-4, 5):
            x, _ = punto(x_m, 0)
            c.create_line(x, oy - 6 * escala, x, oy, fill=REJILLA, dash=(2, 5))
            c.create_text(x, oy + 19, text=f"{x_m}", fill=SUAVE,
                          font=("DejaVu Sans", 10))
        for y_m in range(0, 7):
            _, y = punto(0, y_m)
            c.create_line(ox - 4 * escala, y, ox + 4 * escala, y,
                          fill=REJILLA, dash=(2, 5))
            c.create_text(ox - 4 * escala - 13, y, text=f"{y_m}m",
                          fill=SUAVE, anchor="e", font=("DejaVu Sans", 10))
        c.create_text(w / 2, 27, text="POSICIÓN DE OBJETIVOS  ·  VISTA SUPERIOR",
                      fill=TEXTO, font=("DejaVu Sans", 13, "bold"))
        c.create_text(w / 2, h - 17, text="X: izquierda (−)  /  derecha (+)    |    Y: delante del radar",
                      fill=SUAVE, font=("DejaVu Sans", 10))

        # Arcos de distancia alrededor del radar.
        for radio_m in range(1, 7):
            r = radio_m * escala
            c.create_arc(ox - r, oy - r, ox + r, oy + r,
                         start=0, extent=180, style="arc", outline="#31516a",
                         width=1, dash=(3, 7))

        # Zona de acceso: límites de X y distancia (rectángulo orientativo).
        x1, y1 = punto(-1.5, 4)
        x2, y2 = punto(1.5, 0)
        c.create_rectangle(x1, y1, x2, y2, outline=VERDE, width=2, dash=(7, 5))
        c.create_text(ox, y1 - 14, text="ZONA DE ACCESO",
                      fill=VERDE, font=("DejaVu Sans", 10, "bold"))

        # Referencia física: radar en el origen.
        c.create_oval(ox - 10, oy - 10, ox + 10, oy + 10,
                      fill=TEXTO, outline=VERDE, width=2)
        c.create_text(ox + 17, oy - 13, text="RADAR", fill=TEXTO,
                      anchor="w", font=("DejaVu Sans", 10, "bold"))

        # Trayectorias cortas y posiciones actuales.
        for numero, historial in self.trayectorias.items():
            coords = []
            for x_mm, y_mm in historial:
                if -4.3 <= x_mm / 1000 <= 4.3 and 0 <= y_mm / 1000 <= 6.0:
                    coords.extend(punto(x_mm / 1000, y_mm / 1000))
            if len(coords) >= 4:
                c.create_line(*coords, fill=COLORES[numero], width=2, smooth=True)

        for numero, (x, y, v) in self.objetivos.items():
            x_m, y_m = x / 1000, y / 1000
            if not (-4.3 <= x_m <= 4.3 and 0 <= y_m <= 6.0):
                continue
            px, py = punto(x_m, y_m)
            distancia = math.hypot(x_m, y_m)
            en_zona = y > 0 and abs(x) <= 1500 and 0.5 <= distancia <= 4
            if en_zona:
                c.create_oval(px - 23, py - 23, px + 23, py + 23,
                              outline=VERDE, width=2)
            c.create_oval(px - 12, py - 12, px + 12, py + 12,
                          fill=COLORES[numero], outline=TEXTO, width=2)
            c.create_text(px + 19, py - 16, text=f"{numero} · {distancia:.2f} m",
                          fill=COLORES[numero], anchor="w",
                          font=("DejaVu Sans", 11, "bold"))

    def alternar_pantalla_completa(self, _evento=None):
        self.pantalla_completa = not self.pantalla_completa
        self.root.attributes("-fullscreen", self.pantalla_completa)

    def salir_pantalla_completa(self, _evento=None):
        self.pantalla_completa = False
        self.root.attributes("-fullscreen", False)

    def cerrar(self):
        self.parar.set()
        self.root.destroy()


def main():
    parser = argparse.ArgumentParser(description="Radar HLK-LD2450 en tiempo real")
    parser.add_argument("--port", default="/dev/ttyACM0", help="Puerto USB del ESP32-C6")
    parser.add_argument("--baud", type=int, default=115200, help="Velocidad lógica del monitor")
    parser.add_argument("--demo", action="store_true", help="Mostrar objetivos simulados sin radar")
    args = parser.parse_args()
    root = tk.Tk()
    VisorRadar(root, args.port, args.baud, args.demo)
    root.mainloop()


if __name__ == "__main__":
    main()
