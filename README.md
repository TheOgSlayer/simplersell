# SellWorth
`/sell` GUI + `/worth` GUI + "Worth: $x" tooltip on every item. Paper 1.21+, Java 21.

**Requires:** Paper, [Vault](https://www.spigotmc.org/resources/vault.34315/) + an economy plugin (EssentialsX etc.), [ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/).

## Build
Push to GitHub -> Actions tab -> latest run -> download the `SellWorth` artifact.
Or locally: `mvn package` -> `target/SellWorth.jar`.

## Use
- Edit `plugins/SellWorth/prices.yml`, then `/sellreload`.
- `/sell` - put items in, click the green pane (bottom-right) to sell.
- `/worth` - all prices; `/worth oak` - filtered GUI; `/worth oak planks` - chat + action bar.
