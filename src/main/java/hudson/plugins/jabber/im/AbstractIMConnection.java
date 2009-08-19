package hudson.plugins.jabber.im;

import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Hudson;
import hudson.util.TimeUnit2;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public abstract class AbstractIMConnection implements IMConnection {

    private static final Logger LOGGER = Logger.getLogger(AbstractIMConnection.class.getName());
    
    private final Object connectionLock = new Object();
    
    private final ConnectorRunnable connector = new ConnectorRunnable();
    
    private final Thread connectorThread = new Thread(connector, "IM-ConnectorThread");

    private final Thread statusUpdater;
    
    
    protected AbstractIMConnection() {
        connectorThread.start();
        
        Runnable updater = new UpdaterRunnable();
        this.statusUpdater = new Thread(updater, "IM-StatusUpdater");
        statusUpdater.start();
    }
    
    protected final Object getLock() {
        return this.connectionLock;
    }
    
    /**
     * Starts an asynchronous reconnection attempt
     */
    protected void tryReconnect() {
        this.connector.semaphore.release();
    }
    
    protected abstract boolean isConnected();
    
    protected abstract boolean connect();
    
    public final void close() {
        this.connectorThread.interrupt();
        this.statusUpdater.interrupt();
    }
    
    protected abstract void close0();
    
    private final class UpdaterRunnable implements Runnable {
        public void run() {
            try {
                // sleep initially to give Hudson some time to startup
                TimeUnit.MINUTES.sleep(1);
                
                while (true) {
                    Computer compi = Hudson.getInstance().toComputer();
                    if (compi != null) {
                        try {
                            if (compi.isIdle()) {
                                setPresence(IMPresence.AVAILABLE, "Yawn, I'm so tired. Don't you have some work for me?");
                            } else if (isBusy(compi)) {
                                setPresence(IMPresence.DND, 
                                        "Please give me some rest! All " + compi.getNumExecutors() + " executors are busy, "
                                        + Hudson.getInstance().getQueue().getItems().length + " items in queue");
                            } else {
                                setPresence(IMPresence.OCCUPIED,
                                        "Working. " + getBusyExecutors(compi) + " out of " + compi.getNumExecutors() +
                                        " are busy.");
                            }
                        } catch (IMException e) {
                            // ignore
                        }
                    } else {
                        LOGGER.warning("No Hudson main computer?");
                    }
                    TimeUnit.MINUTES.sleep(2);
                }
            } catch (InterruptedException e) {
                // shutdown
            }
        }

        private int getBusyExecutors(Computer compi) {
            int i = 0;
            for (Executor executor : compi.getExecutors()) {
                if (executor.isBusy()) {
                    i++;
                }
            }
            return i;
        }

        private boolean isBusy(Computer compi) {
            for (Executor executor : compi.getExecutors()) {
                if (executor.isIdle()) {
                    return false;
                }
            }
            return true;
        }
    }

    private final class ConnectorRunnable implements Runnable {

        private final Semaphore semaphore = new Semaphore(0);
        
        public void run() {
            try {
                while (true) {
                    this.semaphore.acquire();
                    this.semaphore.drainPermits();
                    
                    LOGGER.info("Trying to reconnect");
                    // wait a little bit in case the XMPP server/network has just a 'hickup'
                    TimeUnit.SECONDS.sleep(30);
                    
                    boolean success = false;
                    int timeout = 1;
                    while (!success) {
                        synchronized (getLock()) {
                            if (!isConnected()) {
                                success = connect();
                            } else {
                                success = true;
                            }
                        }
                        
                        // make sure to release connectionLock before sleeping!
                        if(!success) {
                            LOGGER.info("Reconnect failed. Next connection attempt in " + timeout + " minutes");
                            TimeUnit2.MINUTES.sleep(timeout);
                            // exponentially increase timeout
                            timeout = timeout * 2;
                        }
                    }
                }
            } catch (InterruptedException e) {
                LOGGER.info("Connect thread interrupted");
                // just bail out
            }
        }
    }

}
