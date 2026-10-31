#!/bin/bash
# usage: render.sh deck.pptx outdir   (needs LibreOffice path in $SO)
S=/tmp/claude-1000/-home-trinq3-Code-projects-hust-information-retrieval/f5574d44-5ff6-472b-9450-8b647c8feb72/scratchpad
SO=$S/lo/root/opt/libreoffice25.8/program/soffice
mkdir -p "$2"; rm -f "$2"/*.jpg "$2"/*.pdf
timeout 300 $SO --headless -env:UserInstallation=file://$S/lo/profile --convert-to pdf --outdir "$2" "$1" >/dev/null 2>&1
pdftoppm -jpeg -r ${3:-80} "$2"/$(basename "${1%.pptx}").pdf "$2"/s
ls "$2"/s-*.jpg | head -50
