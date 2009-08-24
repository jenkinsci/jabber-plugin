package hudson.plugins.jabber.im;

import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Hudson;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.util.TimeUnit2;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;

public abstract class AbstractIMConnection implements IMConnection {

    private static final Logger LOGGER = Logger.getLogger(AbstractIMConnection.class.getName());
    
    private final Object connectionLock = new Object();
    
    private final ConnectorRunnable connector = new ConnectorRunnable();
    
    private volatile Thread connectorThread;

    private final IMPublisherDescriptor desc;

    private final BusyListener busyListener;

    protected AbstractIMConnection(IMPublisherDescriptor desc) {
        this.desc = desc;
        // TODO: cannot use @Extension as BusyListener must be non-static
        this.busyListener = new BusyListener();
    }
    
    public final boolean connect() {
        boolean result = connect0();
        if (StringUtils.isNotBlank(desc.getHost())) {
            this.busyListener.register();
            connectorThread = new Thread(connector, "IM-ConnectorThread");
            connectorThread.start();
        }
        return result;
    }
    
    protected abstract boolean connect0();
    
    protected final Object getLock() {
        return this.connectionLock;
    }
    
    /**
     * Starts an asynchronous reconnection attempt
     */
    protected void tryReconnect() {
        this.connector.semaphore.release();
    }
    
    protected void updateIMStatus() {
        updateIMStatus(null);
    }
    
    private void updateIMStatus(Executor exec) {
        int totalExecutors = getTotalExecutors();
        int busyExecutors = getBusyExecutors(exec);
        
        try {
            if (busyExecutors == 0) {
                setPresence(IMPresence.AVAILABLE, "Yawn, I'm so bored. Don't you have some work for me?");
            } else if (busyExecutors == totalExecutors) {
                setPresence(IMPresence.DND, 
                        "Please give me some rest! All " + totalExecutors + " executors are busy, "
                        + Hudson.getInstance().getQueue().getItems().length + " jobs in queue.");
            } else {
                String msg = "Working: " + busyExecutors + " out of " + totalExecutors +
                    " executors are busy.";
                int queueItems = Hudson.getInstance().getQueue().getItems().length;
                if (queueItems > 0) {
                    msg += " " + queueItems + " jobs in queue.";
                }
                setPresence(IMPresence.OCCUPIED, msg);
            }
        } catch (IMException e) {
            // ignore
        }
    }
    
    private int getBusyExecutors(Executor exec) {
        int busyExecutors = 0;
        Computer[] computers = Hudson.getInstance().getComputers();
        for (Computer compi : computers) {
            
            for (Executor executor : compi.getExecutors()) {
                if (executor.isBusy()) {
                    if (isNotEqual(executor, exec)) {
                        busyExecutors++;
                    }
                }
            }
        }
        
        return busyExecutors;
    }
    
    private int getTotalExecutors() {
        int totalExecutors = 0;
        Computer[] computers = Hudson.getInstance().getComputers();
        for (Computer compi : computers) {
            totalExecutors += compi.getNumExecutors();
        }
        return totalExecutors;
    }
        
    private static boolean isNotEqual(Executor executor, Executor exec) {
        if (exec == null) {
            return true;
        }
        return !(executor.getOwner().equals(exec.getOwner())
            && executor.getNumber() == exec.getNumber());
    }
    
    protected abstract boolean isConnected();
    
    public final void close() {
        this.connectorThread.interrupt();
        this.connectorThread = null;
        this.busyListener.unregister();
    }
    
    protected abstract void close0();
    
    @SuppressWarnings("unchecked")
    public final class BusyListener extends RunListener<Run> {

        public BusyListener() {
            super(Run.class);
            LOGGER.info("Executor busy listener created");
        }

        @Override
        public void onCompleted(Run r, TaskListener listener) {
            // the executor of 'r' is still busy, we have to take that into account!
            updateIMStatus(r.getExecutor());
        }

        @Override
        public void onDeleted(Run r) {
            updateIMStatus(null);
        }

        @Override
        public void onStarted(Run r, TaskListener listener) {
            updateIMStatus(null);
        }
    }
    
    private final class ConnectorRunnable implements Runnable {

        private final Semaphore semaphore = new Semaphore(0);
        
        public void run() {
            try {
                while (true) {
                    this.semaphore.acquire();
                    
                    LOGGER.info("Trying to reconnect");
                    // wait a little bit in case the XMPP server/network has just a 'hickup'
                    TimeUnit.SECONDS.sleep(30);
                    
                    boolean success = false;
                    int timeout = 1;
                    while (!success) {
                        synchronized (getLock()) {
                            if (!isConnected()) {
                                close();
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
                        } else {
                            // remove any permits which came in in the mean time
                            this.semaphore.drainPermits();
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
