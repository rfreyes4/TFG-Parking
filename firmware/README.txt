RADAR HLK-LD2450 EN C + RAYLIB (UBUNTU 24.04)

Los archivos monitor_ld2450.c y CMakeLists.txt deben quedar en la MISMA
carpeta donde ya esta monitor_ld2450.py. README.txt es opcional.

1) Instala una sola vez las dependencias de compilacion:
   sudo apt update
   sudo apt install -y build-essential cmake git libasound2-dev \
     libx11-dev libxrandr-dev libxi-dev libxcursor-dev \
     libxinerama-dev libgl1-mesa-dev

2) Abre una terminal EN ESA CARPETA y compila:
   cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
   cmake --build build -j "$(nproc)"

   Nota: CMake descarga raylib 5.5 de GitHub la primera vez.

3) Modo demostracion (sin sensor):
   ./build/monitor_ld2450_c --demo

4) Radar real (cierra Python antes):
   ./build/monitor_ld2450_c /dev/ttyACM0

   Si el puerto cambia, compruebalo con:
   ls /dev/ttyACM* /dev/ttyUSB* 2>/dev/null

Si aparece Permission denied, comprueba que el usuario pertenece al grupo dialout:
   groups
   sudo usermod -aG dialout "$USER"
Cierra sesion y vuelve a entrar para que surta efecto.

F11: alternar pantalla completa. ESC: salir.

El ESP32-C6 debe enviar texto a 115200 baudios:
INICIO
OBJ,1,100,2500,20
OBJ,2,-900,3100,-6
FIN

No se modifica el codigo del ESP32. Radar LD2450 -> ESP32 a 256000 baudios.
Las coordenadas estan en milimetros, velocidad en centimetros/segundo.
El radar da ~10 tramas/s; 120 FPS de ventana no significa 120 mediciones/s.
La antiguedad de dato mostrada es desde que llega la trama al ordenador,
no desde que el radar realiza la medicion.
