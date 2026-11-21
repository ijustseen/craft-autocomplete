# Craft Autocomplete

A client-side mod. Open a crafting table and it shows the one recipe that makes the most sense right now. The ingredients sit in the grid as faint items. Tab places them, like autocomplete in an editor.

It works on a vanilla server. The recipe book moves the items; the mod only chooses what to suggest. Recipes outside the book are never offered.

## Where it works

- The 3×3 crafting table
- The 2×2 grid in your inventory
- Furnace, blast furnace, and smoker. Each uses its own recipes. If the fuel slot is empty, the mod shows fuel and places it with Tab: coal first, then logs and planks

## How to use it

- **Tab** places the ingredients for one craft. In a furnace it also places enough fuel for that amount.
- **Shift+Tab** places as many as will fit.
- **`[`** and **`]`** cycle the next suggestions. There is no list on screen.
- When the grid is already filled, the next Tab adds one more item to every slot.
- Hover the faint result for the usual item tooltip. A Tab icon sits under the result slot.

On an empty grid the mod looks at your inventory and what you just picked up. It starts with a tool, weapon, or armor piece you lack or only have a worse version of. If you have no furnace but you are carrying ore, it suggests a furnace. When your hunger has dropped, food comes first in a smoker or furnace. The item you just took off the table stays first for a few seconds, so you can craft a stack of them.

If you start placing a recipe yourself, the suggestion switches to it and fills the empty slots. An item on your cursor counts too.

## Requirements

- Minecraft 26.3
- Fabric Loader 0.20.1 or newer
- Fabric API
- Java 25

The mod is client-side. It does not need to be installed on the server.

This is alpha 0.1.0, and only Fabric 26.3 for now. It does not look at the world around you, and it does not suggest the anvil, stonecutter, or brewing stand.

## License

[MIT](LICENSE)
