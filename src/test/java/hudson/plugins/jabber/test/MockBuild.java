package hudson.plugins.jabber.test;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;

import java.io.IOException;

@SuppressWarnings("unchecked")
public class MockBuild extends AbstractBuild {

    protected MockBuild(AbstractProject job) throws IOException {
        super(job);
    }

    @Override
    public void run() {
        // 
    }
}
