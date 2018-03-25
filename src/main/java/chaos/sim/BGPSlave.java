package chaos.sim;

import chaos.topo.AS;

import java.util.Set;

public class BGPSlave implements Runnable {

    private BGPMaster workSource;

    public BGPSlave(BGPMaster master) {
        this.workSource = master;
    }

    @Override
    public void run() {
        try {
            while (true) {

                /*
                 * Fetch work from master
                 */
                Set<AS> workSet = this.workSource.getWork();
                BGPMaster.WorkType jobToDo = this.workSource.getCurentWorkType();

                /*
                 * there is work to do, please do it
                 */
                for (AS tAS : workSet) {
                    if (jobToDo == BGPMaster.WorkType.Process) {
                        tAS.handleAllAdvertisements();
                    } else if (jobToDo == BGPMaster.WorkType.Adv) {
                        tAS.mraiExpire();
                    }
                }

                this.workSource.reportWorkDone();
            }
        } catch (InterruptedException e) {
            /*
             * Done w/ work, nothing to report here, just die
             */
        }

    }

}
