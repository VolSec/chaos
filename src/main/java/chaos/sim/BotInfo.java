package chaos.sim;

import java.util.HashMap;
import java.util.Set;


public class BotInfo {

    private Set<Integer> botSet;
    private HashMap<Integer, Integer> botSetToBotIPs;

    public BotInfo(Set<Integer> botSet, HashMap<Integer, Integer> botSetToBotIPs) {
        this.botSet = botSet;
        this.botSetToBotIPs = botSetToBotIPs;
    }

    public Set<Integer> getBotSet() {
        return botSet;
    }

    public void setBotSet(Set<Integer> botSet) {
        this.botSet = botSet;
    }

    public HashMap<Integer, Integer> getBotSetToBotIPs() {
        return botSetToBotIPs;
    }

    public void setBotSetToBotIPs(HashMap<Integer, Integer> botSetToBotIPs) {
        this.botSetToBotIPs = botSetToBotIPs;
    }
}
