#!/bin/bash
# Instala o APK no emulador, abre cada tela no modo demonstração e tira print.
set -x
APK=$1; OUT=$2; mkdir -p $OUT
P=br.mev.gravador
adb install -r $APK
adb shell settings put system screen_off_timeout 600000
adb shell cmd uimode night yes
shot() { sleep ${2:-5}; adb exec-out screencap -p > $OUT/$1.png; }
abrir() { adb shell am force-stop $P; sleep 1; adb shell am start -n $P/.MainActivity --ez demo true "$@"; }
abrir --es tela lista; shot 1-lista 7
abrir --es tela conversa --ei pos 23; shot 2-conversa-trecho-junto 7
abrir --es tela conversa --ei pos 65; shot 3-conversa-fim 7
abrir --es tela editar; shot 4-editar 7
abrir --es tela compartilhar; shot 5-compartilhar 7
abrir --es tela ajustes; shot 6-ajustes 5
# arrastar a linha do áudio: print no meio do arraste (texto deve acompanhar)
abrir --es tela conversa --ei pos 2; sleep 6
adb shell input swipe 260 545 980 545 3000 &
sleep 2.2; adb exec-out screencap -p > $OUT/7-no-meio-do-arraste.png
wait; shot 8-depois-de-arrastar 2
# tocar numa palavra ("ultrassom", fala das 00:29) com o áudio pausado
abrir --es tela conversa --ei pos 2; sleep 6
adb shell input tap 807 2005; shot 9-tocou-palavra-pausado 2
# tocar play e depois numa palavra: deve continuar tocando
adb shell input tap 125 545; sleep 1.5
adb shell input tap 300 1050; shot 10-tocou-palavra-tocando 2
adb logcat -d -s AndroidRuntime:E > $OUT/erros.txt || true
echo "fim do roteiro" >> $OUT/erros.txt
exit 0
