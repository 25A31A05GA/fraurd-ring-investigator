package com.frauddetect;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Minimal read-mostly REST API over a live simulation (JDK built-in HttpServer, no frameworks).
 *
 * GET  /api/health
 * GET  /api/summary
 * GET  /api/cases            GET /api/cases/{id}
 * POST /api/cases/{id}/status?value=RESOLVED
 * GET  /api/performance
 * GET  /api/farms?threshold=4
 * POST /api/farms/{device}/hang        POST /api/farms/{device}/release
 * GET  /api/gate?device=DEV-3000&threshold=4
 * GET  /api/serial?value=SN123456
 */
public final class ApiServer {
    private final Engine engine;
    private final HttpServer server;
    private final ScheduledExecutorService clock = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "sim-clock"); t.setDaemon(true); return t; });

    public ApiServer(Engine engine, int port, int eventsPerSecond) throws IOException {
        this.engine = engine;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this::route);
        server.setExecutor(Executors.newFixedThreadPool(4));
        clock.scheduleAtFixedRate(() -> engine.tick(eventsPerSecond), 0, 1, TimeUnit.SECONDS);
    }

    public void start() { server.start(); }
    public void stop() { clock.shutdownNow(); server.stop(0); }
    public int port() { return server.getAddress().getPort(); }

    private static Map<String, String> query(HttpExchange x) {
        Map<String, String> q = new HashMap<>();
        String raw = x.getRequestURI().getRawQuery();
        if (raw == null) return q;
        for (String p : raw.split("&")) {
            String[] kv = p.split("=", 2);
            q.put(java.net.URLDecoder.decode(kv[0], StandardCharsets.UTF_8), kv.length > 1 ? java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
        }
        return q;
    }

    private static void send(HttpExchange x, int code, Object body) throws IOException {
        byte[] b = Json.write(body).getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        x.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        x.sendResponseHeaders(code, b.length);
        x.getResponseBody().write(b);
        x.close();
    }

    private Map<String, Object> caseJson(FraudCase c, boolean full) {
        Map<String, Object> m = Json.obj("id", c.id, "title", c.title, "score", c.score, "level", c.level(), "status", c.status,
                "patterns", c.patterns.keySet());
        if (full) {
            List<Object> suspects = new ArrayList<>();
            for (String e : c.entities) if (engine.world.isPerson(e)) {
                Entity en = engine.world.ent.get(e);
                suspects.add(Json.obj("id", e, "name", en.name, "city", en.city, "device", en.deviceId, "accountAgeDays", en.accountAgeDays));
            }
            m.put("why", c.patterns);
            m.put("suspects", suspects);
            m.put("victims", c.victims);
            m.put("notes", c.notes);
            m.put("audit", c.audit);
        }
        return m;
    }

    private void route(HttpExchange x) throws IOException {
        try {
            String path = x.getRequestURI().getPath(), method = x.getRequestMethod();
            Map<String, String> q = query(x);
            int threshold = Integer.parseInt(q.getOrDefault("threshold", "4"));
            synchronized (engine) {
                World w = engine.world;
                if (path.equals("/") || path.equals("/api")) send(x, 200, Json.obj("name", "Fraud Ring Investigator API", "status", "running",
                        "endpoints", List.of("GET /api/health", "GET /api/summary", "GET /api/cases", "GET /api/cases/{id}", "GET /api/performance",
                                "GET /api/farms?threshold=4", "GET /api/gate?device=DEV-3000", "GET /api/serial?value=SN123456")));
                else if (path.equals("/api/health")) send(x, 200, Json.obj("status", "ok", "simulatedSeconds", w.t));
                else if (path.equals("/api/summary")) {
                    int active = 0;
                    for (FraudCase c : engine.book.cases) if (c.status != FraudCase.Status.RESOLVED && c.status != FraudCase.Status.FALSE_POSITIVE) active++;
                    send(x, 200, Json.obj("transactions", w.txCount, "valueProcessedInr", w.valueProcessed, "alerts", engine.book.alerts.size(),
                            "cases", engine.book.cases.size(), "activeCases", active, "blockedDevices", engine.lab.blockedDevices().size()));
                } else if (path.equals("/api/cases")) {
                    List<Object> l = new ArrayList<>();
                    for (FraudCase c : engine.book.cases) l.add(caseJson(c, false));
                    send(x, 200, l);
                } else if (path.matches("/api/cases/[^/]+")) {
                    Optional<FraudCase> c = engine.book.byId(path.substring("/api/cases/".length()));
                    if (c.isPresent()) send(x, 200, caseJson(c.get(), true)); else send(x, 404, Json.obj("error", "case not found"));
                } else if (path.matches("/api/cases/[^/]+/status") && method.equals("POST")) {
                    Optional<FraudCase> c = engine.book.byId(path.split("/")[3]);
                    if (c.isEmpty()) { send(x, 404, Json.obj("error", "case not found")); return; }
                    try { c.get().setStatus(FraudCase.Status.valueOf(q.getOrDefault("value", ""))); send(x, 200, caseJson(c.get(), true)); }
                    catch (IllegalArgumentException e) { send(x, 400, Json.obj("error", "value must be one of " + Arrays.toString(FraudCase.Status.values()))); }
                } else if (path.equals("/api/performance")) send(x, 200, engine.performance());
                else if (path.equals("/api/farms")) send(x, 200, engine.lab.findFarms(threshold));
                else if (path.matches("/api/farms/[^/]+/(hang|release)") && method.equals("POST")) {
                    String[] p = path.split("/");
                    if (p[4].equals("hang")) send(x, 200, Json.obj("device", p[3], "accountsFrozen", engine.lab.hang(p[3])));
                    else { engine.lab.release(p[3]); send(x, 200, Json.obj("device", p[3], "released", true)); }
                } else if (path.equals("/api/gate")) send(x, 200, engine.lab.gate(q.getOrDefault("device", ""), threshold));
                else if (path.equals("/api/serial")) send(x, 200, engine.lab.verifySerial(q.getOrDefault("value", "")));
                else send(x, 404, Json.obj("error", "not found"));
            }
        } catch (Exception e) {
            send(x, 500, Json.obj("error", String.valueOf(e.getMessage())));
        }
    }
}
