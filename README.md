# VoidEconomy

A flexible economy plugin for Spigot/Paper (1.21.1+) with fully configurable currencies. Add any currency via `config.yml` and its commands, permissions, and placeholders are created automatically.

## Features

- Unlimited configurable currencies (money, tokens, crystals, ...)
- Per-currency commands: `/money`, `/moneytop`, `/tokens`, `/tokenstop`, etc.
- Human-friendly amount input: `1k`, `1.5m`, `1b`, `1t`, `1q`, `1qt`, ...
- Compact balance display in messages: `$1,500,000 (1.5M)`
- SQLite with WAL mode — safe for high-frequency balance updates
- In-memory player cache with automatic database sync
- PlaceholderAPI support including compact formatting
- Player-to-player payments with `/pay`
- Developer API with cancellable events

---

## Commands & Permissions

### Per-currency commands
Replace `<currency>` with the currency command (e.g. `money`, `tokens`).

| Command | Description | Permission |
|---|---|---|
| `/<currency>` | View your own balance | — |
| `/<currency> <player>` | View another player's balance | `voideconomy.<currency>.balance.others` |
| `/<currency> give <player> <amount>` | Give balance | `voideconomy.<currency>.give` |
| `/<currency> add <player> <amount>` | Add balance (alias for give) | `voideconomy.<currency>.add` |
| `/<currency> take <player> <amount>` | Take balance | `voideconomy.<currency>.take` |
| `/<currency> remove <player> <amount>` | Remove balance (alias for take) | `voideconomy.<currency>.remove` |
| `/<currency> set <player> <amount>` | Set balance to exact amount | `voideconomy.<currency>.set` |
| `/<currency> reset <player>` | Reset to config default | `voideconomy.<currency>.reset` |
| `/<currency> clear <player>` | Set to 0 | `voideconomy.<currency>.clear` |
| `/<currency>top` | Top 10 richest players | `voideconomy.<currency>.top` |

### General commands

| Command | Description | Permission |
|---|---|---|
| `/pay <player> <currency> <amount>` | Pay another online player | `voideconomy.pay` (default: true) |
| `/voideconomy` | List all currencies and their commands | `voideconomy.info` (default: op) |
| `/voideconomy reload` | Reload config and re-register currencies | `voideconomy.reload` (default: op) |

---

## Amount Input

All amount arguments accept human-friendly suffixes (case-insensitive). Commas and underscores are stripped automatically.

| Input | Value |
|---|---|
| `1k` or `1,000` | 1,000 |
| `1.5m` | 1,500,000 |
| `1b` | 1,000,000,000 |
| `1t` | 1,000,000,000,000 |
| `1q` | 1,000,000,000,000,000 |
| `1qt` | 1,000,000,000,000,000,000 |
| `1sx` | sextillion (10²¹) |
| `1sp` | septillion (10²⁴) |
| `1oc` | octillion (10²⁷) |
| `1no` | nonillion (10³⁰) |
| `1dc` | decillion (10³³) |
| and more... | up to vigintillion (`vi`, 10⁶³) |

---

## PlaceholderAPI

| Placeholder | Description |
|---|---|
| `%voideconomy_<currency>%` | Your balance (formatted with decimals) |
| `%voideconomy_<currency>_raw%` | Your balance (raw double) |
| `%voideconomy_<currency>_formatted%` | Your balance in compact notation (e.g. 1.5M) |
| `%voideconomy_<currency>_top_<rank>_name%` | Name of player at rank N |
| `%voideconomy_<currency>_top_<rank>_balance%` | Balance of rank-N player (formatted) |
| `%voideconomy_<currency>_top_<rank>_balance_raw%` | Balance of rank-N player (raw) |
| `%voideconomy_<currency>_top_<rank>_balance_formatted%` | Balance of rank-N player (compact) |

### Compact suffix reference

| Suffix | Name | Value |
|---|---|---|
| K | thousand | 10³ |
| M | million | 10⁶ |
| B | billion | 10⁹ |
| T | trillion | 10¹² |
| Q | quadrillion | 10¹⁵ |
| Qt | quintillion | 10¹⁸ |
| Sx | sextillion | 10²¹ |
| Sp | septillion | 10²⁴ |
| Oc | octillion | 10²⁷ |
| No | nonillion | 10³⁰ |
| Dc | decillion | 10³³ |
| Ud | undecillion | 10³⁶ |
| Dod | duodecillion | 10³⁹ |
| Trd | tredecillion | 10⁴² |
| Qtd | quattuordecillion | 10⁴⁵ |
| Qid | quindecillion | 10⁴⁸ |
| Sxd | sexdecillion | 10⁵¹ |
| Sd | septendecillion | 10⁵⁴ |
| Od | octodecillion | 10⁵⁷ |
| Nd | novemdecillion | 10⁶⁰ |
| Vi | vigintillion | 10⁶³ |

---

## Config Message Placeholders

These placeholders are available in currency message strings:

| Placeholder | Description |
|---|---|
| `{player}` | Target player's name |
| `{symbol}` | Currency symbol (e.g. `$`) |
| `{display-name}` | Currency display name (e.g. `Money`) |
| `{currency}` | Currency ID (e.g. `money`) |
| `{amount}` | The amount involved, formatted with decimals (e.g. `1,500,000.00`) |
| `{formatted}` | The amount in compact notation (e.g. `1.5M`) |
| `{new_balance}` | Player's balance after the action, formatted with decimals |
| `{new_balance_formatted}` | Player's balance after the action in compact notation |
| `{default}` | Currency's default balance, formatted with decimals |
| `{limit}` | Top list size (`top-header` only) |
| `{rank}` | Player rank number (`top-entry` only) |

---

## Adding a Custom Currency

Add a new entry under `currencies` in `config.yml` and run `/voideconomy reload` (or restart):

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
      balance-self: "&aYour crystals: &d{amount}{symbol} &7({formatted})"
      balance-other: "&a{player}'s crystals: &d{amount}{symbol} &7({formatted})"
      given: "&aGave &d{amount}{symbol} &7({formatted})&a to &6{player}&a."
      taken: "&aTook &d{amount}{symbol} &7({formatted})&a from &6{player}&a."
      set: "&aSet &6{player}&a's crystals to &d{amount}{symbol} &7({formatted})&a."
      reset: "&aReset &6{player}&a's crystals to the default (&d{default}{symbol}&a)."
      cleared: "&aCleared &6{player}&a's crystals."
      received-give: "&aAn admin gave you &d{amount}{symbol} &7({formatted})&a."
      received-take: "&cAn admin took &d{amount}{symbol} &7({formatted})&c from you."
      top-header: "&d&l--- Top {limit} {display-name} ---"
      top-entry: "&d#{rank} &e{player} &7- &d{amount}{symbol} &7({formatted})"
      top-footer: "&d&m                              "
      top-empty: "&cNo data found yet."
```

This automatically registers `/crystals` and `/crystalstop`.

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

// Check if a currency exists
if (api.hasCurrency("crystals")) { ... }

// Get all currencies
for (Currency c : api.getCurrencies()) {
    System.out.println(c.getId() + " -> " + c.getSymbol());
}
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
