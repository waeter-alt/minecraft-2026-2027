package fr.kodistan.interregne.client;

import fr.kodistan.interregne.client.blocks.BankScreen;
import fr.kodistan.interregne.client.blocks.ComptoirScreen;
import fr.kodistan.interregne.client.blocks.CropChoiceScreen;
import fr.kodistan.interregne.client.blocks.MarketScreen;
import fr.kodistan.interregne.client.blocks.RegionalBankScreen;
import fr.kodistan.interregne.client.blocks.ThroneScreen;
import fr.kodistan.interregne.client.hud.InterregneHud;
import fr.kodistan.interregne.network.CrateStockPayload;
import fr.kodistan.interregne.network.FarmCropsSyncPayload;
import fr.kodistan.interregne.network.WalletSyncPayload;
import fr.kodistan.interregne.network.OpenFarmCropPayload;
import fr.kodistan.interregne.network.InfluenceSyncPayload;
import fr.kodistan.interregne.network.OpenThronePayload;
import fr.kodistan.interregne.network.OpenBankPayload;
import fr.kodistan.interregne.network.OpenRegionalBankPayload;
import fr.kodistan.interregne.network.TreasurySyncPayload;
import fr.kodistan.interregne.network.OpenComptoirPayload;
import fr.kodistan.interregne.network.OpenMarketPayload;

/**
 * Client-side handling of server-to-client payloads. Only ever called on a client, so it may touch client classes.
 */
public final class ClientPayloads
{
    public static void openFarmCrop(final OpenFarmCropPayload payload)
    {
        CropChoiceScreen.openForFarm(payload);
    }

    public static void syncWall(final fr.kodistan.interregne.network.WallSyncPayload payload)
    {
        fr.kodistan.interregne.client.walls.ClientWalls.put(payload);
    }

    public static void syncWarRuins(final fr.kodistan.interregne.network.WarRuinsSyncPayload payload)
    {
        fr.kodistan.interregne.client.war.ClientWarRuins.put(payload);
    }

    public static void syncWallet(final WalletSyncPayload payload)
    {
        InterregneHud.setWallet(payload.bank(), payload.regional());
    }

    public static void syncFarmCrops(final FarmCropsSyncPayload payload)
    {
        ClientFarmCrops.set(payload.crops());
    }

    public static void syncCrateStock(final CrateStockPayload payload)
    {
        ClientCrateStock.set(payload.counts());
    }

    public static void openThrone(final OpenThronePayload payload)
    {
        ThroneScreen.open(payload);
    }

    public static void syncInfluence(final InfluenceSyncPayload payload)
    {
        InterregneHud.setInfluence(payload.influence(), payload.delta());
    }

    private ClientPayloads()
    {
    }

    public static void openBank(final OpenBankPayload payload)
    {
        BankScreen.open(payload);
    }

    public static void openRegionalBank(final OpenRegionalBankPayload payload)
    {
        RegionalBankScreen.open(payload);
    }

    public static void syncTreasury(final TreasurySyncPayload payload)
    {
        ClientTreasury.set(payload.colonyKey(), payload.balance());
    }

    public static void openComptoir(final OpenComptoirPayload payload)
    {
        ComptoirScreen.open(payload);
    }

    public static void openMarket(final OpenMarketPayload payload)
    {
        MarketScreen.open(payload);
    }

    public static void intendanceQuote(final fr.kodistan.interregne.network.IntendanceQuotePayload payload)
    {
        ClientIntendance.set(payload);
    }
}
