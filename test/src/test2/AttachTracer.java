package test2;

import com.sun.jdi.*;
import com.sun.jdi.connect.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import java.io.*;
import java.util.*;

public class AttachTracer {
    public static void main(String[] args) throws Exception {
        File logFile = new File("trace.log");
        try (BufferedWriter out = new BufferedWriter(new FileWriter(logFile, true))) {

            AttachingConnector ac = Bootstrap.virtualMachineManager()
                    .attachingConnectors()
                    .stream()
                    .filter(c -> c.name().equals("com.sun.jdi.SocketAttach"))
                    .findFirst().orElseThrow();

            Map<String, Connector.Argument> cfg = ac.defaultArguments();
            cfg.get("hostname").setValue("localhost");
            cfg.get("port").setValue("5005");

            VirtualMachine vm = ac.attach(cfg);
            System.out.println("✅ Connected to target JVM");
            out.write("{\"event\":\"connected\"}\n");
            out.flush();

            EventRequestManager erm = vm.eventRequestManager();

            for (ThreadReference t : vm.allThreads()) {
                try {
                    StepRequest sr = erm.createStepRequest(
                            t, StepRequest.STEP_LINE, StepRequest.STEP_INTO);
                    sr.addCountFilter(1);
                    sr.enable();
                } catch (Exception ignored) {}
            }

            for (EventQueue q = vm.eventQueue(); ; ) {
                EventSet set = q.remove();
                for (Event e : set) {
                    if (e instanceof StepEvent se) {
                        long start = System.nanoTime();
                        try {
                            Location loc = se.location();
                            String className = loc.declaringType().name();
                            String source = loc.sourceName();
                            int line = loc.lineNumber();

                            System.out.printf("%s:%d%n", source, line);

                            long end = System.nanoTime();
                            double durationMs = (end - start) / 1_000_000.0;

                            out.write(String.format(Locale.US,
                                    "{\"class\":\"%s\",\"source\":\"%s\",\"line\":%d,\"time\":%.3f}\n",
                                    className, source, line, durationMs));
                            out.flush();
                        } catch (AbsentInformationException ignored) {}
                        erm.deleteEventRequest(e.request());
                        StepRequest sr = erm.createStepRequest(
                                se.thread(), StepRequest.STEP_LINE, StepRequest.STEP_INTO);
                        sr.addCountFilter(1);
                        sr.enable();
                    } else if (e instanceof VMDisconnectEvent) {
                        out.write("{\"event\":\"disconnected\"}\n");
                        out.flush();
                        System.out.println("🔚 Target JVM disconnected");
                        return;
                    }
                }
                vm.resume();
            }
        }
    }
}
