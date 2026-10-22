# Color Wheel (Fabric, Minecraft 26.3, solo)

## Compiler
1. Installe **Java 25** (JDK).
2. Dans ce dossier : `./gradlew build` (Windows : `gradlew.bat build`).
3. Le mod est dans `build/libs/colorwheel-1.0.0.jar` (pas le `-sources`).
4. Mets-le dans `mods/` avec **Fabric Loader 0.19.5+** et **Fabric API 0.163.0+26.3**.

> Le code n'a pas pu être compilé là où il a été écrit (pas d'accès à Maven).
> Si `./gradlew build` affiche une erreur, copie-la-moi : ce sera un nom de classe à corriger.

## Utiliser
- Touche **K** : ouvre le menu (mode Roue/Bloc, distance en chunks, fréquence, démarrer/arrêter, deux restaurations indépendantes, objectifs).
- Commandes : `/colorwheel start | stop | reset | skip | mode wheel|block | radius <1-64 chunks> | interval <10-3600>`
- Réglages aussi dans `config/colorwheel.properties`.

## Règles
- **Roue** : tire une des 11 couleurs (Noir, Blanc, Orange, Jaune, Rose, Gris, Violet, Bleu, Marron, Vert, Rouge).
  Tous les blocs de cette couleur disparaissent dans le rayon autour de toi. Une couleur tirée quitte la roue.
- **Bloc** : un bloc présent autour de toi est tiré, son nom s'affiche en haut de l'écran, puis il disparaît dans le rayon.
- **Objectif** : une roue des difficultés (vert = facile, jaune = moyen, orange = difficile, rouge = presque impossible) tourne, puis un objectif de cette difficulté est choisi au hasard (20 objectifs). Survis jusqu à l avoir réussi.
  Réussi → « Objectif réussi ! », le monde est restauré, la roue est remise à zéro et la roue des difficultés retourne.
- **Objectifs, réglage dans le menu** : roue automatique, objectif prédéfini, aucun objectif ou texte personnalisé. L’objectif personnalisé est interprété comme une recherche d’objet dans l’inventaire par nom affiché ou identifiant ; le nom doit correspondre à un objet réel disponible. Les phrases libres non liées à un objet ne sont pas interprétées comme des conditions arbitraires.
- **Mort** : le monde est restauré et la roue remise à zéro, tu retentes le même objectif
  (`resetObjectivesOnDeath=true` dans la config pour retirer un nouvel objectif via la roue).
- La bedrock peut disparaître (elle compte comme « gris »).

## Limites connues
- La couleur dominante est échantillonnée depuis la texture `textures/block/<identifiant>.png` lorsqu'elle existe. Les blocs dont le modèle utilise une texture partagée ou un nom différent retombent sur les règles par nom ; ce n'est donc pas encore une correspondance parfaite de tous les modèles et variantes de Minecraft.
- Une distance de 64 chunks correspond à un rayon de 1 024 blocs. Le travail est découpé en lots de 50 000 positions par tick pour éviter un blocage unique, mais un si grand rayon peut prendre très longtemps et solliciter fortement le serveur.
- Coffres, fours, etc. (blocs avec contenu) sont **protégés** contre la suppression par la roue (`protectContainers=true`).
- Le bouton « Réinitialiser les blocs cassés ou posés » conserve l'état des blocs et les données des block entities en mémoire pour les positions capturées pendant le défi. Les blocs posés sont détectés en observant le bloc ciblé et le bloc adjacent lors de l'utilisation d'un bloc plaçable ; certaines méthodes de placement particulières peuvent ne pas être détectées. Cette histoire n'est pas persistée après l'arrêt du serveur. Les objets au sol et les créatures ne reviennent pas.
