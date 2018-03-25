#!/usr/bin/env python3


import argparse
import datetime
import subprocess
import smtplib
import os
import sys


os.mkdir("logs")
os.mkdir("serial")

expMetaLogFile = "logs/expMetaLog_{}".format(datetime.datetime.now().strftime("%Y%m%d-%H%M%S"))

# Embargo Options
modeList = ["ORDERED", "GLOBAL"]
optionalsList = [["--coverageOrdering", "--nopathlog"], ["--coverageOrdering", "--nopathlog"]]
stratList = ["LOCALPREF", "PATHLEN", "TIEBREAK", "LEGACY"]
reverseList = ["NONE", "LYING"]
defectionOptions = ["--coverageOrdering", "--defection", "--dcountstart", "10", "--dcountend", "70", "--dcountstep", "20"]

def noticeEMail(starttime, usr, psw, fromaddr, toaddr, subject, jobmsg):
    """
    Sends an email message through GMail once the script is completed.
    Developed to be used with AWS so that instances can be terminated
    once a long job is done. Only works for those with GMail accounts.

    starttime : a datetime() object for when to start run time clock
    usr : the GMail username, as a string
    psw : the GMail password, as a string

    fromaddr : the email address the message will be from, as a string

    toaddr : a email address, or a list of addresses, to send the
             message to
    """

    # Calculate run time
    runtime=datetime.datetime.now() - starttime

    # Initialize SMTP server
    server=smtplib.SMTP('smtp.gmail.com:587')
    server.starttls()
    server.login(usr, psw)

    # Send email
    senddate=datetime.datetime.strftime(datetime.datetime.now(), '%Y-%m-%d')
    subject=subject
    m="Date: %s\r\nFrom: %s\r\nTo: %s\r\nSubject: %s\r\nX-Mailer: My-Mail\r\n\r\n" % (senddate, fromaddr, toaddr, subject)
    msg='{}\nJob runtime: {}'.format(jobmsg, str(runtime))

    server.sendmail(fromaddr, toaddr, m+msg)
    server.quit()


################################################
# Embargo Run Commands
################################################


def runFull(wardenFile):
    for tRev in reverseList:
        runDeployExplore(wardenFile, tRev)


def runRevExplore(wardenFile):
    stratList = ["LOCALPREF", "PATHLEN", "LEGACY"]
    runDeployExplore(wardenFile, "LYING")


def runDeployExplore(wardenFile, revMode):
    for i in range(len(modeList)):
        runStratExplore(modeList[i], wardenFile, optionalsList[i], revMode)


def runStratExplore(currMode, wardenFile, options, revMode):
    for tStrat in stratList:
        runEmbargo(currMode, wardenFile, tStrat, revMode, options)


def runDefection(wardenFile):
    stratList = ["LOCALPREF", "PATHLEN", "LEGACY"]
    for tStrat in stratList:
        runEmbargo("ORDERED", wardenFile, tStrat, "LYING", defectionOptions)
    runEmbargo("ORDERED", wardenFile, "TIEBREAK", "NONE", defectionOptions)


def runPerformanceTest(wardenFile):
    perfOptions = ["--coverageOrdering", "--dcountstart", "10", "--dcountend", "10", "--dcountstep", "10", "--defection"]
    runEmbargo("ORDERED", wardenFile, "LOCALPREF", "NONE", perfOptions)


def runHonest(wardenFile, depFile):
    wardenFP = open(wardenFile, "r")
    for line in wardenFP:
        if len(line.strip()) > 0:
            print("on " + line)
            outFP = open("honestWardenTemp.txt", "w")
            outFP.write(line)
            outFP.close()
            runEmbargo("HONESTEXPLORE", "honestWardenTemp.txt", "NONE", "HONEST", ["--deployers", depFile])
            print(" done " + line)
    wardenFP.close()


def runVs(wardenFile, depFile):
    options = ["--deployers", args.deployer, "--nopathlog"]
    for i in range(1, 2):
        if i % 2 == 1:
            options = ["--deployers", args.deployer, "--nopathlog", "--defection"]
        for tStrat in stratList:
            tRev = None
            if tStrat == "TIEBREAK":
                tRev = "NONE"
            else:
                tRev = "LYING"
            runEmbargo("VS", wardenFile, tStrat, tRev, options)


def runTor(wardenFile):
    options = ["--nopathlog", "--deployers"]
    torFilePath = "cash-nightwing/tor-asn/"
    depFiles = os.listdir(torFilePath)
    for tFile in depFiles:
        if "-bw" in tFile:
            continue
        for tStrat in stratList:
            tRev = None
            if tStrat == "TIEBREAK":
                tRev = "NONE"
            else:
                tRev = "LYING"
            runEmbargo("VS", wardenFile, tStrat, tRev, options + [torFilePath + tFile])


def runEmbargo(currSim, currWarden, currStrat, currRev, optArgs):
    global jarFile
    global configFile

    baseArgs = ["java", "-Xmx200G", '-XX:OnOutOfMemoryError="kill -9 %p"',
                "-XX:+HeapDumpOnOutOfMemoryError",
                "-XX:+UseConcMarkSweepGC", "-jar", jarFile,
                "-Dcom.sun.management.jmxremote=true",
                "-Dcom.sun.management.jmxremote.port=21000",
                "-Dcom.sun.management.jmxremote.authenticate=false",
                "-Dcom.sun.management.jmxremote.ssl=false",
                "-Dcom.sun.management.jmxremote.password.file=config/jmxremote.password",
                "-c", configFile, "-m", "EMBARGO",
                "-s", currSim, "-wf", currWarden, "-ws", currStrat, "-rs", currRev]
    expProc = subprocess.Popen(baseArgs + optArgs)
    expProc.wait()


