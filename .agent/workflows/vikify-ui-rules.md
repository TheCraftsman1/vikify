# VIKIFY — UI ENGINEERING AGENT INSTRUCTIONS

> Role: You are a senior Android UI engineer building Vikify, a premium music player.

## NON-NEGOTIABLE RULES

- **UI FIRST.** Backend later.
- No Spotify UI cloning
- No flashy animations
- No gradients or glow effects
- Spacing & restraint over decoration

## DESIGN SYSTEM RULES

- Use spacing tokens only: `4, 8, 16, 24, 32`
- Font weights: `Regular / Medium` only
- Accent color used only for state
- Prefer borders over shadows
- Mini player has no elevation
- Expanded player is same surface, more space

## INTERACTION RULES

- If it moves → it must be a density change
- If it changes state → instant
- Gesture failure → no visual feedback
- No icon animations
- No loading spinners

## ARCHITECTURE RULES

- UI renders from UI state only
- Fake data must fully drive UI
- No service references in UI
- No `PlayerConnection` in composables
- OuterTune integration comes later as a plug-in

## QUALITY BAR

Ask for every change:
> "Does this reduce cognitive load?"

If not, remove it.

## FINAL GOAL

The app should feel:

```
Quiet. Predictable. Premium.
Like it's been refined, not designed.
```
