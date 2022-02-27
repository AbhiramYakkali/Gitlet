package gitlet;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

import static gitlet.Utils.*;

public class Repository {
    /** The current working directory. */
    public static final File CWD = new File(System.getProperty("user.dir"));
    /** The .gitlet directory. */
    public static final File GITLET_DIR = join(CWD, ".gitlet");
    public static final File STAGING_AREA = join(GITLET_DIR, "staging-area");
    public static final File ADDITIONS = join(STAGING_AREA, "add");
    public static final File REMOVALS = join(STAGING_AREA, "remove");
    public static final File COMMITS = join(GITLET_DIR, "commits");
    public static final File BLOBS = join(GITLET_DIR, "blobs");
    public static final File BLOB_NAMES = join(GITLET_DIR, "blob-names");
    public static final File BRANCHES = join(GITLET_DIR, "branches");
    /** Head pointer points to the current commit that is in the working directory */
    private static String pointer;
    /** Keeps track of the name of the current branch */
    private static String currentBranch;
    /** The format in which dates should be printed when the log() or logGlobal() commands are invoked */
    private static final SimpleDateFormat format = new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy");

    //Initializes the .gitlet directory
    public void initialize() {
        if(GITLET_DIR.exists()) {
            System.out.println("Gitlet version-control system already exists in the current directory.");
            System.exit(0);
        }

        ADDITIONS.mkdirs();
        REMOVALS.mkdir();
        COMMITS.mkdir();
        BLOBS.mkdir();
        BLOB_NAMES.mkdir();
        BRANCHES.mkdir();

        setCurrentBranch("master");
    }

    public void addFile(String file) {
        File path = join(CWD, file);

        if(!path.exists()) {
            System.out.println("File does not exist.");
            System.exit(0);
        }

        String code = getSHACodeOfFile(file);
        if(join(REMOVALS, code).exists()) {
            join(REMOVALS, code).delete();
            System.exit(0);
        }

        List<String> additions = plainFilenamesIn(ADDITIONS);
        if(additions != null) {
            for(String stagedFile : additions) {
                if(getBlobName(stagedFile).equals(file)) {
                    removeBlob(stagedFile);
                }
            }
        }

        List<String> prevBlobs = getCurrentCommit().getBlobs();
        if(prevBlobs != null) {
            for(String blob : prevBlobs) {
                if(blob.equals(code)) {
                    System.exit(0);
                }
            }
        }

        addToStagingArea(file);
    }
    private void addToStagingArea(String file) {
        String code = getSHACodeOfFile(file);
        writeContents(join(ADDITIONS, code), readContents(join(CWD, file)));
        addBlobName(code, file);
    }

    public void removeFile(String file) {
        if(!join(CWD, file).exists()) {
            List<String> blobs = getCurrentCommit().getBlobs();
            for(String code : blobs) {
                if(getBlobName(code).equals(file)) {
                    writeContents(join(REMOVALS, code), file);
                    System.exit(0);
                }
            }
            System.out.println("No reason to remove the file.");
            System.exit(0);
        }

        String code = getSHACodeOfFile(file);
        if(join(ADDITIONS, code).exists()) {
            removeBlob(code);
            join(ADDITIONS, code).delete();
        } else {
            List<String> blobs = getCurrentCommit().getBlobs();
            if(blobs != null && blobs.contains(code)) {
                writeContents(join(REMOVALS, code));
                restrictedDelete(join(CWD, file));
            } else {
                System.out.println("No reason to remove the file.");
                System.exit(0);
            }
        }
    }

    //Commits the current staging area, then clears the staging area and moves master/head pointer
    private void commit(String message, String mergedBranch) {
        Commit prevCommit = getCurrentCommit();
        ArrayList<String> blobs = new ArrayList<>(Objects.requireNonNull(plainFilenamesIn(ADDITIONS)));
        List<String> prevBlobs = prevCommit.getBlobs();
        List<String> fileNames = new ArrayList<>();
        List<String> removals = plainFilenamesIn(REMOVALS);

        for(String code : blobs) {
            fileNames.add(getBlobName(code));
            writeContents(join(BLOBS, code), readContents(join(ADDITIONS, code)));
        }
        if(prevBlobs != null) {
            for(String blob : prevBlobs) {
                if(!fileNames.contains(getBlobName(blob)) && removals != null && !removals.contains(blob)) {
                    blobs.add(blob);
                }
            }
        }

        Commit commit = new Commit(message, getBranchCommit(currentBranch), mergedBranch, blobs);
        commit(commit);
    }
    public void commit(String message) {
        commit(message, null);
    }
    //Commits a specified commit
    public void commit(Commit commit) {
        setPointer(commit.commit());
        editBranch(currentBranch, pointer);
        clearStagingArea();
    }

