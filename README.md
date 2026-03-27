# VoidEconomy

A flexible economy plugin for Spigot/Paper (1.21.1+) with fully configurable currencies. Add any currency via `config.yml` and the commands, permissions, and placeholders are created automatically.

## Features

- Unlimited configurable currencies (money, tokens, crystals, ...)
- Per-currency commands: `/money`, `/tokens`, `/moneytop`, etc.
- SQLite with WAL mode — safe for high-frequency balance updates
- In-memory player cache with automatic database sync
- PlaceholderAPI support
- Developer API with cancellable events

## Commands & Permissions

Replace `<currency>` with the currency ID (e.g. `money`, `tokens`).

| Command | Description | Permission |
|---|---|---|
| `/<currency>` | View your own balance | — |
| `/<currency> <player>` | View another player's balance | `voideconomy.<currency>.balance.others` |
| `/<currency> give <player> <amount>` | Give balance | `voideconomy.<currency>.give` |
| `/<currency> take <player> <amount>` | Take balance | `voideconomy.<currency>.take` |
| `/<currency> set <player> <amount>` | Set balance | `voideconomy.<currency>.set` |
| `/<currency> reset <player>` | Reset to default | `voideconomy.<currency>.reset` |
| `/<currency> clear <player>` | Set to 0 | `voideconomy.<currency>.clear` |
| `/<currency>top` | Top 10 richest players | `voideconomy.<currency>.top` |

## PlaceholderAPI

| Placeholder | Description |
|---|---|
| `%voideconomy_<currency>%` | Your balance (formatted) |
| `%voideconomy_<currency>_raw%` | Your balance (raw number) |
| `%voideconomy_<currency>_top_<rank>_name%` | Name of player at rank N |
| `%voideconomy_<currency>_top_<rank>_balance%` | Balance of player at rank N (formatted) |
| `%voideconomy_<currency>_top_<rank>_balance_raw%` | Balance of player at rank N (raw) |

## Adding a Custom Currency

Add a new entry under `currencies` in `config.yml`:

```yaml
currencies:
  crystals:
    display-name: "&dCrystals"
    symbol: "✦"
    command: "crystals"
    default-balance: 0.0
    max-balance: -1
    decimals: 0
    messages:
      balance-self: "&aYour crystals: &d{amount}{symbol}"
      balance-other: "&a{player}'s crystals: &d{amount}{symbol}"
      given: "&aGave &d{amount}{symbol}&a to &6{player}&a."
      taken: "&aTook &d{amount}{symbol}&a from &6{player}&a."
      set: "&aSet &6{player}&a's crystals to &d{amount}{symbol}&a."
      reset: "&aReset &6{player}&a's crystals."
      cleared: "&aCleared &6{player}&a's crystals."
      received-give: "&aYou received &d{amount}{symbol}&a from an admin."
      received-take: "&cAn admin took &d{amount}{symbol}&c from you."
      top-header: "&d&l--- Top {limit} {display-name} ---"
      top-entry: "&d#{rank} &e{player} &7- &d{amount}{symbol}"
      top-footer: "&d&m                              "
      top-empty: "&cNo data found yet."
```

Restart the server — `/crystals` and `/crystalstop` are registered automatically.

---

## Developer API

### Dependency

**Maven:**
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.VoidPlugins</groupId>
        <artifactId>VoidEconomy</artifactId>
        <version>1.0.1</version>
        <classifier>api</classifier>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

**Gradle (Groovy):**
```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    compileOnly 'com.github.VoidPlugins:VoidEconomy:1.0.1:api'
}
```

**Gradle (Kotlin DSL):**
```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("com.github.VoidPlugins:VoidEconomy:1.0.1:api")
}
```

### plugin.yml

```yaml
depend:
  - VoidEconomy
```

### Usage

All methods return `CompletableFuture` and are safe to call from any thread. Reads for online players return instantly from cache.

```java
VoidEconomyAPI api = VoidEconomyAPI.getInstance();

// Get balance
api.getBalance(player.getUniqueId(), "money").thenAccept(balance -> {
    player.sendMessage("Balance: " + balance);
});

// Give
api.give(player.getUniqueId(), "money", 100).thenAccept(newBalance -> {
    player.sendMessage("New balance: " + newBalance);
});

// Remove
api.remove(player.getUniqueId(), "tokens", 50);

// Set exact amount
api.set(player.getUniqueId(), "money", 500);

// Clear (set to 0)
api.clear(player.getUniqueId(), "crystals");

// Reset to config default
api.reset(player.getUniqueId(), "money");

// Check if player has enough
api.has(player.getUniqueId(), "money", 100).thenAccept(hasEnough -> {
    if (!hasEnough) player.sendMessage("Not enough money!");
});

// OfflinePlayer overloads work too
api.give(Bukkit.getOfflinePlayer(uuid), "tokens", 25);
```

### CurrencyChangeEvent

Fired on every balance change — from commands and the API. Cancellable.

```java
@EventHandler
public void onCurrencyChange(CurrencyChangeEvent event) {
    if (event.getCurrency().getId().equals("money")) {
        // Apply a 10% tax on all money given
        if (event.getReason() == CurrencyChangeEvent.Reason.GIVE) {
            double delta = event.getNewBalance() - event.getOldBalance();
            event.setNewBalance(event.getOldBalance() + (delta * 0.9));
        }
    }
}
```

**Available reasons:** `GIVE`, `REMOVE`, `SET`, `RESET`, `CLEAR`

| Method | Description |
|---|---|
| `getPlayerUUID()` | UUID of the affected player |
| `getPlayerName()` | Name of the affected player |
| `getCurrency()` | The currency being changed |
| `getOldBalance()` | Balance before the change |
| `getNewBalance()` | Balance that will be applied (modifiable) |
| `setNewBalance(double)` | Override the result (clamped to max-balance) |
| `getDelta()` | `newBalance - oldBalance` |
| `getReason()` | Why the change occurred |
| `isCancelled()` / `setCancelled(boolean)` | Cancel the change |
