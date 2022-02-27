package gitlet;

/** Driver class for Gitlet, a subset of the Git version-control system.
 *  @author Abhiram Yakkali
 */

public class Main {
    public static void main(String[] args) {
        if(args.length == 0) {
            System.out.println("Please enter a command.");
            System.exit(0);
        }
        Repository repo = new Repository();
        String firstArg = args[0];
        switch (firstArg) {
            case "init" -> {
                repo.initialize();
                Commit commit = new Commit("initial commit", null, null, null);
                repo.commit(commit);
            }
            case "add" -> {
                checkGitletDirectory();
                if (args.length > 1) {
                    repo.addFile(args[1]);
                } else {
                    incorrectOperands();
                }
            }
            case "commit" -> {
                checkGitletDirectory();
                if (Utils.plainFilenamesIn(Repository.ADDITIONS).size() > 0 ||
                        Utils.plainFilenamesIn(Repository.REMOVALS).size() > 0) {
                    if (args.length > 1) {
                        repo.commit(args[1]);
                    } else {
                        System.out.println("Please enter a commit message.");
                        System.exit(0);
                    }
                } else {
                    //System.out.println(Utils.plainFilenamesIn(Repository.ADDITIONS));
                    System.out.println("No changes added to the commit.");
                    System.exit(0);
                }
            }
            case "rm" -> {
                checkGitletDirectory();
                if (args.length > 1) {
                    repo.removeFile(args[1]);
                } else {
                    incorrectOperands();
                }
            }
            case "log" -> {
                checkGitletDirectory();
                repo.log();
            }
            case "global-log" -> {
                checkGitletDirectory();
                repo.logGlobal();
            }
            case "find" -> {
                checkGitletDirectory();
                if (args.length > 1) {
                    repo.find(args[1]);
                } else {
                    incorrectOperands();
                }
            }
            case "status" -> {
                checkGitletDirectory();
                repo.printStatus();
            }
            case "checkout" -> {
                checkGitletDirectory();
                switch (args.length) {
                    case 2 -> repo.checkoutBranch(args[1]);
                    case 3 -> repo.checkoutFile(args[2]);
                    case 4 -> {
                        if (!args[2].equals("--")) incorrectOperands();
                        repo.checkoutFile(args[3], args[1]);
                    }
                    default -> incorrectOperands();
                }
            }
            case "branch" -> {
                checkGitletDirectory();
                if (args.length > 1) {
                    repo.createBranch(args[1]);
                } else {
                    incorrectOperands();
                }
            }
            case "rm-branch" -> {
                checkGitletDirectory();
                if (args.length > 1) {
                    repo.removeBranch(args[1]);
                } else {
                    incorrectOperands();
                }
            }
            case "reset" -> {
                checkGitletDirectory();
                if (args.length > 1) {
                    repo.reset(args[1]);
                } else {
                    incorrectOperands();
                }
            }
            case "merge" -> {
                checkGitletDirectory();
                if (args.length > 1) {
                    repo.merge(args[1]);
                } else {
                    incorrectOperands();
                }
            }
            default -> {
                System.out.println("No command with that name exists.");
                System.exit(0);
            }
        }
    }

    private static void incorrectOperands() {
        System.out.println("Incorrect operands.");
        System.exit(0);
    }

    private static void checkGitletDirectory() {
        if(!Repository.GITLET_DIR.exists()) {
            System.out.println("Not in an initialized Gitlet directory.");
            System.exit(0);
        }
    }
}