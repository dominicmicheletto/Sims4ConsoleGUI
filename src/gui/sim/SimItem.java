package gui.sim;

import sim.Sim;

/**
 *
 * @author miche
 */
public class SimItem {
    
    private final Sim sim;
    private final String name;
    
    public SimItem(Sim sim) {
        this(sim, sim.getFullname());
    }
    
    public SimItem(Sim sim, String name) {
        this.sim = sim;
        this.name = name;
    }

    public Sim getSim() {
        return this.sim;
    }

    public String getName() {
        return this.name;
    }
    
    
    
    @Override
    public String toString() {
        return this.name;
    }
    
}