    public void log() {
        String code = pointer;
        Commit commit;
        while(code != null) {
            commit = logCommit(code);

            code = commit.getParent();
        }
    }
    public void logGlobal() {
        List<String> commits = plainFilenamesIn(COMMITS);
        for(String code : commits) {
            logCommit(code);
        }
    }
    private Commit logCommit(String code) {
        Commit commit = getCommit(code);

        System.out.println("===");
        System.out.println("commit " + code);

        if(commit.getSecondParent() != null) {
            System.out.println("Merge: " + commit.getParent().substring(0, 7) + " " + commit.getSecondParent().substring(0, 7));
        }

        System.out.println("Date: " + format.format(commit.getDate()) + " -0800");
        System.out.println(commit.getMessage());
        System.out.println();

        return commit;
    }

    public void find(String message) {
        List<String> files = plainFilenamesIn(COMMITS);
        boolean found = false;

        if(files != null) {
            for(String code : files) {
                if(getCommit(code).getMessage().equals(message)) {
                    System.out.println(code);
                    if(!found) found = true;
                }
            }
        }

        if(!found) {
            System.out.println("Found no commit with that message");
            System.exit(0);
        }
    }

    public void createBranch(String name) {
        if(branchExists(name)) {
            System.out.println("A branch with that name already exists.");
            System.exit(0);
        } else {
            editBranch(name, pointer);
        }
    }
    public void removeBranch(String name) {
        if(!branchExists(name)) {
            System.out.println("A branch with that name does not exist.");
            System.exit(0);
        } else if(currentBranch.equals(name)) {
            System.out.println("Cannot remove the current branch.");
            System.exit(0);
        } else {
            deleteBranch(name);
        }
    }

    public void printStatus() {
        List<String> items = plainFilenamesIn(BRANCHES);

        System.out.println("=== Branches ===");
        for(String branch : items) {
            if(branch.equals(currentBranch)) System.out.print("*");
            System.out.println(branch);
        }

        items = plainFilenamesIn(ADDITIONS);
        if(items != null) {
            System.out.println("\n=== Staged Files ===");
            for(String code : items) {
                System.out.println(getBlobName(code));
            }
        }

        items = plainFilenamesIn(REMOVALS);
        if(items != null) {
            System.out.println("\n=== Removed Files ===");
            for(String code : items) {
                System.out.println(getBlobName(code));
            }
        }

        System.out.println("\n=== Modifications Not Staged For Commit ===\n\n=== Untracked Files ===");
    }

    public void checkoutFile(String file) {
        checkoutFile(file, getBranchCommit(currentBranch));
    }
    public void checkoutFile(String file, String c) {
        String codeCommit = (c.length() == UID_LENGTH) ? c : getFullCommitCode(c);

        if(!commitExists(codeCommit)) {
            System.out.println("No commit with that id exists.");
            System.exit(0);
        }
        Commit commit = getCommit(codeCommit);

        List<String> codes = commit.getBlobs();
        Iterator<String> i = codes.iterator();
        String code = "h";
        while(i.hasNext() && !file.equals(getBlobName(code))) {
            code = i.next();
        }
        if(!getBlobName(code).equals(file)) {
            System.out.println("File does not exist in that commit.");
            System.exit(0);
        }

        checkoutCode(code);
    }
    public void checkoutCode(String code) {
        List<String> files = plainFilenamesIn(CWD);
        String fileName = getBlobName(code);
        if(files != null && files.contains(fileName)) {
            restrictedDelete(join(CWD, fileName));
        }

        writeContents(join(CWD, fileName), readContents(join(BLOBS, code)));
    }
    public void checkoutBranch(String branch) {
        if(!branchExists(branch)) {
            System.out.println("No such branch exists.");
            System.exit(0);
        }
        if(branch.equals(currentBranch)) {
            System.out.println("No need to checkout the current branch.");
            System.exit(0);
        }

        String branchCode = getBranchCommit(branch);
        checkoutCommit(branchCode);

        setCurrentBranch(branch);
    }
    private void checkoutCommit(String c) {
        String code = (c.length() == UID_LENGTH) ? c : getFullCommitCode(c);

        if(!join(COMMITS, code).exists()) {
            System.out.println("No commit with that id exists.");
            System.exit(0);
        }

        List<String> files = plainFilenamesIn(CWD);
        List<String> trackedFiles = getCurrentCommit().getBlobs();
        List<String> previousFiles = getCommit(c).getBlobs();

        //If any of these lists are null, initialize them as a new list to avoid having to check for null cases
        if(trackedFiles == null) trackedFiles = new ArrayList<>();
        if(previousFiles == null) previousFiles = new ArrayList<>();
        if(files == null) files = new ArrayList<>();

        //Find the names of all files in the commit being checked out
        List<String> previousNames = new ArrayList<>();
        for(String blob : previousFiles) {
            previousNames.add(getBlobName(blob));
        }

        if(untrackedFilesExist(c)) {
            System.out.println("There is an untracked file in the way; delete it, or add and commit it first.");
            System.exit(0);
        }

        //There are no untracked files in the way: continue with checkout
        //Delete all files that are currently being tracked that would be overwritten by checkout
        for(String file : files) {
            String fileCode = getSHACodeOfFile(file);
            if(trackedFiles.contains(fileCode)) join(CWD, file).delete();
        }

        for(int i = 0; i < previousFiles.size(); i++) {
            String fileName = previousNames.get(i);
            writeContents(join(CWD, fileName), readContentsAsString(join(BLOBS, previousFiles.get(i))));
        }

        setPointer(code);
        clearStagingArea();
    }
    public void reset(String code) {
        checkoutCommit(code);
        editBranch(currentBranch, code);
    }

