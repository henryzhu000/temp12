import com.sun.jdi.*;
import com.sun.jdi.connect.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import java.util.*;

public class AttachTracer2 {
    public static void main(String[] args) throws Exception {
        // 1. Locate the standard socket attach connector
        AttachingConnector ac = Bootstrap.virtualMachineManager()
                .attachingConnectors()
                .stream()
                .filter(c -> c.name().equals("com.sun.jdi.SocketAttach"))
                .findFirst().orElseThrow();

        // 2. Configure host/port
        Map<String, Connector.Argument> cfg = ac.defaultArguments();
        cfg.get("hostname").setValue("localhost");
        cfg.get("port").setValue("5005");

        // 3. Attach to the target JVM
        VirtualMachine vm = ac.attach(cfg);
        System.out.println("✅ Connected to target JVM");

        EventRequestManager erm = vm.eventRequestManager();

        // 4. Enable line-by-line stepping on all threads
        for (ThreadReference t : vm.allThreads()) {
            try {
                StepRequest sr = erm.createStepRequest(
                        t, StepRequest.STEP_LINE, StepRequest.STEP_INTO);
                sr.addCountFilter(1);
                sr.enable();
            } catch (Exception ignored) {}
        }

        // 5. Event loop
        for (EventQueue q = vm.eventQueue(); ; ) {
            EventSet set = q.remove();
            for (Event e : set) {
                if (e instanceof StepEvent se) {
                    try {
                        Location loc = se.location();
                        System.out.printf("%s:%d%n",
                                loc.sourceName(), loc.lineNumber());
                    } catch (AbsentInformationException ignored) {}
                    // Re-enable step for next line
                    erm.deleteEventRequest(e.request());
                    StepRequest sr = erm.createStepRequest(
                            se.thread(), StepRequest.STEP_LINE, StepRequest.STEP_INTO);
                    sr.addCountFilter(1);
                    sr.enable();
                } else if (e instanceof VMDisconnectEvent) {
                    System.out.println("🔚 Target JVM disconnected");
                    return;
                }
            }
            vm.resume();
        }
    }
}
