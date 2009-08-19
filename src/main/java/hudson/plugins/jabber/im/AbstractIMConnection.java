package hudson.plugins.jabber.im;

import hudson.util.TimeUnit2;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public abstract class AbstractIMConnection implements IMConnection {

    private static final Logger LOGGER = Logger.getLogger(AbstractIMConnection.class.getName());
    
    private final Object connectionLock = new Object();
    
    private final Connector connector = new Connector();
    
    private final Thread connectorThread = new Thread(connector, "IM-ConnectorThread");
    
    
    protected AbstractIMConnection() {
        connectorThread.start();
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
    }
    
    protected abstract void close0();
    
    private final class Connector implements Runnable {

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
