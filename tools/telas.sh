#!/bin/bash
# Instala o APK no emulador, abre cada tela no modo demonstração e tira print.
set -x
APK=$1; OUT=$2; mkdir -p $OUT
P=br.mev.gravador
adb install -r $APK
# o launcher do emulador costuma travar no começo e abre um aviso por cima: esconde avisos e espera assentar
adb shell settings put global hide_error_dialogs 1
adb shell settings put global anr_show_background 0
sleep 40
adb shell input keyevent KEYCODE_HOME; sleep 5
adb shell settings put system screen_off_timeout 600000
adb shell cmd uimode night yes
# letra grande, como no celular dele
adb shell settings put system font_scale 1.3
tira_aviso() { python3 tools/ui.py texto "Wait" | { read X Y && [ -n "$X" ] && adb shell input tap $X $Y; } ; true; }
shot() { sleep ${2:-5}; tira_aviso; adb exec-out screencap -p > $OUT/$1.png; }
abrir() { adb shell am force-stop $P; sleep 1; adb shell am start -n $P/.MainActivity --ez demo true "$@"; }
abrir --es tela lista; shot 1-lista 7
abrir --es tela conversa --ei pos 23; shot 2-conversa-trecho-junto 7
abrir --es tela conversa --ei pos 65; shot 3-conversa-fim 7
abrir --es tela editar; shot 4-editar 7
abrir --es tela compartilhar; shot 5-compartilhar 7
abrir --es tela ajustes; shot 6-ajustes 5
abrir --es tela prova --ei pos 130; shot 11-prova-7-vozes 7
abrir --es tela prova --ei pos 200; shot 12-prova-mais-adiante 7
# arrastar a linha do áudio: print no meio do arraste (texto deve acompanhar)
abrir --es tela conversa --ei pos 2; sleep 6
python3 tools/ui.py faixas --debug 2>&1 | head -c 1500; read YF YV <<< "$(python3 tools/ui.py faixas)"; echo "faixas y=$YF visão geral y=$YV"
adb shell input swipe 200 $YF 950 $YF 3000 &
sleep 2.2; adb exec-out screencap -p > $OUT/7-no-meio-do-arraste.png
wait; shot 8-depois-de-arrastar 2
# tocar numa palavra com o áudio pausado
abrir --es tela conversa --ei pos 2; sleep 6
read X Y <<< "$(python3 tools/ui.py texto 'Faz umas duas')"; echo "palavra em $X $Y"
adb shell input tap $X $Y; shot 9-tocou-palavra-pausado 2
# play e depois tocar numa palavra: deve continuar tocando
read X2 Y2 <<< "$(python3 tools/ui.py texto '▶')"; adb shell input tap $X2 $Y2; sleep 1.5
read X Y <<< "$(python3 tools/ui.py texto 'Certo. Vou pedir')"; adb shell input tap $X $Y; shot 10-tocou-palavra-tocando 2
# visão geral da gravação inteira: arrastar pula direto
abrir --es tela prova --ei pos 100; sleep 6
read YF YV <<< "$(python3 tools/ui.py faixas)"
adb shell input swipe 150 $YV 800 $YV 2000 &
sleep 1.4; adb exec-out screencap -p > $OUT/13-arrastando-visao-geral.png
wait; shot 14-depois-visao-geral 2
adb logcat -d -s AndroidRuntime:E > $OUT/erros.txt || true
echo "fim do roteiro" >> $OUT/erros.txt
exit 0
