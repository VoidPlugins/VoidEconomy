package com.voidplugins.voideconomy.api.event;

import com.voidplugins.voideconomy.currency.Currency;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Fired whenever a player's balance is changed through the {@link com.voidplugins.voideconomy.api.VoidEconomyAPI}
 * or through an in-game admin command.
 *
 * <p>This event is <b>cancellable</b>. If cancelled, the balance change will not be applied.
 * The {@link #getNewBalance()} value can also be modified before cancelling to clamp/adjust the amount.
 *
 * <p>Example listener:
 * <pre>{@code
 * @EventHandler
 * public void onCurrencyChange(CurrencyChangeEvent event) {
 *     if (event.getCurrency().getId().equals("money") && event.getReason() == Reason.GIVE) {
 *         // Halve any money given to players
 *         event.setNewBalance(event.getOldBalance() + (event.getNewBalance() - event.getOldBalance()) / 2.0);
 *     }
 * }
 * }</pre>
 */
public class CurrencyChangeEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    /** The reason a currency balance changed. */
    public enum Reason {
        /** Balance increased via give/add. */
        GIVE,
        /** Balance decreased via take/remove. */
        REMOVE,
        /** Balance was set to an exact value. */
        SET,
        /** Balance was reset to the currency's default. */
        RESET,
        /** Balance was cleared (set to 0). */
        CLEAR
    }

    private final UUID playerUUID;
    private final String playerName;
    private final Currency currency;
    private final double oldBalance;
    private double newBalance;
    private final Reason reason;
    private boolean cancelled = false;

    public CurrencyChangeEvent(UUID playerUUID, String playerName, Currency currency,
                               double oldBalance, double newBalance, Reason reason) {
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.currency = currency;
        this.oldBalance = oldBalance;
        this.newBalance = newBalance;
        this.reason = reason;
    }

    /** The UUID of the player whose balance changed. */
    public UUID getPlayerUUID() { return playerUUID; }

    /** The name of the player at the time of the change. */
    public String getPlayerName() { return playerName; }

    /** The currency that was changed. */
    public Currency getCurrency() { return currency; }

    /** The balance before the change. */
    public double getOldBalance() { return oldBalance; }

    /**
     * The balance that will be applied after the event.
     * Can be modified by listeners to override the result.
     */
    public double getNewBalance() { return newBalance; }

    /**
     * Override the resulting balance. The value is clamped to [0, max-balance].
     *
     * @param newBalance the desired new balance
     */
    public void setNewBalance(double newBalance) {
        if (newBalance < 0) newBalance = 0;
        if (currency.hasMaxBalance() && newBalance > currency.getMaxBalance()) {
            newBalance = currency.getMaxBalance();
        }
        this.newBalance = newBalance;
    }

    /** The reason this change occurred. */
    public Reason getReason() { return reason; }

    /** The net change in balance (newBalance - oldBalance). */
    public double getDelta() { return newBalance - oldBalance; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
