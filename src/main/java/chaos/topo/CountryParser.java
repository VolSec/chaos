package chaos.topo;

import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CountryParser {

    public static void main(String[] args) throws IOException {

        if (args.length != 2) {
            System.out.println("parsing XML usage: ./CountryParser <country name> <abbreviation>");
        }

        BufferedReader inBuff = new BufferedReader(new FileReader("rawCountryData.xml"));
        BufferedWriter outBuff = new BufferedWriter(new FileWriter(args[0] + "-as.txt"));
        Pattern asnPattern = Pattern.compile("<asn>(\\d++)</asn>");

        boolean readRegion = false;
        while (inBuff.ready()) {
            String pollString = inBuff.readLine().trim();
            if (readRegion && pollString.contains("<country country_code=")) {
                break;
            } else if (readRegion) {

                Matcher tempMatcher = asnPattern.matcher(pollString);
                if (tempMatcher.find()) {
                    outBuff.write(tempMatcher.group(1));
                    outBuff.newLine();
                }

                //} else if (pollString.contains("<country country_code=\"" + args[1] + "\" country_name=\"" + args[0] + "\" country_code_is_region=\"0\">")) {
            } else if (pollString.contains("<country country_code=\"" + args[1])) {
                readRegion = true;
            }

        }
        inBuff.close();
        outBuff.close();

        System.out.println(args[0] + " warden parsing finished!");
    }
}
