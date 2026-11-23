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
toca() { read X Y <<< "$(python3 tools/ui.py itexto "$1")"; echo "tocar '$1' em $X $Y"; [ -n "$X" ] && adb shell input tap $X $Y; }
segura() { read X Y <<< "$(python3 tools/ui.py itexto "$1")"; echo "segurar '$1' em $X $Y"; [ -n "$X" ] && adb shell input swipe $X $Y $X $Y 900; }
abrir --es tela lista; shot 1-lista 7
abrir --es tela conversa --ei pos 23; shot 2-conversa-trecho-junto 7
abrir --es tela conversa --ei pos 65; shot 3-conversa-fim 7
abrir --es tela editar; shot 4-editar 7
abrir --es tela compartilhar; shot 5-compartilhar 7
abrir --es tela ajustes; shot 6-ajustes 5
abrir --es tela filtro; shot 15-filtro-painel 7
toca "Pai"; sleep 1; shot 16-filtro-pai 1
toca "Mostrar"; shot 17-lista-filtrada 3
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
read X2 Y2 <<< "$(python3 tools/ui.py desc Tocar)"; adb shell input tap $X2 $Y2; sleep 1.5
read X Y <<< "$(python3 tools/ui.py texto 'Certo. Vou pedir')"; adb shell input tap $X $Y; shot 10-tocou-palavra-tocando 2
# visão geral da gravação inteira: arrastar pula direto
abrir --es tela prova --ei pos 100; sleep 6
read YF YV <<< "$(python3 tools/ui.py faixas)"
adb shell input swipe 150 $YV 800 $YV 2000 &
sleep 1.4; adb exec-out screencap -p > $OUT/13-arrastando-visao-geral.png
wait; shot 14-depois-visao-geral 2

# ===== pastas de verdade: Recordings/Call e Recordings/Voice Recorder =====
R=/sdcard/Recordings
adb shell "mkdir -p '$R/Voice Recorder' '$R/Call'"
for f in tools/amostras/Voz*.m4a; do adb push "$f" "$R/Voice Recorder/"; done
for f in tools/amostras/Call*.m4a; do adb push "$f" "$R/Call/"; done
adb shell content call --uri content://media --method scan_volume --arg external_primary || true
sleep 4
pasta() { echo "== $1"; adb shell ls -R "$R" ; } >> $OUT/pastas.txt
pasta "antes"
adb shell am force-stop $P; sleep 1
adb shell am start -n $P/.MainActivity --ez demo false; shot 20-sem-pasta 6
toca "Liberar Recordings"; shot 21-seletor 5
toca "Use this folder"; sleep 3; shot 22-permitir 1
toca "Allow"; shot 23-aba-gravador 6
toca "Chamadas"; shot 24-aba-chamadas 3
# renomear
segura "Vivo"; shot 25-selecionada 2
toca "Renomear"; sleep 2
read X Y <<< "$(python3 tools/ui.py itexto "Call recording Vivo")"; adb shell input tap $X $Y; sleep 1
adb shell input keyevent KEYCODE_MOVE_END
adb shell input keyevent $(for i in $(seq 1 45); do printf 'KEYCODE_DEL '; done)
adb shell input text "Conta_da_Vivo"; shot 26-renomeando 2
toca "Salvar"; shot 27-renomeado 4
pasta "depois de renomear Vivo -> Conta_da_Vivo"
# mover uma chamada pro Gravador
segura "Dra Ana"; sleep 1; toca "Mover"; shot 28-mover-pra 3
toca "Gravador"; shot 29-movido 4
pasta "depois de mover Dra Ana pra Voice Recorder"
# nova pasta
segura "Pai"; sleep 1; toca "Mover"; sleep 2; toca "Nova pasta"; sleep 2
adb shell input text "Familia"; shot 30-nova-pasta 2
toca "Salvar"; shot 31-depois-nova-pasta 5
pasta "depois de mover Pai pra nova pasta Familia"
# apagar (lixeira do Android)
toca "Gravador"; sleep 3
segura "Voz 261121"; sleep 1; toca "Apagar"; shot 32-lixeira-pergunta 3
toca "Allow"; shot 33-depois-lixeira 4
pasta "depois de apagar Voz 261121 (lixeira)"
adb shell content query --uri content://media/external/audio/media --projection _display_name:is_trashed --where "is_trashed=1" >> $OUT/pastas.txt 2>&1 || true
read X Y <<< "$(python3 tools/ui.py desc ajustes)"; adb shell input tap $X $Y; shot 34-ajustes-atualizacao 5
adb logcat -d -s AndroidRuntime:E > $OUT/erros.txt || true
echo "fim do roteiro" >> $OUT/erros.txt
exit 0
