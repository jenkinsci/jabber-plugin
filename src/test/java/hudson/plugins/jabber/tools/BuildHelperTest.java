package hudson.plugins.jabber.tools;

import hudson.model.AbstractBuild;
import hudson.model.FreeStyleProject;

import java.util.concurrent.TimeUnit;

import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.hudson.test.HudsonTestCase;

public class BuildHelperTest extends HudsonTestCase {
    
    public void testIsFix() throws Exception {
        {
            FreeStyleProject project = createFreeStyleProject();
            project.getBuildersList().add(new FailureBuilder());
            AbstractBuild<?, ?> build = project.scheduleBuild2(0).get(10, TimeUnit.SECONDS);
            assertFalse(BuildHelper.isFix(build));
            
            project.getBuildersList().remove(FailureBuilder.class);
            build = project.scheduleBuild2(0).get(10, TimeUnit.SECONDS);
            assertTrue(BuildHelper.isFix(build));
        }
        
        {
            FreeStyleProject project = createFreeStyleProject();
            AbstractBuild<?, ?> build = project.scheduleBuild2(0).get(10, TimeUnit.SECONDS);
            assertFalse(BuildHelper.isFix(build));
        }
        
        // test with aborted build
        // TODO doesn't work yet
//        {
//            FreeStyleProject project = createFreeStyleProject();
//            // a successful
//            project.scheduleBuild2(0).get(10, TimeUnit.SECONDS);
//            
//            final CountDownLatch latch = new CountDownLatch(1);
//            RunListener<AbstractBuild> runListener = new RunListener<AbstractBuild>(AbstractBuild.class) {
//                @Override
//                public void onCompleted(AbstractBuild r, TaskListener listener) {
//                    assertTrue(r.getResult() == Result.ABORTED);
//                    latch.countDown();
//                }
//                
//                @Override
//                public void onStarted(AbstractBuild r, TaskListener listener) {
//                    r.getExecutor().interrupt();
//                }
//            };
//            
//            runListener.register();
//            
//            // an aborted build
//            project.getBuildersList().add(new SleepBuilder(5000));
//            project.scheduleBuild2(0);
//            latch.await();
//
//            project.getBuildersList().remove(SleepBuilder.class);
//            
//            // a successful build
//            AbstractBuild<?, ?> build = project.scheduleBuild2(0).get(10, TimeUnit.SECONDS);
//            assertTrue(build.getResult() == Result.SUCCESS);
//            assertFalse(BuildHelper.isFix(build));
//        }
        
    }
    
    public void testGetResultDescription() throws Exception {
        {
            FreeStyleProject project = createFreeStyleProject();
            AbstractBuild<?, ?> build = project.scheduleBuild2(0).get(10, TimeUnit.SECONDS);
            assertEquals("SUCCESS", BuildHelper.getResultDescription(build));
            
            project.getBuildersList().add(new FailureBuilder());
            build = project.scheduleBuild2(0).get(10, TimeUnit.SECONDS);
            assertEquals("FAILURE", BuildHelper.getResultDescription(build));
            
            build = project.scheduleBuild2(0).get(10, TimeUnit.SECONDS);
            assertEquals("STILL FAILING", BuildHelper.getResultDescription(build));
            
            project.getBuildersList().remove(FailureBuilder.class);
            build = project.scheduleBuild2(0).get(10, TimeUnit.SECONDS);
            assertEquals("FIXED", BuildHelper.getResultDescription(build));
            
//            project.getBuildersList().add(new UnstableBuilder());
//            build = project.scheduleBuild2(0).get(10, TimeUnit.SECONDS);
//            assertEquals("UNSTABLE", BuildHelper.getResultDescription(build));
//            
//            build = project.scheduleBuild2(0).get(10, TimeUnit.SECONDS);
//            assertEquals("STILL UNSTABLE", BuildHelper.getResultDescription(build));
//            
//            project.getBuildersList().remove(UnstableBuilder.class);
//            build = project.scheduleBuild2(0).get(10, TimeUnit.SECONDS);
//            assertEquals("FIXED", BuildHelper.getResultDescription(build));
        }
        
        // TODO: test some more
    }
}
