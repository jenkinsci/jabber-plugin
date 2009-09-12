package hudson.plugins.jabber.im;

import hudson.util.TimeUnit2;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;

public abstract class AbstractIMConnection implements IMConnection {

    private static final Logger LOGGER = Logger.getLogger(AbstractIMConnection.class.getName());
    
    private final Lock connectionLock = new ReentrantLock();
    
    private final ConnectorRunnable connector = new ConnectorRunnable();
    
    private volatile Thread connectorThread;

    private final IMPublisherDescriptor desc;

    protected AbstractIMConnection(IMPublisherDescriptor desc) {
        this.desc = desc;
    }
    
    @Override
    public final void init() {
    	if (StringUtils.isNotBlank(desc.getHost())) {
    		// TODO: busyListener and especially reconnection thread
    		// don't really belong here. They should be moved to
    		// ConnectionProvider o.s.l.t.
            connectorThread = new Thread(connector, "IM-ConnectorThread");
            connectorThread.start();
            tryReconnect();
        }
    }
    
    @Override
    public void shutdown() {
    	if (this.connectorThread != null) {
    		this.connectorThread.interrupt();
    	}
    }
    
    protected final void lock() {
        this.connectionLock.lock();
    }
    
    protected final boolean tryLock(long time, TimeUnit timeUnit) throws InterruptedException {
        return this.connectionLock.tryLock(time, timeUnit);
    }

    protected final void unlock() {
        this.connectionLock.unlock();
    }
    
    /**
     * Starts an asynchronous reconnection attempt
     */
    protected void tryReconnect() {
        this.connector.semaphore.release();
    }
    
    private final class ConnectorRunnable implements Runnable {

        private final Semaphore semaphore = new Semaphore(0);
        
        private boolean firstConnect = true;
        
        public void run() {
            try {
                while (true) {
                    this.semaphore.acquire();
                    
                    if (!firstConnect) {
                    	// wait a little bit in case the XMPP server/network has just a 'hickup'
                    	TimeUnit.SECONDS.sleep(30);
                    	LOGGER.info("Trying to reconnect");
                    } else {
                    	firstConnect = false;
                    	LOGGER.info("Trying to connect");
                    }
                    
                    boolean success = false;
                    int timeout = 1;
                    while (!success) {
                        lock();
                        try {
                            if (!isConnected()) {
                                close();
                                success = connect();
                            } else {
                                success = true;
                            }
                        } finally {
                            unlock();
                        }
                        
                        // make sure to release the lock before sleeping!
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
