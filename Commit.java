package gitlet;

import java.io.Serializable;
import java.util.*;

import static gitlet.Utils.*;

public class Commit implements Serializable {
    /** The message of this gitlet.Commit */
    private final String message;
    /** ID of parent commit */
    private final String parent;
    /** ID of second parent commit(in the case of merge commits) */
    private final String parent2;
    /** The date that this gitlet.Commit was created */
    private final Date date;
    /** List containing ids of blobs tracked by this commit */
    private final ArrayList<String> blobs;

    public Commit(String message, String parent1, String parent2, ArrayList<String> blobs) {
        this.message = message;
        parent = parent1;
        this.parent2 = parent2;
        this.blobs = blobs;
        if(parent == null) {
            date = new Date(0);
        } else {
            date = new Date();
        }
    }

    //Commits this commit: adds this commit to the commits directory
    public String commit() {
        String code = sha1(serialize(this));
        writeObject(join(Repository.COMMITS, code), this);
        return code;
    }


    public List<String> getBlobs() {
        return blobs;
    }
    public String getParent() {
        return parent;
    }
    public String getSecondParent() { return parent2; }
    public Date getDate() {
        return date;
    }
    public String getMessage() {
        return message;
    }

    //Returns a HashMap mapping blob codes to file names and file names to blob codes
    public HashMap<String, String> createHashMap() {
        HashMap<String, String> map = new HashMap<>();
        if(blobs == null) return map;

        for(String blob : blobs) {
            String name = Repository.getBlobName(blob);
            map.put(name, blob);
        }
        return map;
    }

    public List<String> getFileNames() {
        List<String> names = new ArrayList<>();
        if(blobs == null) return names;

        for(String blob : blobs) {
            names.add(Repository.getBlobName(blob));
        }
        return names;
    }
}