    public void merge(String branch) {
        boolean conflict = false;

        if(branch.equals(currentBranch)) {
            System.out.println("Cannot merge a branch with itself.");
            System.exit(0);
        }
        if(!join(BRANCHES, branch).exists()) {
            System.out.println("A branch with that name does not exist.");
            System.exit(0);
        }
        if(plainFilenamesIn(ADDITIONS).size() > 0 || plainFilenamesIn(REMOVALS).size() > 0) {
            System.out.println("You have uncommitted changes.");
            System.exit(0);
        }

        String mergeCommitCode = getBranchCommit(branch), splitCommitCode = findSplitPoint(mergeCommitCode);

        if(untrackedFilesExist(mergeCommitCode)) {
            System.out.println("There is an untracked file in the way; delete it, or add and commit it first.");
            System.exit(0);
        }
        if(splitCommitCode.equals(mergeCommitCode)) {
            System.out.println("Given branch is an ancestor of the current branch.");
            System.exit(0);
        } else if(splitCommitCode.equals(getCurrentBranchCommit())) {
            System.out.println("Current branch fast-forwarded.");
            checkoutBranch(branch);
            System.exit(0);
        }

        Commit currCommit = getCurrentCommit(), mergeCommit = getCommit(mergeCommitCode), splitPoint = getCommit(splitCommitCode);

        HashMap<String, String> currMap = currCommit.createHashMap(), mergeMap = mergeCommit.createHashMap(),
                splitMap = splitPoint.createHashMap();

        List<String> currNames = currCommit.getFileNames(), mergeNames = mergeCommit.getFileNames(),
                splitNames = splitPoint.getFileNames();

        //Loop through the files that were tracked by the split point
        for(String name : splitNames) {
            String currCode = currMap.get(name), mergeCode = mergeMap.get(name), splitCode = splitMap.get(name);

            if (currCode == null) currCode = "";
            if (mergeCode == null) mergeCode = "";

            if(!currCode.equals(splitCode) && !mergeCode.equals(splitCode) && !currCode.equals(mergeCode)) {
                mergeConflict(currCode, mergeCode, name);
                conflict = true;
                continue;
            }

            if(!mergeCode.equals(splitCode)) {
                if(currCode.equals(splitCode)) {
                    if(mergeCode.equals("")) {
                        writeContents(join(REMOVALS, currCode));
                        restrictedDelete(join(CWD, name));
                    } else {
                        checkoutFile(name, mergeCommitCode);
                        addToStagingArea(name);
                    }
                }
            }

            currNames.remove(name);
            mergeNames.remove(name);
        }
        //Files that are present in the given branch but not at the split point
        for(String name : mergeNames) {
            if(currMap.get(name) == null) {
                checkoutFile(name, mergeCommitCode);
                addToStagingArea(name);
            } else if(!currMap.get(name).equals(mergeMap.get(name))) {
                mergeConflict(currMap.get(name), mergeMap.get(name), name);
                conflict = true;
            }
        }

        commit("Merged " + branch + " into " + currentBranch + ".", mergeCommitCode);
        if(conflict) System.out.println("Encountered a merge conflict.");
    }
    private void mergeConflict(String currFile, String mergeFile, String name) {
        String output = "<<<<<<< HEAD";
        output += (currFile.equals("")) ? "" : readContentsAsString(join(BLOBS, currFile));
        output += "=======";
        output += (mergeFile.equals("")) ? "" : readContentsAsString(join(BLOBS, mergeFile));
        output += ">>>>>>>";

        writeContents(join(CWD, name), output);
        addToStagingArea(name);
    }
    public String findSplitPoint(String mergeCommitCode) {
        Commit temp = getCommit(mergeCommitCode);
        ArrayList<String> commits = new ArrayList<>();
        commits.add(mergeCommitCode);
        while(temp.getParent() != null) {
            commits.add(temp.getParent());
            temp = getCommit(temp.getParent());
        }

        if(commits.contains(getCurrentBranchCommit())) return getCurrentBranchCommit();
        temp = getCommit(getCurrentBranchCommit());
        while(true) {
            if(commits.contains(temp.getParent())) return temp.getParent();
            if(commits.contains(temp.getSecondParent())) return temp.getSecondParent();
            temp = getCommit(temp.getParent());
        }
    }

