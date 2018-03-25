package chaos.topo;

import chaos.sim.BotInfo;
import chaos.sim.Chaos;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Class made up of static methods used to build the topology used by the
 * nightwing simulator.
 *
 * @author pendgaft
 */
public class BotParser {

    /**
     * Parse the bot ASN list file and return the resulting list.
     */
    public static BotInfo parseBotASNFile(Chaos sim) {
        Set<Integer> botSet = new HashSet<>();
        HashMap<Integer, Integer> botSetToBotIPs = new HashMap<>();

        int numBotsInActive = 0;
        int numBotsInPruned = 0;

        int numActiveBotAS = 0;
        int numPrunedBotAS = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(sim.getConfig().botASFile))) {
            String line = br.readLine();

            while (line != null) {
                String[] splitLine = line.split(" ");

                try {
                    int lineASN = Integer.parseInt(splitLine[0]);
                    int numBots = Integer.parseInt(splitLine[1]);

                    botSet.add(lineASN);
                    botSetToBotIPs.put(lineASN, numBots);

                    if (sim.getActiveTopology().containsKey(lineASN)) {
                        numActiveBotAS += 1;
                        numBotsInActive += numBots;
                    } else if (sim.getPrunedTopology().containsKey(lineASN)) {
                        numPrunedBotAS += 1;
                        numBotsInPruned += numBots;
                    }

                    line = br.readLine();
                } catch (NumberFormatException e) {
                    line = br.readLine();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }

        sim.logMessage(String.format("Number of Bots in Active Topology: %d", numBotsInActive));
        sim.logMessage(String.format("Number of Bots in Pruned Topology: %d", numBotsInPruned));
        sim.logMessage(String.format("Number of Bot ASs in Active Topology: %d", numActiveBotAS));
        sim.logMessage(String.format("Number of Bot ASs in Pruned Topology: %d", numPrunedBotAS));

        sim.logMessage(String.format("Added %d ASNs to bot set", botSet.size()));

        return new BotInfo(botSet, botSetToBotIPs);
    }
}
