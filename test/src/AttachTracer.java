

import com.sun.jdi.*;
import com.sun.jdi.connect.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import java.util.*;

public class AttachTracer {
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

        // 3. Attach to target JVM
        VirtualMachine vm = ac.attach(cfg);
        System.out.println("✅ Connected to target JVM");

        EventRequestManager erm = vm.eventRequestManager();
        TraceAnalyzer analyzer = new TraceAnalyzer();

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
            long start = System.nanoTime();

            for (Event e : set) {
                if (e instanceof StepEvent se) {
                    try {
                        Location loc = se.location();
                        String className = loc.declaringType().name();
                        String source = loc.sourceName();
                        int line = loc.lineNumber();

                        analyzer.recordClass(className);
                        analyzer.recordLine(className, line);
                        analyzer.startTimer(className + ":" + line);

                        System.out.printf("%s:%d%n", source, line);

                        analyzer.stopTimer(className + ":" + line);
                    } catch (AbsentInformationException ignored) {}
                    // Re-enable step for next line
                    erm.deleteEventRequest(e.request());
                    StepRequest sr = erm.createStepRequest(
                            se.thread(), StepRequest.STEP_LINE, StepRequest.STEP_INTO);
                    sr.addCountFilter(1);
                    sr.enable();
                } else if (e instanceof VMDisconnectEvent) {
                    System.out.println("🔚 Target JVM disconnected");
                    analyzer.printResults();
                    return;
                }
            }

            vm.resume();
            long end = System.nanoTime();
            analyzer.addLoopTime(end - start);
        }
    }
}