    //Returns a list containing filenames of all untracked files in the working directory.
    //Returns an empty list if all files are being tracked.
    private List<String> findUntrackedFiles(String commit) {
        List<String> trackedFiles = getCurrentCommit().getBlobs(), previousFiles = getCommit(commit).getBlobs()
                , previousNames = getCommit(commit).getFileNames();

        if(trackedFiles == null) trackedFiles = new ArrayList<>();
        if(previousFiles == null) previousFiles = new ArrayList<>();

        List<String> untrackedFiles = new ArrayList<>(), files = plainFilenamesIn(CWD);
        for (String file : files) {
            String fileCode = getSHACodeOfFile(file);
            if (!trackedFiles.contains(fileCode) && !previousFiles.contains(fileCode)) {
                if (previousNames.contains(file)) {
                    System.out.println("There is an untracked file in the way; delete it, or add and commit it first.");
                    System.exit(0);
                }
            }
        }
        return untrackedFiles;
    }
    private boolean untrackedFilesExist(String commit) {
        return !findUntrackedFiles(commit).isEmpty();
    }

    //Reads the current tracked commit from the directory and returns it
    private Commit getCurrentCommit() {
        return getCommit(pointer);
    }
    //Reads a certain commit from the directory and returns it
    private Commit getCommit(String code) {
        File path = join(COMMITS, code);
        if(path.exists()) {
            return readObject(path, Commit.class);
        } else {
            return null;
        }
    }
    private boolean commitExists(String code) {
        return join(COMMITS, code).exists();
    }

    public static String getBlobName(String code) {
        File file = join(BLOB_NAMES, code);
        if(file.exists()) {
            return readContentsAsString(file);
        } else {
            return null;
        }
    }
    private void removeBlob(String code) {
        join(BLOB_NAMES, code).delete();
    }
    private void addBlobName(String code, String name) {
        writeContents(join(BLOB_NAMES, code), name);
    }

    private String getBranchCommit(String name) {
        return readContentsAsString(join(BRANCHES, name));
    }
    private String getCurrentBranchCommit() {
        return getBranchCommit(currentBranch);
    }
    private void editBranch(String name, String commit) {
        writeContents(join(BRANCHES, name), commit);
    }
    private void deleteBranch(String name) {
        join(BRANCHES, name).delete();
    }
    private boolean branchExists(String name) {
        return join(BRANCHES, name).exists();
    }

    private void setPointer(String commit) {
        pointer = commit;
        writeContents(join(GITLET_DIR, "pointer"), pointer);
    }
    private String getPointer() {
        return readContentsAsString(join(GITLET_DIR, "pointer"));
    }

    private void setCurrentBranch(String branch) {
        currentBranch = branch;
        writeContents(join(GITLET_DIR, "branch-current"), branch);
    }
    private String getCurrentBranch() {
        return (join(GITLET_DIR, "branch-current").exists()) ? readContentsAsString(join(GITLET_DIR, "branch-current"))
                : null;
    }

    //Finds the full commit code from a shortened version
    private String getFullCommitCode(String shortCode) {
        List<String> commits = plainFilenamesIn(COMMITS);
        int length = shortCode.length();

        if(commits != null) {
            for(String commit : commits) {
                if(commit.substring(0, length).equals(shortCode)) {
                    return commit;
                }
            }
        }
        return null;
    }

    private String getSHACodeOfFile(String file) {
        return sha1(readContents(join(CWD, file)), file);
    }

    private void clearStagingArea() {
        clearDirectory(ADDITIONS);
        clearDirectory(REMOVALS);
    }

    public Repository() {
        currentBranch = getCurrentBranch();
        if(currentBranch != null) pointer = getPointer();
    }
}