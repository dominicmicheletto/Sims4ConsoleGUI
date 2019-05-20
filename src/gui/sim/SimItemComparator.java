package gui.sim;

import java.text.Collator;
import java.util.Comparator;
import sim.Sim;

/**
 *
 * @author miche
 */
public class SimItemComparator implements Comparator<Sim> {

    @Override
    public int compare(Sim o1, Sim o2) {
        
        if (o1 == null && o2 == null)
            return 0;
        else if (o1 == null || o2 == null)
            return Collator.getInstance().compare((o1 == null ? o2 : o1).getFullname(), "");
        else {
            if (o1.getSpecies().equals(o2.getSpecies()))
                return Collator.getInstance().compare(o1.getFullname(), o2.getFullname());
            else
                return o1.getSpecies().compareTo(o2.getSpecies());
        }
    }
    
}
