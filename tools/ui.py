#!/usr/bin/env python3
"""Acha coordenadas na tela do emulador pela árvore de vistas (uiautomator)."""
import re, subprocess, sys

def dump():
    subprocess.run(["adb", "shell", "uiautomator", "dump", "/sdcard/ui.xml"], capture_output=True)
    return subprocess.run(["adb", "shell", "cat", "/sdcard/ui.xml"], capture_output=True, text=True).stdout

def nos(xml):
    for m in re.finditer(r'<node [^>]*?text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
        yield m.group(1), tuple(int(m.group(i)) for i in range(2, 6))

def dens():
    out = subprocess.run(["adb", "shell", "wm", "density"], capture_output=True, text=True).stdout
    return int(re.findall(r"\d+", out)[-1]) / 160

xml = dump()
if "--debug" in sys.argv: print(xml[:3000], file=sys.stderr)
modo = sys.argv[1]
if modo == "texto":
    alvo = sys.argv[2]
    for t, (x0, y0, x1, y1) in nos(xml):
        if alvo in t and (alvo != "Wait" or t == "Wait"):
            # começo do texto: ajusta pela posição da palavra na linha (aproximado)
            print(x0 + 40, (y0 + y1) // 2 if y1 - y0 < 120 else y0 + 40); break
elif modo == "itexto":
    # sem diferenciar maiúscula (botões do Android vêm em CAIXA ALTA às vezes); pega o centro
    alvo = sys.argv[2].lower()
    todos = list(nos(xml))
    achado = [b for t, b in todos if t.strip().lower() == alvo] or [b for t, b in todos if alvo in t.lower()]
    if achado:
        x0, y0, x1, y1 = achado[0]
        print((x0 + x1) // 2, (y0 + y1) // 2)
elif modo == "desc":
    for m in re.finditer(r'<node [^>]*?content-desc="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
        if m.group(1) == sys.argv[2]:
            x0, y0, x1, y1 = (int(m.group(i)) for i in range(2, 6)); print((x0 + x1) // 2, (y0 + y1) // 2); break
elif modo == "faixas":
    d = dens()
    for t, (x0, y0, x1, y1) in nos(xml):
        if t.startswith(" / "):  # tempo total, na linha do play
            print(int(y0 - 73 * d), int(y0 - 29 * d)); break