################################################
# Cloud Run Commands
################################################


def runPathDiversity(numRuns, logId, currRev):
    runCloud("PATH_DIVERSITY", numRuns, logId, currRev)


def runPathIntersection(numRuns, logId, currRev):
    runCloud("PATH_INTERSECTION", numRuns, logId, currRev)


def runCloud(currSim, numRuns, logId, currRev):
    global jarFile
    global configFile

    print("Running Cloud!")
    baseArgs = ["java", "-Xmx200G", '-XX:OnOutOfMemoryError="kill -9 %p"',
                "-XX:+HeapDumpOnOutOfMemoryError",
                "-Dcom.sun.management.jmxremote=true",
                "-Dcom.sun.management.jmxremote.port=21000",
                "-Dcom.sun.management.jmxremote.authenticate=false",
                "-Dcom.sun.management.jmxremote.ssl=false",
                "-Dcom.sun.management.jmxremote.password.file=config/jmxremote.password",
                "-XX:+UseConcMarkSweepGC", "-jar", jarFile, "-c", configFile,
                "-m", "CLOUD",
                "-s",
                currSim,
                "--numRuns", str(numRuns)]

    if logId != "":
        baseArgs += ["--logId", logId]

    args = baseArgs
    expProc = subprocess.Popen(args)
    expProc.wait()


################################################
# Nyx Run Commands
################################################


def runSingleCritical(withBots, numRuns, logId, currRev):
    runNyx("FULL_REACTIVE", withBots, numRuns, logId, currRev)


def runNyx(currSim, withBots, numRuns, logId, currRev):
    global jarFile
    global configFile

    print("Running Nyx!")

    baseArgs = ["java", "-Xmx200G", '-XX:OnOutOfMemoryError="kill -9 %p"', "-XX:+HeapDumpOnOutOfMemoryError",
                "-Dcom.sun.management.jmxremote=true",
                "-Dcom.sun.management.jmxremote.port=21000",
                "-Dcom.sun.management.jmxremote.authenticate=false",
                "-Dcom.sun.management.jmxremote.ssl=false",
                "-Dcom.sun.management.jmxremote.password.file=config/jmxremote.password",
                "-XX:+UseConcMarkSweepGC", "-jar", jarFile, "-c", configFile, "-m", "NYX",
                "-s",
                currSim,
                "--numRuns", str(numRuns)]

    if logId != "":
        baseArgs += ["--logId", logId]

    if withBots:
        baseArgs.append("--withBots")

    args = baseArgs
    expProc = subprocess.Popen(args)
    expProc.wait()


if __name__ == "__main__":
    global jarFile
    global configFile

    subprocess.run(["mvn", "package"])

    parser = argparse.ArgumentParser()
    parser.add_argument("--numRuns", help="Num runs for the test", required=False, default=5)
    parser.add_argument("--withBots", action="store_true", default=False, required=False, help="run with bots")
    parser.add_argument("--logId", type=str, required=False, default="", help="Identifier for log file")
    parser.add_argument("--warden", help ="Warden file", default=None, required=False)
    parser.add_argument("--deployer", help ="Deployer file", required=False)
    parser.add_argument("--jarFile", type=str, required=True, dest="jarFile", help="JAR file to run.")
    parser.add_argument("--config", type=str, required=False, dest="configFile",
                        default="config/default_config.yml", help="Config file for Chaos")

    evalSim = parser.add_mutually_exclusive_group(required=True)
    evalSim.add_argument("--rev", action ="store_true", help ="reverse poison test")
    evalSim.add_argument("--strat", action ="store_true", help ="strat test")
    evalSim.add_argument("--defection", action="store_true", help="defection run")
    evalSim.add_argument("--perf", action="store_true", help="performance test")
    evalSim.add_argument("--full", action="store_true", help="full test")
    evalSim.add_argument("--honest", action="store_true", help="honest rev poison")
    evalSim.add_argument("--vs", action="store_true", help="run vs mode")
    evalSim.add_argument("--tor", action="store_true", help="tor experiment mode")
    evalSim.add_argument("--reactive", action="store_true", help="full single critical nyx test")
    evalSim.add_argument("--diversity", action="store_true", help="path diversity test")
    evalSim.add_argument("--intersection", action="store_true", help="path intersection test")

    args = parser.parse_args()

    jarFile = args.jarFile
    configFile = args.configFile

    optArgs = []

    # Fill these in with the appropriate info...
    usr=''
    psw=''
    fromaddr=''
    toaddr=''
    subject = 'Chaos Run {} Complete'.format(str(args.logId))
    msg = '{} Run for ID {} for Chaos is done.'.format(int(args.numRuns), str(args.logId))
    starttime=datetime.datetime.now()

    if args.reactive:
        runSingleCritical(args.withBots, args.numRuns, args.logId, "LYING")
    elif args.diversity:
        runPathDiversity(args.numRuns, args.logId, "LYING")
    elif args.intersection:
        runPathIntersection(args.numRuns, args.logId, "LYING")
    else:
        if args.warden is None:
            print('Warden file must be provided for Embargo evaluations!')
            sys.exit(1)
        if args.strat:
            runDeployExplore(args.warden, "NONE")
        elif args.rev:
            runRevExplore(args.warden)
        elif args.defection:
            runDefection(args.warden)
        elif args.perf:
            runPerformanceTest(args.warden)
        elif args.full:
            runFull(args.warden)
        elif args.honest:
            runHonest(args.warden, args.deployer)
        elif args.vs:
            runVs(args.warden, args.deployer)
        elif args.tor:
            runTor(args.warden)

    # Send notification email
    # noticeEMail(starttime, usr, psw, fromaddr, toaddr, subject, msg)

