package top.nontage.jniil.test.target;

public class FunctionalTarget {

    private String username;
    private int loginAttempts = 0;
    private boolean isLocked = false;

    public FunctionalTarget(String username) {
        this.username = username;
    }

    public boolean login(String inputPassword) {
        System.out.println("[Target] Attempting login for user: " + username);

        if (isLocked) {
            System.out.println("[Target] Account is already locked.");
            return false;
        }

        int currentAttempt = ++loginAttempts;
        boolean passwordMatches = "secret123".equals(inputPassword);

        if (passwordMatches) {
            this.loginAttempts = 0;
            System.out.println("[Target] Login successful!");
            return true;
        } else {
            System.out.println("[Target] Password mismatch. Attempts: " + currentAttempt);
            if (currentAttempt >= 3) {
                this.isLocked = true;
                System.out.println("[Target] Maximum attempts reached. Account locked.");
            }
            return false;
        }
    }

    public void processTransaction(String amountStr) {
        System.out.println("[Target] Processing transaction amount: " + amountStr);
        try {
            int amount = Integer.parseInt(amountStr);
            if (amount <= 0) {
                throw new IllegalArgumentException("Amount must be positive.");
            }
            System.out.println("[Target] Transaction completed successfully for amount: $" + amount);
        } catch (NumberFormatException e) {
            System.out.println("[Target] Caught exception inside target method.");
        }
    }

    public int getLoginAttempts() {
        return loginAttempts;
    }

    public boolean isLocked() {
        return isLocked;
    }
}