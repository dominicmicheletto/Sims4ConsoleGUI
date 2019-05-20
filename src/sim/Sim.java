package sim;

/**
 *
 * @author miche
 */
public class Sim {
    
    public static enum Species {
        HUMAN,
        CAT,
        DOG,
        INVALID
    }
    
    private final String fullname;
    private final long id;
    private final boolean isHuman;
    private final Species species;
    
    public Sim(String inputString) {
        var args = inputString.split("\0");
        
        if (inputString == null)
            throw new NullPointerException();
        System.out.println(inputString);
        
        try {
            this.fullname = args[0];
            this.id = Long.parseLong(args[1]);
            this.isHuman = Boolean.parseBoolean(args[2]);
            this.species = Species.valueOf(args[3]);
        }
        catch (IndexOutOfBoundsException | NumberFormatException ex) {
            throw new IllegalArgumentException(ex);
        }
    }
    
    public Sim(String fullname, long id, boolean isHuman, Species species) {
        this.fullname = fullname;
        this.id = id;
        this.isHuman = isHuman;
        this.species = species;
    }

    public String getFullname() {
        return fullname;
    }

    public long getId() {
        return id;
    }

    public boolean isIsHuman() {
        return isHuman;
    }

    public Species getSpecies() {
        return species;
    }

    @Override
    public String toString() {
        return "Sim{" +
                "fullname=" + fullname +
                ", id=" + id +
                ", isHuman=" + isHuman +
                ", species=" + species + '}';
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 89 * hash + (int) (this.id ^ (this.id >>> 32));
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Sim other = (Sim) obj;
        return this.id == other.id;
    }
    
}
