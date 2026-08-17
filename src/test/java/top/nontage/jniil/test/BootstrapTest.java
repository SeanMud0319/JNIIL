package top.nontage.jniil.test;

import org.junit.jupiter.api.Test;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import top.nontage.jniil.agent.JNIILBootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

public class BootstrapTest {

    @Test
    void install() {
        JNIILBootstrap.install(JNIILBootstrap.MODE.NATIVE);

        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(MonitorTest.class))
                .build();

        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        launcher.execute(request, listener);

        TestExecutionSummary summary = listener.getSummary();
        System.out.println("========================================");
        System.out.println("Tests found: " + summary.getTestsFoundCount());
        System.out.println("Tests succeeded: " + summary.getTestsSucceededCount());
        System.out.println("Tests failed: " + summary.getTestsFailedCount());
        System.out.println("========================================");

        summary.getFailures().forEach(failure -> {
            System.err.println("FAILED: " + failure.getTestIdentifier().getDisplayName());
            failure.getException().printStackTrace();
        });

        assertEquals(0, summary.getTestsFailedCount(), "MonitorTest 有測試失敗");
    }
}