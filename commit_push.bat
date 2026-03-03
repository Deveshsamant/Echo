@echo off
cd /d d:\Echo
echo committing...
git add .
git commit -m "initial commit"
git branch -M main
git remote set-url origin https://github.com/Deveshsamant/Echo.git
git push -u origin main
