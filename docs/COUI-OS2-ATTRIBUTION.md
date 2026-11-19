# Attributions COUI 2.0

## Fond OS2 du briefing

Le fond du briefing reprend le shader OS2, ses quatre jeux de paramètres et le
code de rendu du laboratoire « Background effect » de COUI, dépôt
`https://github.com/suqi8/coui`, commit
`df5883f7f36b075125469488c54625152e83a556`.

Fichiers adaptés dans `app/src/main/kotlin/io/github/mangi/eta/ui/screens/home/effect/` :
`OS2BgFrag.kt`, `BgEffectConfig.kt`, `BgEffectPainter.kt`,
`BgEffectModifier.kt` et `DeviceType.kt`. L'intégration Compose se trouve dans
`NowBriefMotion.kt`. La variante OS3 et ses paramètres ont été retirés.

Copyright 2026, compose-coui-ui contributors. Licence Apache-2.0 ; voir
`https://github.com/suqi8/coui/blob/df5883f7f36b075125469488c54625152e83a556/LICENSE`.

## Échafaudage de page et pont de flou

`app/src/main/kotlin/io/github/mangi/eta/ui/components/coui/CouiBlur.kt` et
`CouiScaffoldPage.kt` sont adaptés de la recette du skill `coui-app`
(`.claude/skills/coui-app/references/scaffold.md`) du même dépôt et du même
commit, sous la même licence Apache-2.0. Le texte de licence est embarqué dans
`app/src/main/assets/licenses/coui-Apache-2.0.txt`.
