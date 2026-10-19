# Posted Signage

NeoForge mod for Minecraft Java 1.21.1. It adds safety and wayfinding signs as wall-placeable blocks. Right-click a vertical wall face to install one; it drops as an item when broken or when its supporting block is removed.

The mod also includes a connecting sansevieria planter with exact textured foliage on two crossed X planes and a flat ten-wide by four-high T-shaped six-figure mural.

The restroom collection uses double-sided 3D signs that project perpendicular to the wall, with a raised frame, wall plate, and support brackets. The other safety signs remain thin wall panels.

## Items

- `posted_signage:drinking_fountain`
- `posted_signage:customizable_hanging_sign`
- `posted_signage:customizable_hanging_sign_green`
- `posted_signage:customizable_hanging_sign_red`
- `posted_signage:customizable_hanging_sign_yellow`
- `posted_signage:customizable_hanging_sign_light_blue`
- `posted_signage:customizable_hanging_sign_purple`
- `posted_signage:customizable_hanging_sign_orange`
- `posted_signage:right_arrow_sign`
- `posted_signage:fire_extinguisher_sign`
- `posted_signage:emergency_exit_sign`
- `posted_signage:do_not_use_elevator_sign`
- `posted_signage:emergency_stairs_sign`
- `posted_signage:sansevieria_planter`
- `posted_signage:statue_relief`

### Decorative models

- Place two sansevieria planters next to each other to join them into one continuous two-block planter. Each planter accepts only one partner, and the remaining half returns to its single model when the other half is removed.
- Place the mural from the lower-left cell of its four-wide center stem on a sturdy ten-wide by four-high wall area. One item installs all 28 occupied parts of the T, and breaking a part removes the connected mural.

### Restroom hanging signs

The men's, women's, and mixed designs are available in the original blue plus green, red, orange, yellow, and light blue, for 18 restroom signs total.

Base IDs:

- `posted_signage:mens_restroom_sign`
- `posted_signage:womens_restroom_sign`
- `posted_signage:mixed_restroom_sign`

Colored variants append one of these suffixes to the base ID:

- `_green`
- `_red`
- `_orange`
- `_yellow`
- `_light_blue`

All items are available from the **Posted Signage** creative tab and can also be obtained with `/give`.

## Customizable CIT hanging sign

The customizable signs follow the wide white CIT reference design and support different text on each side. Seven separate items provide blue, green, red, yellow, light blue, purple, and orange CIT panels.

- Place it against a vertical wall.
- Right-click either broad face with an empty hand to edit that face's four text lines.
- Select the desired color variant from the creative tab or with `/give`.
- The white background and CIT mark are part of the sign texture; only the central room-name lettering is editable.
- The main panel remains white and the editable lettering remains dark for readability.
- Glow ink and honeycomb retain the usual vanilla sign behavior.

## Drinking fountain and hydration (1.4.0)

Install the same JAR on client and server. Obtain the fountain from the creative tab or
`/give @s posted_signage:drinking_fountain`. Place it against two vertically adjacent solid
wall faces, leaving two empty cells in front. It occupies a 1x2x1 space and keeps the full
pedestal, basin and back panel. No floor support, tank or pipes are needed.

Right-click the long front push bar beneath the basin to drink. A two-second water-particle
arc and sound accompany the button press. Each player has a three-second reuse cooldown.
Drinking adds six hydration points, two hunger points and two saturation points (vanilla
caps apply), and extinguishes fire. Breaking either half drops one item in Survival;
Creative removal drops none.

Hydration is 0–20, shown as ten drops above the hunger/air HUD. Survival and Adventure
players lose one point per minute of game time. At zero, dehydration applies the equivalent
of Weakness I and Slowness I; drinking removes those penalties without removing potion
effects. Hydration persists through saving, death and dimension changes. Creative and
Spectator players do not lose hydration or receive penalties. This version adds no recipe
or bottle-filling mechanic; map creators supply fountains through Creative or commands.

### Development checks

`gradlew.bat runGameTestServer` runs fountain/hydration tests in an isolated generated world.
It also covers building tools, the three active facilities, player-relative texture orientation,
exact drops, working doors, confirmed bin disposal, and legacy facility migration (24 tests total).
The tests and their arena are excluded from the release JAR. Workspace asset generators and
validation live two directories above this Gradle project.

### Functional facilities

- Trash bin: one block tall with nine persistent slots. Right-click while holding an item
  to deposit the held stack; any overflow stays in your hand. Right-click with an empty main
  hand to open storage. The **Empty bin** button permanently deletes only the stored contents
  with one click; your inventory and cursor item are preserved. Items can be recovered normally
  or with shift-click before emptying. Closing storage keeps its contents; breaking the bin
  returns undeleted items. The previous crouch-double-click disposal gesture is removed.
- Wide glass door (2x3): right-click any part to rotate the complete panel open/closed.
  Its occupied footprint moves with the panel; the destination cells must be clear.
- Short glass door (1x2): right-click either part to open/close the single leaf.
  Doors update collision and play metal-door sounds; their open state is saved. Interaction
  is manual, without redstone control. The fixed CIT label is not yet editable.
- The retired 1x3 glass door is no longer registered or listed. Old block/item IDs resolve
  to the short door. On chunk load, obsolete third-row door cells and second-row bin cells
  are removed without extra drops. The old editable Blockbench model is retained.

### Compilation

Requires a Java 21 toolchain.

```powershell
.\validate_assets.ps1
.\gradlew.bat build
```

The distributable mod JAR is created under `build/libs/`.
