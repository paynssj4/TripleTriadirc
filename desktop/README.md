# Triple Triad – version PC (Swing)

Projet indépendant : il ne modifie rien dans `app/`. Il compile le moteur et l'IA de l'appli Android
(`Card`, `Owner`, `Rules`, `GameEngine`, `AiPlayer`) et ajoute une interface Swing, un client IRC
(`IrcClient` = `IrcSession` sans Android, même protocole "TT1") et un profil local.

## Installation
Copie ce dossier `desktop/` à côté de `app/` (dans `TripleTriad/`).

## Lancer
    cd TripleTriad\desktop
    ..\gradlew.bat run            (ou double-clic sur lancer.bat)

## Ce que contient la version PC
- Menu : jouer contre l'IA, défis en ligne, collection / mains, option « Cacher la main adverse ».
- Première partie : choix de la collection (25 cartes de niveau 1 à 5) puis de la main (5 cartes, ou 13 pour le 5x5).
- Lobby IRC : connexion, discussion du salon, liste des joueurs, défi par double-clic (ou bouton « Défier »).
  Salon par défaut identique à l'appli Android (irc.quakenet.org:6667, #tripletriad-fr) : choisissez le même salon.
- Après une victoire : +10 or et une carte adverse capturée à garder (comme sur Android).
- Profil enregistré dans `%USERPROFILE%\.tripletriad\profile.properties`.

## Pas repris
Animations, sons / musique.

## Exécutable portable
    ..\gradlew.bat installDist
    -> build\install\triple-triad-desktop\bin\triple-triad-desktop.bat  (+ chemin du dossier assets en argument)
