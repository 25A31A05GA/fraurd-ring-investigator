package com.frauddetect;

/** Convenience bundle: a world, its case book, the detector and the device lab, advanced together. */
public final class Engine {
    public final World world;
    public final CaseBook book;
    public final DetectionEngine detector;
    public final DeviceFarmLab lab;

    public Engine(long seed) {
        this.world = World.standard(seed);
        this.book = new CaseBook(world);
        this.detector = new DetectionEngine(world, book);
        this.lab = new DeviceFarmLab(world);
    }

    /** One simulated second. */
    public synchronized void tick(int eventsPerSecond) {
        world.step(eventsPerSecond, true);
        detector.run();
    }

    public synchronized Performance performance() { return Performance.evaluate(world, book); }
}
